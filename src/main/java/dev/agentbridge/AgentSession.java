package dev.agentbridge;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One provider/project session; no credentials are read or copied by the plugin. */
final class AgentSession implements AutoCloseable, RpcProcess.Handler {
    enum Provider { Codex, Claude, Copilot }
    record Choice(String id, String label) { }
    interface Listener {
        void text(String text);
        void status(String text);
        CompletableFuture<String> permission(String title, String detail, List<Choice> choices);
    }
    private final Provider provider;
    private final Listener listener;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile RpcProcess rpc;
    private volatile Process claude;
    private volatile String sessionId = "";
    private volatile String turnId = "";
    private volatile CompletableFuture<Void> turnDone;
    private final String executable;
    private final Path cwd;
    private final String model;
    private final boolean allowEdits;

    AgentSession(Provider provider, String executable, Path cwd, String model, boolean allowEdits, Listener listener) {
        this.provider = provider; this.executable = executable; this.cwd = cwd; this.model = model;
        this.allowEdits = allowEdits; this.listener = listener;
    }
    void prompt(String text) throws Exception {
        checkOpen();
        if (provider == Provider.Claude) { claudePrompt(text); return; }
        if (rpc == null) initialize();
        checkOpen();
        if (provider == Provider.Codex) {
            turnDone = new CompletableFuture<>();
            JsonObject result = call("turn/start", Json.obj("threadId", sessionId,
                "input", Json.arr(Json.obj("type", "text", "text", text))));
            turnId = Json.str(Json.child(result, "turn"), "id");
            turnDone.get(30, TimeUnit.MINUTES);
        } else {
            rpc.request("session/prompt", Json.obj("sessionId", sessionId,
                "prompt", Json.arr(Json.obj("type", "text", "text", text)))).get(30, TimeUnit.MINUTES);
        }
    }
    private void initialize() throws Exception {
        listener.status("Connecting to " + provider + "…");
        List<String> command = provider == Provider.Codex ? List.of(executable, "app-server") : copilotCommand(executable, model);
        RpcProcess started = new RpcProcess(command, cwd, provider == Provider.Copilot, this);
        rpc = started;
        if (closed.get()) { started.close(); checkOpen(); }
        if (provider == Provider.Codex) {
            call("initialize", Json.obj("clientInfo", Json.obj("name", "agent_bridge", "version", "0.9.0")));
            rpc.notify("initialized", new JsonObject());
            JsonObject params = Json.obj("cwd", cwd.toString(), "approvalPolicy", "untrusted",
                "sandbox", allowEdits ? "workspace-write" : "read-only");
            if (!model.isBlank()) params.addProperty("model", model);
            sessionId = Json.str(Json.child(call("thread/start", params), "thread"), "id");
        } else {
            JsonObject init = call("initialize", Json.obj("protocolVersion", 1,
                "clientCapabilities", new JsonObject(), "clientInfo", Json.obj("name", "agent-bridge", "version", "0.9.0")));
            if (!"1".equals(Json.str(init, "protocolVersion"))) throw new IOException("Unsupported ACP protocol version");
            JsonObject result = call("session/new", Json.obj("cwd", cwd.toString(), "mcpServers", new JsonArray()));
            sessionId = Json.str(result, "sessionId");
            // Only use the plan mode if the runtime actually advertises it.
            if (!allowEdits) {
                JsonObject modes = Json.child(result, "modes");
                String plan = "";
                if (modes.has("availableModes")) for (JsonElement m : modes.getAsJsonArray("availableModes")) {
                    if (Json.str(m.getAsJsonObject(), "id").equals("plan")) plan = "plan";
                }
                if (plan.isEmpty()) throw new IOException("Copilot did not advertise plan mode. Update the CLI, or explicitly enable agent actions.");
                call("session/set_mode", Json.obj("sessionId", sessionId, "modeId", plan));
            }
        }
        if (sessionId.isBlank()) throw new IOException("Agent returned no session ID");
        listener.status("Working…");
    }
    static List<String> copilotCommand(String executable, String model) {
        List<String> command = new ArrayList<>(List.of(executable, "--acp", "--stdio"));
        if (!model.isBlank()) command.addAll(List.of("--model", ModelCatalog.validate(model)));
        return List.copyOf(command);
    }
    private JsonObject call(String method, JsonObject params) throws Exception {
        checkOpen(); return rpc.request(method, params).get(60, TimeUnit.SECONDS);
    }
    @Override public void notification(String method, JsonObject p) {
        if (closed.get()) return;
        switch (method) {
            case "item/agentMessage/delta" -> listener.text(Json.str(p, "delta"));
            case "item/started" -> listener.status("Working: " + Json.str(Json.child(p, "item"), "type"));
            case "turn/completed" -> {
                CompletableFuture<Void> done = turnDone;
                JsonObject turn = Json.child(p, "turn");
                if (done != null) {
                    if ("failed".equals(Json.str(turn, "status")))
                        done.completeExceptionally(new IOException(Json.str(Json.child(turn, "error"), "message")));
                    else done.complete(null);
                }
            }
            case "error" -> listener.status("Agent reported: " + Json.str(Json.child(p, "error"), "message"));
            case "session/update" -> {
                JsonObject update = Json.child(p, "update");
                String type = Json.str(update, "sessionUpdate");
                if (type.equals("agent_message_chunk")) listener.text(Json.str(Json.child(update, "content"), "text"));
                else if (type.equals("tool_call") || type.equals("tool_call_update"))
                    listener.status("Tool: " + Json.str(update, "title") + " " + Json.str(update, "status"));
            }
        }
    }
    @Override public CompletableFuture<JsonObject> request(String method, JsonObject p) {
        if (closed.get()) return CompletableFuture.failedFuture(new IOException("Cancelled"));
        if (method.equals("session/request_permission")) {
            if (!allowEdits) return CompletableFuture.completedFuture(Json.obj("outcome", Json.obj("outcome", "cancelled")));
            List<Choice> choices = new ArrayList<>();
            if (p.has("options")) for (JsonElement e : p.getAsJsonArray("options")) {
                JsonObject o = e.getAsJsonObject(); String kind = Json.str(o, "kind");
                // Do not offer durable/broad grants in the first release.
                if (kind.equals("allow_once") || kind.equals("reject_once"))
                    choices.add(new Choice(Json.str(o, "optionId"), Json.str(o, "name")));
            }
            return listener.permission("Copilot requests permission", Json.GSON.toJson(Json.child(p, "toolCall")), choices)
                .thenApply(id -> id == null || closed.get() ? Json.obj("outcome", Json.obj("outcome", "cancelled"))
                    : Json.obj("outcome", Json.obj("outcome", "selected", "optionId", id)));
        }
        if (method.equals("item/commandExecution/requestApproval") || method.equals("item/fileChange/requestApproval")) {
            if (!allowEdits) return CompletableFuture.completedFuture(Json.obj("decision", "decline"));
            return listener.permission("Codex requests permission", p.toString(),
                List.of(new Choice("accept", "Allow once"), new Choice("decline", "Deny")))
                .thenApply(id -> Json.obj("decision", id == null || closed.get() ? "cancel" : id));
        }
        return CompletableFuture.failedFuture(new IOException("Unsupported client operation: " + method));
    }
    @Override public void closed(String reason) {
        CompletableFuture<Void> done = turnDone;
        if (done != null) done.completeExceptionally(new IOException(reason));
        if (!closed.get()) listener.status(reason);
    }
    private void claudePrompt(String text) throws Exception {
        listener.status("Claude is working…");
        List<String> args = new ArrayList<>(List.of(executable, "-p", "--output-format", "stream-json", "--verbose",
            "--include-partial-messages", "--permission-mode", "plan", "--tools", "Read,Glob,Grep",
            "--strict-mcp-config", "--mcp-config", "{\"mcpServers\":{}}", "--setting-sources", "user"));
        if (!sessionId.isEmpty()) args.addAll(List.of("--resume", sessionId));
        if (!model.isBlank()) args.addAll(List.of("--model", model));
        Process started = new ProcessBuilder(args).directory(cwd.toFile()).start();
        claude = started;
        if (closed.get()) { destroyClaude(); checkOpen(); }
        Thread.ofPlatform().daemon().name("agent-bridge-claude-stderr").start(() -> {
            try (InputStream s = started.getErrorStream()) { s.transferTo(OutputStream.nullOutputStream()); }
            catch (IOException ignored) { }
        });
        try (Writer w = new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8)) { w.write(text); }
        AtomicBoolean gotResult = new AtomicBoolean();
        AtomicBoolean gotDelta = new AtomicBoolean();
        try (Reader r = new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8)) {
            RpcProcess.readFrames(r, line -> {
                JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                if (o.has("session_id")) sessionId = Json.str(o, "session_id");
                switch (Json.str(o, "type")) {
                    case "stream_event" -> {
                        JsonObject delta = Json.child(Json.child(o, "event"), "delta");
                        if ("text_delta".equals(Json.str(delta, "type"))) { gotDelta.set(true); listener.text(Json.str(delta, "text")); }
                    }
                    case "result" -> {
                        gotResult.set(true);
                        if (o.has("is_error") && o.get("is_error").getAsBoolean()) throw new IllegalStateException("Claude failed: " + o);
                        if (!gotDelta.get()) listener.text(Json.str(o, "result"));
                    }
                }
            });
        }
        if (started.waitFor() != 0 || !gotResult.get()) throw new IOException("Claude did not complete. Run claude in the terminal to check login/access.");
        claude = null;
    }
    private void checkOpen() throws IOException { if (closed.get()) throw new IOException("Stopped"); }
    private void destroyClaude() {
        Process p = claude;
        if (p != null) { RpcProcess.descendants(p).forEach(ProcessHandle::destroyForcibly); p.destroyForcibly(); }
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (rpc != null) rpc.close();
        destroyClaude();
        if (turnDone != null) turnDone.completeExceptionally(new IOException("Stopped"));
    }
}
