package dev.agentbridge;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** Delegates authentication to the installed CLI; never reads credential files. */
final class AccountAuth {
    enum Status { SIGNED_IN, SIGNED_OUT, CONFIGURED, UNKNOWN, UNSUPPORTED }
    record Result(Status status, String label, String detail) { }
    record Output(int exitCode, String text) { }
    static String executable(String value) {
        String command = value.trim();
        if (command.isEmpty() || command.startsWith("-") || command.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Enter the CLI executable path in Settings, without arguments.");
        return command;
    }
    static List<String> loginCommand(AgentSession.Provider provider, String value) {
        return loginCommand(provider, value, false);
    }
    static List<String> loginCommand(AgentSession.Provider provider, String value, boolean sso) {
        String command = executable(value);
        return switch (provider) {
            case Claude -> sso ? List.of(command, "auth", "login", "--claudeai", "--sso") : List.of(command, "auth", "login", "--claudeai");
            case Codex, Copilot -> List.of(command, "login");
        };
    }
    static Result check(AgentSession.Provider provider, String executable) {
        if (provider == AgentSession.Provider.Copilot)
            return new Result(Status.UNSUPPORTED, "CLI-managed", "This Copilot CLI has no standalone login-status command. Its sign-in terminal confirms completion; send a message to verify access.");
        try {
            if (provider == AgentSession.Provider.Codex) return new CodexAuth(executable).read();
            List<String> command = List.of(executable(executable), "auth", "status", "--json");
            Output output = run(command, Path.of(System.getProperty("user.home")), Duration.ofSeconds(10));
            return interpret(provider, output);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return unknown("Login check cancelled.");
        } catch (TimeoutException timeout) {
            return unknown("The CLI login check timed out. Check the executable in Settings, then retry.");
        } catch (Exception error) {
            return unknown("Could not check login. Check the executable in Settings and try Sign in.");
        }
    }
    static Result interpret(AgentSession.Provider provider, Output output) {
        if (provider == AgentSession.Provider.Claude) {
            try {
                JsonObject json = JsonParser.parseString(output.text()).getAsJsonObject();
                JsonElement value = json.get("loggedIn");
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                    return unknown("The CLI returned an unrecognized login status. Update Claude Code or use Sign in.");
                if (value.getAsBoolean() && output.exitCode() == 0)
                    return signedIn();
                if (!value.getAsBoolean()) return signedOut();
            } catch (RuntimeException ignored) { /* Raw CLI output can contain account data; do not display it. */ }
        }
        return unknown("The CLI did not confirm a login. Try Sign in, or check your provider configuration.");
    }
    private static Result signedIn() {
        return new Result(Status.SIGNED_IN, "Signed in", "The CLI reports an existing login. The provider checks account access and usage limits when you send a message. Existing API-key or enterprise configuration still applies.");
    }
    private static Result signedOut() { return new Result(Status.SIGNED_OUT, "Not signed in", "Click Sign in to start the installed CLI's browser login."); }
    private static Result unknown(String detail) { return new Result(Status.UNKNOWN, "Not verified", detail); }

    // No shell, no model call, bounded output, deadline, and cleanup on cancellation.
    static Output run(List<String> command, Path directory, Duration timeout) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        FutureTask<byte[]> capture = new FutureTask<>(() -> {
            byte[] bytes = process.getInputStream().readNBytes(32769);
            if (bytes.length > 32768) process.destroyForcibly();
            return bytes;
        });
        Thread reader = Thread.ofVirtual().name("agent-bridge-auth-output").start(capture);
        try {
            process.getOutputStream().close();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new TimeoutException();
            byte[] output = capture.get(1, TimeUnit.SECONDS);
            if (output.length > 32768) throw new IllegalStateException("Login-status output exceeds its limit");
            return new Output(process.exitValue(), new String(output, StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            capture.cancel(true); reader.interrupt();
            process.getInputStream().close();
        }
    }
}
