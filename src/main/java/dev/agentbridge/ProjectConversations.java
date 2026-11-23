package dev.agentbridge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import java.nio.file.Path;

@Service(Service.Level.PROJECT)
public final class ProjectConversations implements Disposable {
    final ConversationArchive archive;
    public ProjectConversations(Project project) {
        archive = new ConversationArchive(new ConversationStore(ConversationStore.projectDirectory(
            Path.of(PathManager.getConfigPath()), project.getBasePath() == null ? project.getLocationHash() : project.getBasePath())));
    }
    @Override public void dispose() {
        archive.close();
        try {
            if (!archive.awaitClosed(5)) Logger.getInstance(ProjectConversations.class).warn("Saved conversation writes did not finish before project shutdown.");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
