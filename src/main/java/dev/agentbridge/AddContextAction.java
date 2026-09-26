package dev.agentbridge;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import java.util.*;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/** Uses IntelliJ's actual Project selection, without opening another file picker. */
public final class AddContextAction extends DumbAwareAction {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(@NotNull AnActionEvent event) {
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        event.getPresentation().setEnabledAndVisible(event.getProject() != null && files != null && files.length > 0);
    }
    @Override @SuppressWarnings("unchecked") public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() == null) return;
        VirtualFile[] files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY); if (files == null) return;
        var window = ToolWindowManager.getInstance(event.getProject()).getToolWindow("Agent Bridge");
        if (window == null) return;
        List<VirtualFile> selected = List.copyOf(Arrays.asList(files));
        window.activate(() -> {
            var content = window.getContentManager().getSelectedContent();
            if (content != null && content.getComponent() instanceof ChatSurface surface) {
                Object attach = surface.getClientProperty("agentBridge.attach");
                if (attach instanceof Consumer<?>) ((Consumer<List<VirtualFile>>)attach).accept(selected);
            }
        });
    }
}
