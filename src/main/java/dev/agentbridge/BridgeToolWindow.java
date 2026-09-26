package dev.agentbridge;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class BridgeToolWindow implements ToolWindowFactory, DumbAware {
    @Override public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
        ChatPanel panel = new ChatPanel(project);
        var content = ContentFactory.getInstance().createContent(panel.component(), "", false);
        content.setDisposer(panel);
        window.getContentManager().addContent(content);
    }
}
