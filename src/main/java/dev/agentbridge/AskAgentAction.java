package dev.agentbridge;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/** Editor entry point keeps the current file/selection in focus until the tool window opens. */
public final class AskAgentAction extends DumbAwareAction {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && event.getData(CommonDataKeys.EDITOR) != null);
    }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() == null) return;
        var window = ToolWindowManager.getInstance(event.getProject()).getToolWindow("Agent Bridge");
        if (window == null) return;
        window.activate(() -> {
            var content = window.getContentManager().getSelectedContent();
            if (content != null && content.getComponent() instanceof ChatSurface surface) {
                if (!surface.currentFile.isSelected()) surface.currentFile.doClick();
                surface.composer.requestFocusInWindow();
            }
        });
    }
}
