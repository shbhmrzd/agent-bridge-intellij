package dev.agentbridge;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in live smoke test. Sends only synthetic code, through the existing Claude CLI account. */
public final class LiveClaudeTest {
    public static void main(String[] args) throws Exception {
        Path cwd = Path.of(args[0]).toAbsolutePath(); Files.createDirectories(cwd);
        String before = "class Calculator { int add(int a, int b) { return a - b; } }\n";
        ContextPacket context = new ContextPacket(List.of(new ContextPacket.File("Calculator.java", before)), "Calculator.java", "");
        StringBuilder output = new StringBuilder();
        AgentSession.Listener listener = new AgentSession.Listener() {
            public void text(String text) { output.append(text); }
            public void status(String status) { }
            public CompletableFuture<String> permission(String title, String detail, List<AgentSession.Choice> choices) {
                return CompletableFuture.completedFuture(null);
            }
        };
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (AgentSession session = new AgentSession(AgentSession.Provider.Claude, args[1], cwd, "", false, listener)) {
            try {
                worker.submit(() -> {
                    try { session.prompt(context.prompt("Fix add so it returns a plus b. Give one minimal suggested edit. Use only the attached buffer; do not call tools.", true)); }
                    catch (Exception e) { throw new CompletionException(e); }
                }).get(90, TimeUnit.SECONDS);
                EditProposal.Result result = EditProposal.parse(output.toString(), context.buffers());
                if (result.changes().size() != 1 || !result.changes().get(0).after().contains("a + b"))
                    throw new AssertionError("Claude responded but did not produce the expected reviewable fix");
                System.out.println("PASS live Claude: saved login → synthetic editor context → streamed answer → validated edit proposal; no files applied");
            } finally { worker.shutdownNow(); }
        }
    }
}
