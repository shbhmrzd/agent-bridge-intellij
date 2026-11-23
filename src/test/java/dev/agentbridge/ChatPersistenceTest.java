package dev.agentbridge;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockProject;
import com.intellij.openapi.application.AsyncExecutionService;
import com.intellij.openapi.application.impl.AsyncExecutionServiceImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.Disposer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

/** Exercises the real ChatPanel lifecycle, background reads, provider fixture, and persistence queue. */
public final class ChatPersistenceTest {
    private static int passed;
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label); passed++; System.out.println("PASS " + label);
    }
    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    private static void until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            boolean[] done = {false}; edt(() -> done[0] = condition.getAsBoolean());
            if (done[0]) return; Thread.sleep(20);
        }
        throw new AssertionError("Chat operation did not finish within 15 seconds");
    }
    private static void resume(ChatPanel panel, ConversationArchive archive, String id) throws Exception {
        var ownerField = ChatPanel.class.getDeclaredField("savedOwner"); ownerField.setAccessible(true);
        var chat = archive.resume(id, ownerField.get(panel)).get(10, TimeUnit.SECONDS);
        var resume = ChatPanel.class.getDeclaredMethod("resume", ConversationStore.Chat.class); resume.setAccessible(true);
        edt(() -> { try { resume.invoke(panel, chat); } catch (ReflectiveOperationException error) { throw new RuntimeException(error); } });
    }
    public static void main(String[] args) throws Exception {
        int exit = 0; var lifetime = Disposer.newDisposable();
        Path root = Files.createTempDirectory("agent-bridge-panel-test-");
        System.setProperty("idea.config.path", root.resolve("ide-config").toString());
        var store = new ConversationStore(root.resolve("saved"));
        var archive = new ConversationArchive(store);
        ChatPanel[] panels = new ChatPanel[2];
        try {
            var application = MockApplication.setUp(lifetime);
            application.registerService(AsyncExecutionService.class, new AsyncExecutionServiceImpl());
            application.registerService(ProgressManager.class, new CoreProgressManager());
            var properties = new TestProperties();
            properties.setValue("agentBridge.executable.claude", Path.of(args[0]).toAbsolutePath().toString());
            properties.setValue("agentBridge.model.claude", "fixture-model");
            application.registerService(PropertiesComponent.class, properties);
            var project = new MockProject(application.getPicoContainer(), lifetime) {
                @Override public String getBasePath() { return root.toString(); }
                @Override public String getName() { return "Persistence fixture"; }
            };
            edt(() -> {
                panels[0] = new ChatPanel(project, null, archive, () -> null);
                var view = (ChatSurface)panels[0].component(); view.projectContext.setSelected(false);
                view.composer.setText("Persist this question"); view.send.doClick();
            });
            var view = (ChatSurface)panels[0].component();
            until(() -> view.status.getText().startsWith("Done") && view.savedState.getText().startsWith("Saved ·"));
            String id = archive.list().get(10, TimeUnit.SECONDS).getFirst().id();
            check(store.read(id).turns().getFirst().status() == ConversationStore.Status.COMPLETED, "chat Send automatically saves the completed exchange");
            edt(() -> view.fresh.doClick());
            check(store.exists(id), "New chat preserves the previous saved conversation");
            edt(() -> check(view.transcript.getComponentCount() == 0, "New chat clears the current transcript"));
            resume(panels[0], archive, id);
            edt(() -> {
                check(view.transcript.getComponentCount() == 2 && view.provider.getSelectedItem() == AgentSession.Provider.Claude,
                    "resume restores messages and provider into the real chat controller");
                view.composer.setText("Follow up"); view.send.doClick();
            });
            until(() -> view.status.getText().startsWith("Done") && view.savedState.getText().startsWith("Saved ·"));
            check(store.read(id).turns().size() == 2 && store.read(id).title().equals("Persist this question"), "resumed follow-up appends to the original saved conversation");
            edt(() -> { view.composer.setText("fixture-slow"); view.send.doClick(); });
            until(() -> view.transcript.getComponentCount() == 6 && ((ChatSurface.Card)view.transcript.getComponent(5)).source.length() > 0);
            edt(() -> {
                check(!view.saved.isEnabled() && !view.fresh.isEnabled(), "history and new-chat actions are disabled during generation");
                view.stop.doClick();
            });
            until(() -> view.savedState.getText().startsWith("Saved ·"));
            archive.list().get(10, TimeUnit.SECONDS);
            check(store.read(id).turns().getLast().status() == ConversationStore.Status.INTERRUPTED
                && store.read(id).turns().getLast().assistant().contains("Claude fixture reply"), "Stop saves partial text as interrupted");
            edt(() -> { view.composer.setText("fixture-failure"); view.send.doClick(); });
            until(() -> view.status.getText().startsWith("Request failed") && view.savedState.getText().startsWith("Saved ·"));
            archive.list().get(10, TimeUnit.SECONDS);
            check(store.read(id).turns().getLast().status() == ConversationStore.Status.FAILED, "provider failure is persisted as failed instead of completed");
            edt(() -> { view.composer.setText("fixture-slow"); view.send.doClick(); });
            until(() -> view.transcript.getComponentCount() == 10 && ((ChatSurface.Card)view.transcript.getComponent(9)).source.length() > 0);
            edt(() -> { Disposer.dispose(panels[0]); panels[0] = null; });
            archive.list().get(10, TimeUnit.SECONDS);
            check(store.read(id).turns().getLast().status() == ConversationStore.Status.INTERRUPTED, "closing a chat saves interrupted state and releases its session");
            edt(() -> panels[1] = new ChatPanel(project, null, archive, () -> null));
            resume(panels[1], archive, id);
            var historyField = ChatPanel.class.getDeclaredField("history"); historyField.setAccessible(true);
            edt(() -> {
                try { check(((ConversationHistory)historyField.get(panels[1])).forSession(true).turns().size() == 2,
                    "recreated panel restores only the two successful exchanges into provider context"); }
                catch (IllegalAccessException error) { throw new RuntimeException(error); }
            });
            edt(() -> { Disposer.dispose(panels[1]); panels[1] = null; });
            Path config = Path.of(com.intellij.openapi.application.PathManager.getConfigPath());
            check(config.startsWith(root), "project-service persistence uses an isolated test IDE profile");
            var service = new ProjectConversations(project);
            var serviceStore = new ConversationStore(ConversationStore.projectDirectory(config, root.toString()));
            edt(() -> {
                panels[1] = new ChatPanel(project, null, service.archive, () -> null); panels[1].track(service);
                var closingView = (ChatSurface)panels[1].component(); closingView.projectContext.setSelected(false);
                closingView.composer.setText("fixture-slow"); closingView.send.doClick();
            });
            var closingView = (ChatSurface)panels[1].component();
            until(() -> closingView.transcript.getComponentCount() == 2 && ((ChatSurface.Card)closingView.transcript.getComponent(1)).source.length() > 0);
            edt(service::dispose); panels[1] = null;
            var closedChat = serviceStore.read(serviceStore.list().getFirst().id());
            check(closedChat.turns().getFirst().status() == ConversationStore.Status.INTERRUPTED
                && closedChat.turns().getFirst().assistant().contains("Claude fixture reply"),
                "service-first project disposal closes panels and drains their final response save");
            System.out.println("Passed " + passed + " chat persistence integration checks (real controller, mock IDE services, no paid model calls).");
        } catch (Throwable error) { error.printStackTrace(); exit = 1; }
        finally {
            edt(() -> { for (ChatPanel panel : panels) if (panel != null) Disposer.dispose(panel); });
            archive.close(); archive.awaitClosed(10); Disposer.dispose(lifetime);
            try (var paths = Files.walk(root)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
        System.exit(exit);
    }
    private static final class TestProperties extends PropertiesComponent {
        private final Map<String, String> values = new HashMap<>();
        public void unsetValue(String name) { values.remove(name); }
        public boolean isValueSet(String name) { return values.containsKey(name); }
        public String getValue(String name) { return values.get(name); }
        public void setValue(String name, String value) { if (value == null) values.remove(name); else values.put(name, value); }
        public void setValue(String name, String value, String defaultValue) { setValue(name, Objects.equals(value, defaultValue) ? null : value); }
        public void setValue(String name, float value, float defaultValue) { setValue(name, value == defaultValue ? null : Float.toString(value)); }
        public void setValue(String name, int value, int defaultValue) { setValue(name, value == defaultValue ? null : Integer.toString(value)); }
        public void setValue(String name, boolean value, boolean defaultValue) { setValue(name, value == defaultValue ? null : Boolean.toString(value)); }
        // Required legacy abstract methods of the SDK's test double; production never calls them.
        @Deprecated public String[] getValues(String name) { return values.containsKey(name) ? values.get(name).split("\n") : null; }
        @Deprecated public void setValues(String name, String[] value) { setValue(name, value == null ? null : String.join("\n", value)); }
        public List<String> getList(String name) { return values.containsKey(name) ? List.of(values.get(name).split("\n")) : null; }
        public void setList(String name, Collection<String> value) { setValue(name, value == null ? null : String.join("\n", value)); }
        public boolean updateValue(String name, boolean value) { boolean old = getBoolean(name); setValue(name, value); return old != value; }
    }
}
