package dev.agentbridge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.*;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;

/** A debounced, editor-local gutter affordance; selecting code never starts an agent or reads files. */
public final class SelectionChatListener implements EditorFactoryListener {
    private static final Key<State> STATE = Key.create("agentBridge.selectionIcon");
    @Override public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!InlineChat.eligible(editor) || editor.getUserData(STATE) != null) return;
            State state = new State(editor);
            editor.putUserData(STATE, state);
            Disposer.register(editor.getProject(), state);
        });
    }
    @Override public void editorReleased(@NotNull EditorFactoryEvent event) {
        State state = event.getEditor().getUserData(STATE);
        if (state != null) Disposer.dispose(state);
        InlineChat.close(event.getEditor());
    }
    private static final class State implements Disposable, SelectionListener {
        private final Editor editor;
        private final Timer debounce;
        private RangeHighlighter icon;
        private boolean disposed;
        State(Editor editor) {
            this.editor = editor;
            debounce = new Timer(160, e -> update()); debounce.setRepeats(false);
            editor.getSelectionModel().addSelectionListener(this);
            debounce.start();
        }
        @Override public void selectionChanged(@NotNull SelectionEvent event) {
            if (disposed) return;
            clearIcon(); debounce.restart();
        }
        private void update() {
            if (disposed || !InlineChat.eligible(editor)) return;
            var selection = editor.getSelectionModel();
            if (!selection.hasSelection() || editor.getCaretModel().getCaretCount() != 1) return;
            int start = selection.getSelectionStart(), end = selection.getSelectionEnd();
            long stamp = editor.getDocument().getModificationStamp();
            clearIcon();
            icon = editor.getMarkupModel().addLineHighlighter(editor.getDocument().getLineNumber(start), HighlighterLayer.ADDITIONAL_SYNTAX, null);
            icon.setGutterIconRenderer(new GutterIconRenderer() {
                @Override public @NotNull Icon getIcon() { return IconLoader.getIcon("/icons/selection-chat.svg", SelectionChatListener.class); }
                @Override public String getTooltipText() { return "Chat about selected code · Agent Bridge"; }
                @Override public boolean isNavigateAction() { return true; }
                @Override public boolean isDumbAware() { return true; }
                @Override public Alignment getAlignment() { return Alignment.RIGHT; }
                @Override public AnAction getClickAction() {
                    return new DumbAwareAction("Chat about selected code") {
                        @Override public void actionPerformed(@NotNull AnActionEvent event) {
                            // Gutter clicks can clear selection before the action runs; use its captured offsets.
                            if (!editor.isDisposed() && editor.getDocument().getModificationStamp() == stamp)
                                InlineChat.open(editor, start, end);
                        }
                    };
                }
                @Override public boolean equals(Object other) { return this == other; }
                @Override public int hashCode() { return System.identityHashCode(this); }
            });
        }
        private void clearIcon() {
            if (icon != null) { icon.dispose(); icon = null; }
        }
        @Override public void dispose() {
            if (disposed) return;
            disposed = true; debounce.stop();
            editor.getSelectionModel().removeSelectionListener(this); clearIcon();
            editor.putUserData(STATE, null); InlineChat.close(editor);
        }
    }
}
