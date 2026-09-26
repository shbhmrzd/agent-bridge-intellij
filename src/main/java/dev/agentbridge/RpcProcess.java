package dev.agentbridge;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Dedicated readers keep a pending approval from blocking protocol traffic. No shell evaluation. */
final class RpcProcess implements AutoCloseable {
    interface Handler {
        void notification(String method, JsonObject params);
        CompletableFuture<JsonObject> request(String method, JsonObject params);
        void closed(String reason);
    }
    private final Process process;
    private final BufferedWriter writer;
    private final boolean acp;
    private final Handler handler;
    private final AtomicLong nextId = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final StringBuilder stderr = new StringBuilder();

    RpcProcess(List<String> command, Path cwd, boolean acp, Handler handler) throws IOException {
        this.acp = acp;
        this.handler = handler;
        ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile());
        process = builder.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        Thread.ofPlatform().daemon().name("agent-bridge-stderr").start(() -> {
            try (Reader reader = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
                char[] b = new char[1024]; int n;
                while ((n = reader.read(b)) != -1) synchronized (stderr) {
                    stderr.append(b, 0, n);
                    if (stderr.length() > 8192) stderr.delete(0, stderr.length() - 8192);
                }
            } catch (IOException ignored) { }
        });
        Thread.ofPlatform().daemon().name("agent-bridge-stdout").start(this::read);
    }
    CompletableFuture<JsonObject> request(String method, JsonObject params) {
        String id = Long.toString(nextId.incrementAndGet());
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(id, future);
        try { send(Json.obj("id", id, "method", method, "params", params)); }
        catch (IOException e) { pending.remove(id); future.completeExceptionally(e); }
        future.whenComplete((v, e) -> pending.remove(id));
        return future;
    }
    void notify(String method, JsonObject params) throws IOException {
        send(Json.obj("method", method, "params", params));
    }
    private synchronized void send(JsonObject message) throws IOException {
        if (closed.get()) throw new IOException("Agent connection is closed");
        if (acp) message.addProperty("jsonrpc", "2.0");
        writer.write(message.toString()); writer.newLine(); writer.flush();
    }
    private void read() {
        try (Reader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            readFrames(reader, line -> dispatch(JsonParser.parseString(line).getAsJsonObject()));
            if (!closed.get()) fail("Agent exited. Check CLI login and version in the terminal.");
        } catch (Exception e) { if (!closed.get()) fail("Agent connection failed: " + e.getMessage()); }
    }
    static void readFrames(Reader reader, Consumer<String> consumer) throws IOException {
        StringBuilder frame = new StringBuilder(); char[] buf = new char[8192]; int count;
        while ((count = reader.read(buf)) != -1) {
            for (int i = 0; i < count; i++) {
                if (buf[i] == '\n') {
                    if (!frame.toString().isBlank()) consumer.accept(frame.toString());
                    frame.setLength(0);
                } else {
                    if (frame.length() >= 4 * 1024 * 1024) throw new IOException("Protocol frame exceeds 4 MiB");
                    frame.append(buf[i]);
                }
            }
        }
        if (!frame.toString().isBlank()) throw new EOFException("Truncated protocol frame");
    }
    private void dispatch(JsonObject message) {
        if (message.has("method")) {
            String method = Json.str(message, "method");
            JsonObject params = Json.child(message, "params");
            if (!message.has("id")) { handler.notification(method, params); return; }
            JsonElement id = message.get("id");
            handler.request(method, params).whenComplete((result, error) -> {
                JsonObject reply = Json.obj("id", id);
                if (error != null) reply.add("error", Json.obj("code", -32601, "message", "Unsupported or cancelled client request"));
                else reply.add("result", result);
                try { send(reply); } catch (IOException ignored) { }
            });
        } else if (message.has("id")) {
            CompletableFuture<JsonObject> future = pending.remove(message.get("id").getAsString());
            if (future == null) return;
            if (message.has("error")) future.completeExceptionally(new IOException(Json.str(Json.child(message, "error"), "message")));
            else future.complete(Json.child(message, "result"));
        }
    }
    private void fail(String reason) { close(); handler.closed(reason); }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        pending.values().forEach(f -> f.completeExceptionally(new IOException("Agent disconnected")));
        pending.clear();
        // Kill descendants we own as well as the parent. Never target other existing CLI sessions.
        List<ProcessHandle> children = descendants(process);
        children.forEach(ProcessHandle::destroy);
        process.destroy();
        Thread.ofPlatform().daemon().name("agent-bridge-cleanup").start(() -> {
            try { if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        });
    }
    static List<ProcessHandle> descendants(Process process) {
        // Some macOS managed environments prohibit process enumeration. Still stop the owned parent.
        try { return process.descendants().toList(); }
        catch (RuntimeException restricted) { return List.of(); }
    }
}
