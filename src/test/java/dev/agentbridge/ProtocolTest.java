package dev.agentbridge;

import com.google.gson.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public final class ProtocolTest {
    private static int passed;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++; System.out.println("PASS " + name);
    }
    private static boolean fails(CompletableFuture<?> future) throws Exception {
        try { future.get(5, TimeUnit.SECONDS); return false; }
        catch (ExecutionException e) { return true; }
    }
    public static void main(String[] args) throws Exception {
        check(EditGuard.sameBuffer(12, 12, "a", "a", "text", "text"), "unchanged editor buffer accepted");
        check(!EditGuard.sameBuffer(12, 13, "a", "a", "text", "text"), "edit then undo invalidates stale proposal conservatively");
        check(!EditGuard.sameBuffer(12, 12, "a", "b", "text", "text"), "renamed file invalidates proposal");
        check(!EditGuard.sameBuffer(12, 12, "a", "a", "text", "changed"), "changed contents rejected even if stamp matches");
        List<String> frames = new ArrayList<>();
        RpcProcess.readFrames(new StringReader("{\"x\":1}\n\n{\"x\":2}\r\n"), frames::add);
        check(frames.size() == 2, "multiple frames and blank lines");
        try { RpcProcess.readFrames(new StringReader("{\"x\":"), frames::add); throw new AssertionError(); }
        catch (EOFException expected) { check(true, "truncated frame is rejected"); }
        try { RpcProcess.readFrames(new StringReader("x".repeat(4 * 1024 * 1024 + 1)), frames::add); throw new AssertionError(); }
        catch (IOException expected) { check(true, "oversized frame is bounded"); }
        RpcProcess.Handler handler = new RpcProcess.Handler() {
            public void notification(String m, JsonObject p) { }
            public CompletableFuture<JsonObject> request(String m, JsonObject p) {
                return CompletableFuture.completedFuture(Json.obj("outcome", Json.obj("outcome", "cancelled")));
            }
            public void closed(String reason) { }
        };
        List<String> command = List.of("/usr/bin/python3", args[0]);
        try (RpcProcess rpc = new RpcProcess(command, Path.of("."), true, handler)) {
            JsonObject echo = rpc.request("echo", Json.obj("text", "hello 🌍\nquoted \"text\"" )).get(5, TimeUnit.SECONDS);
            check(Json.str(echo, "text").equals("hello 🌍\nquoted \"text\""), "fragmented Unicode frame and JSON escaping");
            var first = rpc.request("hold", new JsonObject());
            var second = rpc.request("release", new JsonObject());
            check(Json.str(second.get(5, TimeUnit.SECONDS), "order").equals("2") && Json.str(first.get(5, TimeUnit.SECONDS), "order").equals("1"), "out-of-order replies correlate by ID");
            var permission = rpc.request("permission", new JsonObject()).get(5, TimeUnit.SECONDS);
            check(Json.str(Json.child(permission, "outcome"), "outcome").equals("cancelled"), "bidirectional permission response");
            check(fails(rpc.request("fail", new JsonObject())), "RPC error propagated");
            var pending = rpc.request("hold", new JsonObject()); rpc.close();
            check(fails(pending), "close settles pending requests");
            check(fails(rpc.request("echo", new JsonObject())), "request after close fails immediately");
        }
        try (RpcProcess rpc = new RpcProcess(command, Path.of("."), false, handler)) {
            check(fails(rpc.request("exit", new JsonObject())), "process death settles pending requests");
        }
        AgentSession.Listener listener = new AgentSession.Listener() {
            public void text(String t) { }
            public void status(String t) { }
            public CompletableFuture<String> permission(String title, String detail, List<AgentSession.Choice> choices) {
                check(choices.stream().noneMatch(c -> c.id().equals("always")), "durable grants excluded");
                return CompletableFuture.completedFuture("once");
            }
        };
        try (AgentSession session = new AgentSession(AgentSession.Provider.Copilot, "unused", Path.of("."), "", true, listener)) {
            JsonObject permission = session.request("session/request_permission", Json.obj("options", Json.arr(
                Json.obj("optionId", "once", "name", "Allow once", "kind", "allow_once"),
                Json.obj("optionId", "always", "name", "Allow always", "kind", "allow_always")))).get();
            check(Json.str(Json.child(permission, "outcome"), "optionId").equals("once"), "ACP option IDs preserved");
            check(fails(session.request("fs/write_text_file", new JsonObject())), "unsupported capabilities fail closed");
        }
        try (AgentSession session = new AgentSession(AgentSession.Provider.Codex, "unused", Path.of("."), "", false, listener)) {
            check(Json.str(session.request("item/fileChange/requestApproval", new JsonObject()).get(), "decision").equals("decline"), "actions-off denies Codex writes");
            check(Json.str(Json.child(session.request("session/request_permission", new JsonObject()).get(), "outcome"), "outcome").equals("cancelled"), "actions-off cancels ACP permissions");
        }
        for (var provider : AgentSession.Provider.values()) {
            StringBuilder text = new StringBuilder();
            AgentSession.Listener stream = new AgentSession.Listener() {
                public void text(String t) { text.append(t); }
                public void status(String t) { }
                public CompletableFuture<String> permission(String title, String detail, List<AgentSession.Choice> choices) {
                    return CompletableFuture.completedFuture(null);
                }
            };
            try (AgentSession session = new AgentSession(provider, Path.of(args[0]).toAbsolutePath().toString(), Path.of("."), "", false, stream)) {
                session.prompt("hello");
                session.prompt("follow-up");
                check(text.toString().equals((provider + " fixture reply").repeat(2)), provider + " initialization, streaming, completion and follow-up");
            }
            try (AgentSession session = new AgentSession(provider, Path.of(args[0]).toAbsolutePath().toString(), Path.of("."), "fixture-model", false, stream)) {
                session.prompt("Use selected model");
                check(text.toString().equals((provider + " fixture reply").repeat(3)), provider + " selected model reaches the native CLI/protocol");
            }
            var history = new ConversationHistory();
            history.completed("Claude", "sonnet", "Earlier question", "Earlier answer 🌍");
            var packet = new ContextPacket(List.of(new ContextPacket.File("Queue.java", "fresh unsaved buffer")), "Queue.java", "");
            try (AgentSession session = new AgentSession(provider, Path.of(args[0]).toAbsolutePath().toString(), Path.of("."), "fixture-model", false, stream)) {
                session.prompt(packet.prompt("fixture-handoff-check", false, history.forSession(true)));
                check(text.toString().equals((provider + " fixture reply").repeat(4)), provider + " new session receives structured history and fresh buffers");
            }
        }
        System.out.println("Passed " + passed + " protocol checks (no model requests).");
    }
}
