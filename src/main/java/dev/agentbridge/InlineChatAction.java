package dev.agentbridge;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public final class InlineChatAction extends DumbAwareAction {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(@NotNull AnActionEvent event) {
        var editor = event.getData(CommonDataKeys.EDITOR);
        event.getPresentation().setEnabledAndVisible(InlineChat.eligible(editor)
            && editor.getCaretModel().getCaretCount() == 1 && editor.getSelectionModel().hasSelection());
    }
    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        var editor = event.getData(CommonDataKeys.EDITOR);
        if (!InlineChat.eligible(editor) || editor.getCaretModel().getCaretCount() != 1) return;
        var selection = editor.getSelectionModel();
        if (selection.hasSelection()) InlineChat.open(editor, selection.getSelectionStart(), selection.getSelectionEnd());
    }
}
