package dev.agentbridge;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.awt.RelativePoint;
import java.awt.*;
import javax.swing.JComponent;

/** Editor-anchored chat; one conversation per editor, explicitly closed with Escape or ×. */
final class InlineChat {
    private static final Key<JBPopup> POPUP = Key.create("agentBridge.inlinePopup");

    static boolean eligible(Editor editor) {
        return editor != null && !editor.isDisposed() && editor.getEditorKind() == EditorKind.MAIN_EDITOR
            && editor.getProject() != null && !editor.getProject().isDisposed()
            && FileDocumentManager.getInstance().getFile(editor.getDocument()) != null;
    }
    static void open(Editor editor, int start, int end) {
        if (!eligible(editor)) return;
        JBPopup existing = editor.getUserData(POPUP);
        if (existing != null && !existing.isDisposed()) {
            ((JComponent) existing.getContent().getClientProperty("agentBridge.inlineFocus")).requestFocusInWindow();
            return;
        }
        if (start < 0 || end <= start || end > editor.getDocument().getTextLength()) return;
        SelectionContext selection = new SelectionContext(editor.getDocument(), start, end);
        ChatPanel panel = new ChatPanel(editor.getProject(), selection);
        panel.component().setPreferredSize(new Dimension(480, panel.component().getPreferredSize().height));
        JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel.component(), panel.preferredFocus())
            .setProject(editor.getProject()).setTitle("Agent Bridge · selected code")
            .setResizable(true).setMovable(true).setFocusable(true).setRequestFocus(true)
            .setMinSize(new Dimension(340, panel.component().getPreferredSize().height)).setLocateWithinScreenBounds(true)
            .setCancelOnClickOutside(false).setCancelOnOtherWindowOpen(false).setCancelOnWindowDeactivation(false)
            .setCancelKeyEnabled(true).setCancelButton(new IconButton("Close selection chat", AllIcons.Actions.Close))
            .createPopup();
        Disposer.register(popup, panel);
        Disposer.register(editor.getProject(), popup);
        popup.getContent().putClientProperty("agentBridge.inlineFocus", panel.preferredFocus());
        panel.component().putClientProperty("agentBridge.conversation", (Runnable) () -> {
            popup.setSize(new Dimension(Math.max(480, popup.getSize().width), Math.max(540, popup.getSize().height)));
            popup.moveToFitScreen();
        });
        editor.putUserData(POPUP, popup);
        popup.addListener(new JBPopupListener() {
            @Override public void onClosed(LightweightWindowEvent event) {
                if (editor.getUserData(POPUP) == popup) editor.putUserData(POPUP, null);
            }
        });
        try {
            Point point = editor.offsetToXY(start);
            Rectangle visible = editor.getScrollingModel().getVisibleArea();
            point.x = Math.max(visible.x + 8, Math.min(point.x, visible.x + Math.max(8, visible.width - 480)));
            point.y = Math.max(visible.y, Math.min(point.y + editor.getLineHeight(), visible.y + Math.max(0, visible.height - 80)));
            popup.show(new RelativePoint(editor.getContentComponent(), point));
        } catch (RuntimeException error) {
            editor.putUserData(POPUP, null); Disposer.dispose(popup); throw error;
        }
    }
    static void close(Editor editor) {
        JBPopup popup = editor.getUserData(POPUP);
        editor.putUserData(POPUP, null);
        if (popup != null && !popup.isDisposed()) Disposer.dispose(popup);
    }
}
