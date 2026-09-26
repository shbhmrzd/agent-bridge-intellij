package dev.agentbridge;

import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public final class AuthTest {
    private static int passed;
    static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); passed++; System.out.println("PASS " + name); }
    static AccountAuth.Result claude(int exit, String json) { return AccountAuth.interpret(AgentSession.Provider.Claude, new AccountAuth.Output(exit, json)); }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            switch (args[0]) {
                case "hang" -> Thread.sleep(30000);
                case "large" -> System.out.print("x".repeat(50000));
                case "stderr" -> { System.err.print("Not logged in"); System.exit(1); }
                case "argv" -> System.out.print(args[1]);
                case "live-status" -> {
                    // Explicit opt-in, local login metadata only, no inference or login mutation.
                    String executable = args.length > 1 ? args[1] : "claude";
                    check(AccountAuth.check(AgentSession.Provider.Claude, executable).status() == AccountAuth.Status.SIGNED_IN, "installed Claude CLI login recognized (no credential data printed)");
                }
                default -> throw new IllegalArgumentException();
            }
            return;
        }
        check(claude(0, "{\"loggedIn\":true,\"email\":\"private@example.test\",\"token\":\"secret\"}").status() == AccountAuth.Status.SIGNED_IN, "Claude JSON login status recognized without exposing account fields");
        check(!claude(0, "{\"loggedIn\":true,\"token\":\"secret\"}").toString().contains("secret"), "auth UI result contains no raw credential output");
        check(claude(1, "{\"loggedIn\":false}").status() == AccountAuth.Status.SIGNED_OUT, "signed-out CLI with nonzero exit reported");
        check(claude(1, "{\"loggedIn\":true}").status() == AccountAuth.Status.UNKNOWN, "failed command never claims successful login");
        check(claude(0, "{\"loggedIn\":\"true\"}").status() == AccountAuth.Status.UNKNOWN, "malformed login boolean not trusted");
        check(claude(0, "unexpected output").status() == AccountAuth.Status.UNKNOWN, "unrecognized output fails without displaying raw contents");
        check(AccountAuth.loginCommand(AgentSession.Provider.Claude, "claude", true).equals(List.of("claude", "auth", "login", "--claudeai", "--sso")), "Claude organization login uses the official SSO flag");
        check(AccountAuth.check(AgentSession.Provider.Copilot, "/does/not/exist").status() == AccountAuth.Status.UNSUPPORTED, "unsupported Copilot status does not launch a command or claim authentication");
        String tricky = "/tmp/CLI with spaces/it's $(literal);`literal`";
        check(AccountAuth.loginCommand(AgentSession.Provider.Claude, tricky).equals(List.of(tricky, "auth", "login", "--claudeai")), "login passes executable as one literal argument without shell interpolation");
        try { AccountAuth.executable("claude\nother-command"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { check(true, "control characters in executable rejected"); }
        check(AccountAuth.run(command("argv", tricky), Path.of("."), Duration.ofSeconds(5)).text().equals(tricky), "process runner preserves literal argument including shell metacharacters");
        var stderr = AccountAuth.run(command("stderr"), Path.of("."), Duration.ofSeconds(5));
        check(stderr.exitCode() == 1 && stderr.text().equals("Not logged in"), "status runner captures stderr and exit status");
        long start = System.nanoTime();
        try { AccountAuth.run(command("hang"), Path.of("."), Duration.ofMillis(250)); throw new AssertionError(); }
        catch (TimeoutException expected) { check(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 5, "hung status process has a bounded timeout"); }
        try { AccountAuth.run(command("large"), Path.of("."), Duration.ofSeconds(5)); throw new AssertionError(); }
        catch (IllegalStateException expected) { check(true, "oversized status output rejected"); }
        var done = new CompletableFuture<Boolean>();
        Thread worker = Thread.ofPlatform().start(() -> {
            try { AccountAuth.run(command("hang"), Path.of("."), Duration.ofSeconds(30)); done.complete(false); }
            catch (InterruptedException expected) { done.complete(true); }
            catch (Exception unexpected) { done.completeExceptionally(unexpected); }
        });
        Thread.sleep(200); worker.interrupt();
        check(done.get(3, TimeUnit.SECONDS), "cancelling a status check interrupts and cleans up its process");
        System.out.println("Passed " + passed + " authentication checks (no real login or model request).");
    }
    private static List<String> command(String... args) {
        var command = new java.util.ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", System.getProperty("java.class.path"), AuthTest.class.getName()));
        command.addAll(List.of(args)); return command;
    }
}
