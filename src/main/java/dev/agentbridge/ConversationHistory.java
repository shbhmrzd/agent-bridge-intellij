package dev.agentbridge;

import java.util.ArrayList;
import java.util.List;

/** EDT-owned, bounded conversation handoff. Never retains editor buffers or CLI credentials. */
final class ConversationHistory {
    static final int TEXT_LIMIT = 48000;
    static final int MESSAGE_LIMIT = 12000;
    static final int TURN_LIMIT = 12;
    enum Choice { CONTINUE, FRESH, CANCEL }
    record Turn(String provider, String model, String user, String assistant) { }
    record Snapshot(List<Turn> turns, boolean truncated) {
        Snapshot { turns = List.copyOf(turns); }
        static Snapshot empty() { return new Snapshot(List.of(), false); }
    }
    private final List<Turn> turns = new ArrayList<>();
    private boolean visibleConversation;
    private boolean truncated;

    static Choice choice(int index) {
        return index == 0 ? Choice.CONTINUE : index == 1 ? Choice.FRESH : Choice.CANCEL;
    }
    boolean hasConversation() { return visibleConversation; }
    void sent() { visibleConversation = true; }
    void completed(String provider, String model, String user, String assistant) {
        sent();
        turns.add(new Turn(provider, model, bounded(user), bounded(assistant)));
        while (turns.size() > TURN_LIMIT || textSize() > TEXT_LIMIT) {
            turns.remove(0); truncated = true;
        }
    }
    private int textSize() { return turns.stream().mapToInt(t -> t.user().length() + t.assistant().length()).sum(); }
    private String bounded(String text) {
        if (text.length() <= MESSAGE_LIMIT) return text;
        truncated = true;
        String suffix = "\n[Message truncated for handoff]";
        int end = MESSAGE_LIMIT - suffix.length();
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end) + suffix;
    }
    Snapshot forSession(boolean newSession) { return newSession ? new Snapshot(turns, truncated) : Snapshot.empty(); }
    void apply(Choice choice) { if (choice == Choice.FRESH) clear(); }
    void clear() { turns.clear(); visibleConversation = false; truncated = false; }
}
