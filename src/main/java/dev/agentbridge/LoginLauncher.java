package dev.agentbridge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import java.util.List;

/** Optional Terminal integration. The base plugin works without the Terminal plugin. */
public interface LoginLauncher {
    interface Handle { void cancel(); }
    Handle launch(Project project, String title, List<String> command, Disposable owner, Runnable terminated);
}
