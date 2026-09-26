package dev.agentbridge;

import java.util.List;

public final class ConversationHistoryTest {
    private static int passed;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        passed++;
        System.out.println("PASS " + label);
    }
    public static void main(String[] args) {
        var history = new ConversationHistory();
        check(!history.hasConversation(), "empty chat needs no switch prompt");
        history.sent();
        check(history.hasConversation() && history.forSession(true).turns().isEmpty(), "failed or stopped turn still prompts but is not transferred");
        history.completed("Claude", "sonnet", "Remember \"queue\" 🌍", "Use leases.\n```java\nreserve();\n```");
        var original = history.forSession(true);
        history.apply(ConversationHistory.choice(0));
        check(history.forSession(true).equals(original), "continue keeps completed conversation");
        history.apply(ConversationHistory.choice(2));
        history.apply(ConversationHistory.choice(-1));
        check(history.forSession(true).equals(original), "cancel and closing dialog preserve history");
        check(history.forSession(false).turns().isEmpty(), "existing native session does not receive duplicate history");
        check(history.forSession(true).equals(original), "failed new session can retry the same handoff");
        history.apply(ConversationHistory.Choice.CONTINUE);
        check(history.forSession(true).equals(original), "consecutive switches without sending retain handoff");
        var packet = new ContextPacket(List.of(new ContextPacket.File("Queue.java", "fresh unsaved buffer")), "Queue.java", "");
        String prompt = packet.prompt("What next?", false, original);
        String encoded = prompt.split("\nPREVIOUS_CONVERSATION_JSON\n", 2)[1].split("\n\nCURRENT_EDITOR_CONTEXT_JSON\n", 2)[0];
        var decoded = Json.GSON.fromJson(encoded, ConversationHistory.Snapshot.class);
        check(decoded.equals(original), "structured history round trips quotes, Unicode and code");
        check(prompt.endsWith("USER_REQUEST\nWhat next?"), "new request appears separately after handoff");
        check(prompt.contains("fresh unsaved buffer") && prompt.contains("Current editor buffers take precedence"), "fresh buffers remain authoritative");
        check(prompt.contains("Do not replay past tool calls or edit proposals"), "historical suggestions cannot authorize replay");
        check(!packet.prompt("What next?", false).contains("PREVIOUS_CONVERSATION_JSON"), "fresh prompt excludes handoff entirely");
        history.completed("Codex", "fixture-model", "Next question", "Next answer");
        check(original.turns().size() == 1 && history.forSession(true).turns().size() == 2, "in-flight snapshot immutable and subsequent provider exchange appended");
        check(history.forSession(true).turns().get(1).provider().equals("Codex"), "handoff keeps provider provenance");
        history.apply(ConversationHistory.choice(1));
        check(!history.hasConversation() && history.forSession(true).turns().isEmpty(), "start fresh removes prior session context");
        for (int i = 0; i < 20; i++) history.completed("Claude", "sonnet", "q" + i, "a" + i);
        var bounded = history.forSession(true);
        check(bounded.turns().size() == 12 && bounded.turns().getFirst().user().equals("q8"), "oldest complete exchanges evicted beyond turn limit");
        check(bounded.truncated(), "omitted history is marked explicitly");
        history.clear();
        for (int i = 0; i < 5; i++) history.completed("Claude", "sonnet", "q".repeat(16000), "🌍".repeat(15000));
        bounded = history.forSession(true);
        check(bounded.turns().stream().mapToInt(t -> t.user().length() + t.assistant().length()).sum() <= 48000, "aggregate history bounded to 48000 text characters");
        check(bounded.turns().stream().allMatch(t -> t.assistant().length() <= 12000 && t.user().length() <= 12000), "individual messages bounded");
        check(bounded.turns().stream().allMatch(t -> !t.assistant().matches("(?s).*\\p{Cs}.*")), "truncation preserves Unicode surrogate pairs");
        history.clear();
        check(!history.forSession(true).truncated(), "new chat resets truncation state");
        System.out.println("Passed " + passed + " conversation history checks.");
    }
}
