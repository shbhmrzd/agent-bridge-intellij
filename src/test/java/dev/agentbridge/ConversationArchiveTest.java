package dev.agentbridge;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class ConversationArchiveTest {
    private static int passed;
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        passed++; System.out.println("PASS " + name);
    }
    private static <T> T get(CompletableFuture<T> future) throws Exception { return future.get(10, TimeUnit.SECONDS); }
    private static void fails(CompletableFuture<?> future, String name) throws Exception {
        try { get(future); throw new AssertionError(name); }
        catch (ExecutionException expected) { check(true, name); }
    }
    private static ConversationStore.Turn turn(String question) {
        return new ConversationStore.Turn(UUID.randomUUID().toString(), "Claude", "sonnet", question, "", ConversationStore.Status.INTERRUPTED);
    }
    private static void layout(Container component) {
        component.doLayout(); for (Component child : component.getComponents()) if (child instanceof Container c) layout(c);
    }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("agent-bridge-conversations-test-");
        try {
            Path first = ConversationStore.projectDirectory(root, "/projects/one");
            Path second = ConversationStore.projectDirectory(root, "/projects/two");
            check(!first.equals(second) && first.equals(ConversationStore.projectDirectory(root, "/projects/other/../one")), "project isolation uses stable normalized paths");
            check(!first.toString().contains("projects/one"), "storage path does not expose the project path");
            var store = new ConversationStore(first); var other = new ConversationStore(second);
            String id = UUID.randomUUID().toString(); Object sidebar = new Object(), popup = new Object();
            var question = turn("Earlier question");
            ConversationStore.Chat restored;
            try (var archive = new ConversationArchive(store)) {
                check(get(archive.list()).isEmpty(), "first run has an empty saved-chat list");
                var pending = get(archive.begin(id, sidebar, question));
                check(pending.turns().getFirst().status() == ConversationStore.Status.INTERRUPTED, "question is durable before a response completes");
                check(other.list().isEmpty(), "a different project cannot see saved chats");
                restored = get(archive.finish(id, sidebar, question.id(), "Earlier answer 🌍", ConversationStore.Status.COMPLETED));
                check(restored.turns().getFirst().assistant().equals("Earlier answer 🌍"), "completed response survives UTF-8 disk round trip");
                fails(archive.resume(id, popup), "sidebar and popup cannot resume the same active conversation concurrently");
                fails(archive.rename(id, popup, "Other"), "another surface cannot rename an open chat");
                fails(archive.delete(id, popup), "another surface cannot delete an open chat");
                get(archive.rename(id, sidebar, "  Queue\n design 🌍  "));
                check(store.read(id).title().equals("Queue design 🌍"), "rename normalizes whitespace and preserves Unicode");
                fails(archive.rename(id, sidebar, "  "), "blank rename is rejected");
                fails(archive.rename(id, sidebar, "x".repeat(121)), "overlong rename is rejected");
                var partial = turn("Unfinished question");
                get(archive.begin(id, sidebar, partial));
                get(archive.finish(id, sidebar, partial.id(), "partial response", ConversationStore.Status.INTERRUPTED));
                var failed = turn("Failed question");
                get(archive.begin(id, sidebar, failed));
                get(archive.finish(id, sidebar, failed.id(), "partial failure", ConversationStore.Status.FAILED));
                get(archive.release(sidebar));
                restored = get(archive.resume(id, popup));
                check(restored.turns().size() == 3 && restored.title().equals("Queue design 🌍"), "release and resume keep name and all turn states");
            }
            try (var restarted = new ConversationArchive(new ConversationStore(first))) {
                restored = get(restarted.resume(id, sidebar));
                check(restored.turns().size() == 3, "restart reconstructs saved conversations without in-memory state");
                var history = new ConversationHistory(); history.restore(restored);
                check(history.hasConversation() && history.forSession(true).turns().size() == 1, "only completed saved turns become resumed context");
                var packet = new ContextPacket(List.of(new ContextPacket.File("Queue.java", "fresh unsaved buffer")), "Queue.java", "");
                for (var provider : AgentSession.Provider.values()) {
                    StringBuilder response = new StringBuilder();
                    var listener = new AgentSession.Listener() {
                        public void text(String text) { response.append(text); }
                        public void status(String text) { }
                        public CompletableFuture<String> permission(String title, String detail, List<AgentSession.Choice> choices) { return CompletableFuture.completedFuture(null); }
                    };
                    try (var session = new AgentSession(provider, Path.of(args[0]).toAbsolutePath().toString(), root, "fixture-model", false, listener)) {
                        session.prompt(packet.prompt("fixture-handoff-check", false, history.forSession(true)));
                    }
                    check(response.toString().equals(provider + " fixture reply"), provider + " receives saved history after restart with fresh editor context");
                }
                String editId = UUID.randomUUID().toString(); var editTurn = turn("Suggest an edit");
                get(restarted.begin(editId, popup, editTurn));
                var editChat = get(restarted.finish(editId, popup, editTurn.id(), "Explanation\n```agent-bridge-edit\n{\"oldText\":\"private snapshot\"}\n```", ConversationStore.Status.COMPLETED));
                check(!Json.GSON.toJson(editChat).contains("private snapshot") && !editChat.turns().getFirst().assistant().contains("```agent-bridge-edit"), "saved transcripts exclude executable edit payloads and their buffer snippets");
                Path abandoned = first.resolve("." + editId + "-crash.tmp"); Files.writeString(abandoned, "unfinished save");
                get(restarted.delete(editId, popup));
                check(!Files.exists(abandoned), "deletion removes temporary data left by an interrupted save");
                fails(restarted.finish(editId, popup, editTurn.id(), "late callback", ConversationStore.Status.COMPLETED), "a late completion cannot resurrect a deleted chat");
                check(!store.exists(editId), "delete removes the on-disk conversation");
                get(restarted.release(sidebar));
            }
            String serialized = Files.readString(first.resolve(id + ".json"));
            check(!serialized.contains("fresh unsaved buffer") && !serialized.contains("session_id") && !serialized.contains("executable"), "storage contains transcript data, not attached buffers or CLI state");
            if (Files.getFileStore(first).supportsFileAttributeView("posix"))
                check(Files.getPosixFilePermissions(first.resolve(id + ".json")).equals(PosixFilePermissions.fromString("rw-------")), "saved transcripts are owner-readable and owner-writable only");
            var oldChat = store.read(id);
            try { store.update(new ConversationStore.Chat(1, id, "", 1, oldChat.turns(), false)); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { check(Files.readString(first.resolve(id + ".json")).equals(serialized), "invalid save preserves the previous file exactly"); }
            String corruptId = UUID.randomUUID().toString(); Path corrupt = first.resolve(corruptId + ".json"); Files.writeString(corrupt, "{broken");
            check(store.list().stream().anyMatch(s -> s.id().equals(corruptId) && !s.readable()), "corrupt chat is listed for recovery or deletion without hiding healthy chats");
            try { store.read(corruptId); throw new AssertionError(); }
            catch (IOException expected) { check(Files.readString(corrupt).equals("{broken"), "corrupt data is never silently overwritten"); }
            Files.writeString(corrupt, serialized.replace("\"version\":1", "\"version\":99"));
            try { store.read(corruptId); throw new AssertionError(); }
            catch (IOException expected) { check(true, "unknown storage schema is rejected safely"); }
            store.delete(corruptId);
            try { store.read("../escape"); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { check(true, "conversation IDs cannot traverse outside storage"); }
            Path bad = root.resolve("not-a-directory"); Files.writeString(bad, "existing");
            try (var unavailable = new ConversationArchive(new ConversationStore(bad.resolve("chats")))) {
                fails(unavailable.begin(UUID.randomUUID().toString(), sidebar, question), "storage I/O errors propagate to the UI instead of claiming a successful save");
            }
            var many = new ArrayList<ConversationStore.Turn>();
            for (int i = 0; i < 110; i++) many.add(turn("Question " + i));
            var bounded = ConversationStore.bounded(new ConversationStore.Chat(1, id, "Long conversation", 1, many, false));
            check(bounded.turns().size() == 100 && bounded.truncated() && bounded.turns().getFirst().user().equals("Question 10"), "storage turn limit retains recent exchanges with an explicit truncation flag");
            many.clear();
            for (int i = 0; i < 12; i++) many.add(new ConversationStore.Turn(UUID.randomUUID().toString(), "Claude", "", "Q", "x".repeat(256000), ConversationStore.Status.COMPLETED));
            bounded = ConversationStore.bounded(new ConversationStore.Chat(1, id, "Large responses", 1, many, false));
            check(bounded.truncated() && bounded.turns().stream().mapToInt(t -> t.user().length() + t.assistant().length()).sum() <= ConversationStore.MAX_TEXT, "aggregate stored text remains bounded");
            var capacity = new ConversationStore(root.resolve("capacity"));
            for (int i = 0; i < ConversationStore.MAX_CHATS; i++) capacity.create(new ConversationStore.Chat(1, UUID.randomUUID().toString(), "Chat " + i, i, List.of(question), false));
            try { capacity.create(new ConversationStore.Chat(1, UUID.randomUUID().toString(), "Overflow", 101, List.of(question), false)); throw new AssertionError(); }
            catch (IOException expected) { check(capacity.list().size() == 100, "full archive refuses new chats without silently deleting existing ones"); }
            var draining = new ConversationArchive(new ConversationStore(root.resolve("drain")));
            String drainId = UUID.randomUUID().toString(); var start = draining.begin(drainId, sidebar, question);
            var finish = draining.finish(drainId, sidebar, question.id(), "Saved before closing", ConversationStore.Status.COMPLETED);
            draining.close();
            check(draining.awaitClosed(10) && get(start) != null && get(finish).turns().getFirst().assistant().equals("Saved before closing"), "closing a project drains pending saves in order");
            fails(draining.list(), "operations after disposal fail promptly");
            ConversationStore.Chat renderChat = restored;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    for (boolean dark : new boolean[]{false, true}) {
                        UIManager.put("Panel.background", new Color(dark ? 0x24262b : 0xf7f8fa));
                        UIManager.put("Label.foreground", new Color(dark ? 0xe1e4eb : 0x252a34));
                        for (boolean inline : new boolean[]{false, true}) {
                            ChatSurface surface = new ChatSurface(); if (inline) surface.inlineMode(); surface.restore(renderChat);
                            surface.savedState.setText("Saved · " + renderChat.title());
                            surface.setSize(380, 850); for (int i = 0; i < 5; i++) layout(surface);
                            check(surface.transcript.getComponentCount() == 6, "restored transcript includes interrupted and failed messages: " + dark + "/" + inline);
                            check(((JTextArea)((ChatSurface.Card)surface.transcript.getComponent(3)).metadata.getComponent(0)).getText().contains("excluded from resumed context"), "interrupted saved response is clearly labeled: " + dark + "/" + inline);
                            check(surface.send.getWidth() >= 40 && surface.composer.getHeight() >= 50, "saved-chat controls preserve narrow composer layout: " + dark + "/" + inline);
                            check(Arrays.stream(surface.settingsMenu().getComponents()).anyMatch(c -> c instanceof JMenuItem item && item.getText().equals("Saved chats…")), "history remains accessible in selection chat settings: " + dark + "/" + inline);
                            BufferedImage image = new BufferedImage(380, 850, BufferedImage.TYPE_INT_RGB);
                            Graphics2D graphics = image.createGraphics(); surface.printAll(graphics); graphics.dispose();
                            ImageIO.write(image, "png", Path.of(args[1], "saved-chat-" + dark + "-" + inline + ".png").toFile());
                        }
                    }
                    SavedChatsPanel panel = new SavedChatsPanel();
                    check(!panel.rename.isEnabled() && !panel.delete.isEnabled(), "empty history disables management actions");
                    panel.entries(List.of(new ConversationStore.Summary(id, "<html>Literal name 🌍", 1, true)), id); panel.enabled(true);
                    JLabel label = (JLabel)panel.list.getCellRenderer().getListCellRendererComponent(panel.list, panel.entries.get(0), 0, true, false);
                    check(Boolean.TRUE.equals(label.getClientProperty("html.disable")) && label.getText().startsWith("<html>"), "conversation names render as inert text");
                    check(panel.rename.isEnabled() && panel.delete.isEnabled(), "selecting a chat enables rename and delete");
                    panel.entries(List.of(new ConversationStore.Summary(id, "Unreadable", 0, false)), id); panel.enabled(true);
                    check(!panel.rename.isEnabled() && panel.delete.isEnabled(), "unreadable chats can be deleted without being resumed or renamed");
                    panel.setSize(520, 340); layout(panel);
                    BufferedImage image = new BufferedImage(520, 340, BufferedImage.TYPE_INT_RGB); Graphics2D graphics = image.createGraphics();
                    panel.printAll(graphics); graphics.dispose(); ImageIO.write(image, "png", Path.of(args[1], "saved-chat-list.png").toFile());
                    var maximum = new ConversationStore.Turn(UUID.randomUUID().toString(), "Claude", "", "Question", "x".repeat(256000), ConversationStore.Status.INTERRUPTED);
                    ChatSurface full = new ChatSurface(); full.restore(new ConversationStore.Chat(1, id, "Maximum response", 1, List.of(maximum), false));
                    check(((ChatSurface.Card)full.transcript.getComponent(1)).source.length() == 256000,
                        "status notice does not overflow the response limit when restoring a maximum-size partial answer");
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            System.out.println("Passed " + passed + " saved conversation checks (disk persistence, provider fixtures and Swing UI).");
        } finally {
            try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
}
