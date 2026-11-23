package dev.agentbridge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.PROJECT)
public final class ProjectConversations implements Disposable {
    final ConversationArchive archive;
    private final Set<ChatPanel> panels = ConcurrentHashMap.newKeySet();
    public ProjectConversations(Project project) {
        archive = new ConversationArchive(new ConversationStore(ConversationStore.projectDirectory(
            Path.of(PathManager.getConfigPath()), project.getBasePath() == null ? project.getLocationHash() : project.getBasePath())));
    }
    void track(ChatPanel panel) { panels.add(panel); }
    void untrack(ChatPanel panel) { panels.remove(panel); }
    @Override public void dispose() {
        // Project services can close before tool-window contents. Settle panels first so their
        // final interrupted responses reach the queue before it stops accepting writes.
        for (ChatPanel panel : Set.copyOf(panels)) Disposer.dispose(panel);
        panels.clear();
        archive.close();
        try {
            if (!archive.awaitClosed(5)) Logger.getInstance(ProjectConversations.class).warn("Saved conversation writes did not finish before project shutdown.");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
