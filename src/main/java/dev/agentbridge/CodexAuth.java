package dev.agentbridge;

import com.google.gson.*;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Codex-managed OAuth over the documented app-server account API. No token handling. */
final class CodexAuth implements RpcProcess.Handler, LoginLauncher.Handle {
    record Instructions(String loginId, URI url, String userCode) {
        // Do not accidentally log the OAuth URL, state, or one-time code.
        @Override public String toString() { return "Codex sign-in instructions"; }
    }
    record Outcome(boolean success, boolean cancelled, String message, AccountAuth.Result account) { }
    private final List<String> command;
    private final Path directory;
    private final Duration requestTimeout, loginTimeout;
    private final CompletableFuture<JsonObject> completion = new CompletableFuture<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean used = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicBoolean cancelSent = new AtomicBoolean();
    private final List<JsonObject> earlyEvents = new ArrayList<>();
    private volatile RpcProcess rpc;
    private volatile String loginId;

    CodexAuth(String executable) {
        this(List.of(AccountAuth.executable(executable), "app-server"), Path.of(System.getProperty("user.home")), Duration.ofSeconds(10), Duration.ofMinutes(15));
    }
    CodexAuth(List<String> command, Path directory, Duration requestTimeout, Duration loginTimeout) {
        this.command = List.copyOf(command); this.directory = directory;
        this.requestTimeout = requestTimeout; this.loginTimeout = loginTimeout;
    }
    void start(boolean deviceCode, Consumer<Instructions> instructions, Consumer<Outcome> completed) {
        if (!used.compareAndSet(false, true)) throw new IllegalStateException("A sign-in attempt can only start once");
        Thread.ofVirtual().name("agent-bridge-codex-login").start(() -> {
            Outcome outcome;
            try {
                connect();
                JsonObject response = call("account/login/start", Json.obj("type", deviceCode ? "chatgptDeviceCode" : "chatgpt"));
                Instructions info = parseInstructions(response, deviceCode);
                synchronized (this) {
                    loginId = info.loginId();
                    for (JsonObject event : earlyEvents) acceptCompletion(event);
                    earlyEvents.clear();
                }
                if (!cancelled.get() && !completion.isDone()) instructions.accept(info);
                JsonObject result = completion.get(loginTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (cancelled.get()) outcome = cancelledOutcome();
                else if (!booleanField(result, "success")) outcome = failure("OpenAI sign-in did not complete. Retry, or choose Device code in Settings.");
                else {
                    AccountAuth.Result account = parseAccount(call("account/read", Json.obj("refreshToken", false)));
                    boolean confirmed = account.status() == AccountAuth.Status.SIGNED_IN && account.label().equals("ChatGPT");
                    outcome = confirmed ? new Outcome(true, false, "Signed in with ChatGPT · Ready to chat", account)
                        : failure("Sign-in finished, but Codex did not confirm a ChatGPT account. Use Check login.");
                }
            } catch (TimeoutException timeout) {
                cancelRemote();
                outcome = failure("OpenAI sign-in timed out. Retry, or choose Device code in Settings.");
            } catch (Exception error) {
                cancelRemote();
                outcome = failure("OpenAI sign-in could not finish. Check the Codex executable/version or try Device code in Settings.");
            } finally {
                finished.set(true); closeProcess();
                synchronized (this) { earlyEvents.clear(); }
            }
            completed.accept(cancelled.get() ? cancelledOutcome() : outcome);
        });
    }
    boolean isActive() { return !cancelled.get() && !finished.get(); }
    @Override public void cancel() {
        if (finished.get() || !cancelled.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("agent-bridge-codex-login-cancel").start(() -> {
            cancelRemote(); closeProcess();
            completion.complete(Json.obj("success", false));
        });
    }
    private void cancelRemote() {
        RpcProcess connection = rpc; String id = loginId;
        if (connection != null && id != null && cancelSent.compareAndSet(false, true)) {
            try { connection.request("account/login/cancel", Json.obj("loginId", id)).get(2, TimeUnit.SECONDS); }
            catch (Exception ignored) { /* Closing only our app-server also tears down its pending flow. */ }
        }
    }
    private void closeProcess() { RpcProcess connection = rpc; if (connection != null) connection.close(); }
    private void connect() throws Exception {
        if (cancelled.get()) throw new CancellationException();
        rpc = new RpcProcess(command, directory, false, this);
        if (cancelled.get()) { closeProcess(); throw new CancellationException(); }
        call("initialize", Json.obj("clientInfo", Json.obj("name", "agent_bridge", "title", "Agent Bridge", "version", "0.8.1")));
        rpc.notify("initialized", new JsonObject());
    }
    private JsonObject call(String method, JsonObject params) throws Exception {
        if (cancelled.get()) throw new CancellationException();
        return rpc.request(method, params).get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }
    AccountAuth.Result read() throws Exception {
        if (!used.compareAndSet(false, true)) throw new IllegalStateException("Account check already started");
        try { connect(); return parseAccount(call("account/read", Json.obj("refreshToken", false))); }
        finally { finished.set(true); closeProcess(); }
    }
    @Override public synchronized void notification(String method, JsonObject params) {
        if (finished.get() || !method.equals("account/login/completed")) return;
        if (loginId == null) {
            if (earlyEvents.size() < 8) earlyEvents.add(params.deepCopy());
            else completion.completeExceptionally(new IllegalStateException("Too many unsolicited login events"));
        } else acceptCompletion(params);
    }
    private void acceptCompletion(JsonObject params) {
        if (loginId.equals(Json.str(params, "loginId"))) {
            if (isBoolean(params.get("success"))) completion.complete(params);
            else completion.completeExceptionally(new IllegalStateException("Invalid login completion"));
        }
    }
    @Override public CompletableFuture<JsonObject> request(String method, JsonObject params) {
        // We do not opt into external-token mode or handle refresh tokens on the client's behalf.
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Unsupported auth client request"));
    }
    @Override public void closed(String reason) {
        completion.completeExceptionally(new IllegalStateException("Codex authentication connection closed"));
    }
    static Instructions parseInstructions(JsonObject result, boolean device) {
        String expected = device ? "chatgptDeviceCode" : "chatgpt";
        if (!expected.equals(Json.str(result, "type"))) throw new IllegalArgumentException("Unexpected login type");
        String id = stringField(result, "loginId");
        if (id.isBlank() || id.length() > 256) throw new IllegalArgumentException("Missing login ID");
        URI url = URI.create(stringField(result, device ? "verificationUrl" : "authUrl"));
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443)
            || !Set.of("auth.openai.com", "chatgpt.com").contains(Objects.toString(url.getHost(), "").toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Unexpected OpenAI login URL");
        String code = device ? stringField(result, "userCode") : "";
        if (device && !code.matches("[A-Za-z0-9 -]{4,32}")) throw new IllegalArgumentException("Invalid device code");
        return new Instructions(id, url, code);
    }
    static AccountAuth.Result parseAccount(JsonObject result) {
        if (!isBoolean(result.get("requiresOpenaiAuth"))) throw new IllegalArgumentException("Invalid account status");
        JsonElement account = result.get("account");
        if (account == null || account.isJsonNull()) {
            if (!booleanField(result, "requiresOpenaiAuth")) return new AccountAuth.Result(AccountAuth.Status.CONFIGURED, "Provider configured", "Codex's configured provider does not require OpenAI authentication. Its credentials were not verified.");
            return new AccountAuth.Result(AccountAuth.Status.SIGNED_OUT, "Not signed in", "Click Sign in to connect your ChatGPT account.");
        }
        String type = stringField(account.getAsJsonObject(), "type");
        return switch (type) {
            case "chatgpt" -> new AccountAuth.Result(AccountAuth.Status.SIGNED_IN, "ChatGPT", "Codex reports a saved ChatGPT login and manages token refresh. Account access and remaining quota are checked on use.");
            case "apiKey" -> new AccountAuth.Result(AccountAuth.Status.SIGNED_IN, "API key", "Codex is configured with an API key. API billing applies separately from ChatGPT subscription usage.");
            case "amazonBedrock" -> new AccountAuth.Result(AccountAuth.Status.CONFIGURED, "Bedrock configured", "Codex is configured for Amazon Bedrock. This check does not validate AWS credentials.");
            default -> new AccountAuth.Result(AccountAuth.Status.UNKNOWN, "Not verified", "Codex returned an unfamiliar account type. Check its provider configuration.");
        };
    }
    private static boolean isBoolean(JsonElement value) { return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(); }
    private static boolean booleanField(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (!isBoolean(value)) throw new IllegalArgumentException("Invalid boolean");
        return value.getAsBoolean();
    }
    private static String stringField(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid string");
        return value.getAsString();
    }
    private static Outcome failure(String message) { return new Outcome(false, false, message, null); }
    private static Outcome cancelledOutcome() { return new Outcome(false, true, "Sign-in cancelled · Saved credentials were not removed; Check login to see current status", null); }
}
