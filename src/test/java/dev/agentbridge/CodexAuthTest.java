package dev.agentbridge;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class CodexAuthTest {
    private static int passed;
    private static Path temp;
    private static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); passed++; System.out.println("PASS " + name); }
    private record Attempt(CodexAuth auth, CompletableFuture<CodexAuth.Instructions> instructions, CompletableFuture<CodexAuth.Outcome> done, Path trace) { }
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("fixture")) { fixture(args[1], Path.of(args[2])); return; }
        if (args.length > 0 && args[0].equals("live-read")) {
            var status = new CodexAuth(args[1]).read();
            check(status.status() == AccountAuth.Status.SIGNED_IN, "installed Codex account/read recognizes saved authentication (no account details printed)");
            return;
        }
        temp = Files.createTempDirectory("agent-bridge-auth-test-");
        try {
            var browser = attempt("browser", false, 3000);
            check(browser.instructions().get(3, TimeUnit.SECONDS).url().getHost().equals("auth.openai.com"), "browser URL comes from Codex-managed login");
            check(browser.done().get(5, TimeUnit.SECONDS).success(), "matching completion plus account/read confirms ChatGPT login");
            check(Files.readString(browser.trace()).equals("initialize\ninitialized\naccount/login/start\naccount/read\n"), "login uses only stable account API and ordered handshake, without model calls");
            var device = attempt("device", true, 3000);
            check(device.instructions().get(3, TimeUnit.SECONDS).userCode().equals("ABCD-1234"), "device flow exposes one-time instructions without external tokens");
            check(device.done().get(5, TimeUnit.SECONDS).success(), "device-code completion verified against account status");
            var early = attempt("early", false, 3000);
            check(early.done().get(5, TimeUnit.SECONDS).success(), "completion arriving before start response is correlated correctly");
            check(!early.instructions().isDone(), "already-completed login does not launch a redundant browser");
            var cancelled = attempt("pending", false, 3000);
            cancelled.instructions().get(3, TimeUnit.SECONDS); cancelled.auth().cancel();
            check(cancelled.done().get(5, TimeUnit.SECONDS).cancelled(), "user cancellation returns cancelled rather than signed in");
            check(Files.readString(cancelled.trace()).contains("account/login/cancel\n") && !Files.readString(cancelled.trace()).contains("account/logout"), "cancel sends login ID to official endpoint without logging out");
            var before = create("pending", 3000); before.auth().cancel();
            before.auth().start(false, before.instructions()::complete, before.done()::complete);
            check(before.done().get(5, TimeUnit.SECONDS).cancelled(), "cancellation before process startup settles the attempt");
            var timeout = attempt("wrong-id", false, 200);
            var timed = timeout.done().get(5, TimeUnit.SECONDS);
            check(!timed.success() && timed.message().contains("timed out"), "unrelated completion cannot authenticate the pending login");
            check(Files.readString(timeout.trace()).contains("account/login/cancel"), "timeout cancels the pending login before cleanup");
            check(!attempt("failed", false, 3000).done().get(5, TimeUnit.SECONDS).message().contains("private-error"), "server login errors are not leaked into UI output");
            check(!attempt("disconnect", false, 3000).done().get(5, TimeUnit.SECONDS).success(), "app-server disconnect settles login as failure");
            check(!attempt("invalid-success", false, 3000).done().get(5, TimeUnit.SECONDS).success(), "string success flag cannot mark an account authenticated");
            check(!attempt("wrong-account", false, 3000).done().get(5, TimeUnit.SECONDS).success(), "success event requires matching ChatGPT account state");
            var readTrace = temp.resolve("read.trace");
            var read = client("browser", readTrace, 3000).read();
            check(read.label().equals("ChatGPT") && !read.toString().contains("private@example"), "account metadata is reduced to safe status labels");
            check(Files.readString(readTrace).equals("initialize\ninitialized\naccount/read\n"), "Check login is read-only and never starts OAuth or a model request");
            var configured = CodexAuth.parseAccount(Json.obj("account", null, "requiresOpenaiAuth", false));
            check(configured.status() == AccountAuth.Status.CONFIGURED, "non-OpenAI provider configuration is not claimed to be authenticated");
            check(CodexAuth.parseAccount(Json.obj("account", null, "requiresOpenaiAuth", true)).status() == AccountAuth.Status.SIGNED_OUT, "missing required OpenAI account reported as signed out");
            check(CodexAuth.parseAccount(Json.obj("account", Json.obj("type", "apiKey"), "requiresOpenaiAuth", true)).label().equals("API key"), "API-key configuration distinguished from ChatGPT subscription login");
            for (String url : List.of("http://auth.openai.com/login", "https://auth.openai.com.attacker.test/login", "https://user@auth.openai.com/login", "file:///tmp/login", "https://auth.openai.com:444/login")) {
                try { CodexAuth.parseInstructions(Json.obj("type", "chatgpt", "loginId", "id", "authUrl", url), false); throw new AssertionError("unsafe URL"); }
                catch (IllegalArgumentException expected) { }
            }
            check(true, "browser navigation rejects non-HTTPS, lookalike hosts, userinfo and unexpected ports");
            var safe = CodexAuth.parseInstructions(Json.obj("type", "chatgpt", "loginId", "id", "authUrl", "https://chatgpt.com/login?state=private-state"), false);
            check(!safe.toString().contains("private-state"), "OAuth URL cannot leak through instruction toString");
            System.out.println("Passed " + passed + " Codex account protocol checks (fake server; no real login or model request).");
        } finally {
            try (var paths = Files.walk(temp)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    private static Attempt create(String scenario, long timeout) throws Exception {
        Path trace = Files.createTempFile(temp, scenario, ".trace");
        return new Attempt(client(scenario, trace, timeout), new CompletableFuture<>(), new CompletableFuture<>(), trace);
    }
    private static Attempt attempt(String scenario, boolean device, long timeout) throws Exception {
        Attempt a = create(scenario, timeout); a.auth().start(device, a.instructions()::complete, a.done()::complete); return a;
    }
    private static CodexAuth client(String scenario, Path trace, long timeout) {
        return new CodexAuth(List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp", System.getProperty("java.class.path"), CodexAuthTest.class.getName(), "fixture", scenario, trace.toString()), temp, Duration.ofSeconds(2), Duration.ofMillis(timeout));
    }
    private static void emit(JsonObject object) { System.out.println(object); System.out.flush(); }
    private static void event(String id, Object success) { emit(Json.obj("method", "account/login/completed", "params", Json.obj("loginId", id, "success", success, "error", "private-error"))); }
    private static void fixture(String scenario, Path trace) throws Exception {
        boolean initialized = false;
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        for (String line; (line = reader.readLine()) != null;) {
            JsonObject request = JsonParser.parseString(line).getAsJsonObject();
            String method = Json.str(request, "method");
            Files.writeString(trace, method + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            JsonObject params = Json.child(request, "params"); JsonElement id = request.get("id");
            switch (method) {
                case "initialize" -> {
                    if (request.toString().contains("experimentalApi") || !Json.str(Json.child(params, "clientInfo"), "name").equals("agent_bridge")) throw new AssertionError();
                    emit(Json.obj("id", id, "result", Json.obj("userAgent", "fixture")));
                }
                case "initialized" -> initialized = true;
                case "account/read" -> {
                    if (!initialized || params.get("refreshToken").getAsBoolean()) throw new AssertionError();
                    emit(Json.obj("id", id, "result", Json.obj("requiresOpenaiAuth", true, "account", Json.obj("type", scenario.equals("wrong-account") ? "apiKey" : "chatgpt", "email", "private@example.test", "planType", "pro"))));
                }
                case "account/login/start" -> {
                    if (!initialized) throw new AssertionError();
                    boolean device = scenario.equals("device");
                    if (!Json.str(params, "type").equals(device ? "chatgptDeviceCode" : "chatgpt")) throw new AssertionError();
                    if (scenario.equals("early")) event("our-login", true);
                    emit(Json.obj("id", id, "result", device
                        ? Json.obj("type", "chatgptDeviceCode", "loginId", "our-login", "verificationUrl", "https://auth.openai.com/codex/device", "userCode", "ABCD-1234")
                        : Json.obj("type", "chatgpt", "loginId", "our-login", "authUrl", "https://auth.openai.com/oauth/authorize?state=fixture")));
                    if (scenario.equals("early") || scenario.equals("pending")) continue;
                    Thread.sleep(200);
                    if (scenario.equals("disconnect")) return;
                    if (scenario.equals("wrong-id")) { event("other-login", true); continue; }
                    event("other-login", false);
                    event("our-login", scenario.equals("invalid-success") ? "true" : !scenario.equals("failed"));
                }
                case "account/login/cancel" -> {
                    if (!Json.str(params, "loginId").equals("our-login")) throw new AssertionError();
                    emit(Json.obj("id", id, "result", Json.obj("status", "canceled"))); event("our-login", false);
                }
                default -> throw new AssertionError("Unexpected method: " + method);
            }
        }
    }
}
