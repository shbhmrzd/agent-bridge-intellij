package dev.agentbridge;

import com.intellij.openapi.editor.impl.DocumentImpl;

/** Exercises real IntelliJ range-marker behavior without a model or an IDE window. */
public final class SelectionContextTest {
    private static int passed;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        passed++; System.out.println("PASS " + label);
    }
    public static void main(String[] args) throws Exception {
        var lifetime = com.intellij.openapi.util.Disposer.newDisposable();
        var application = com.intellij.mock.MockApplication.setUp(lifetime);
        application.getExtensionArea().registerExtensionPoint("com.intellij.documentWriteAccessGuard",
            "com.intellij.openapi.editor.impl.DocumentWriteAccessGuard", com.intellij.openapi.extensions.ExtensionPoint.Kind.INTERFACE, false);
        application.registerService(com.intellij.openapi.fileEditor.FileDocumentManager.class,
            new com.intellij.mock.MockFileDocumentManagerImpl(com.intellij.openapi.util.Key.create("inline-test-document"), DocumentImpl::new));
        var commands = new com.intellij.openapi.command.impl.CoreCommandProcessor();
        application.registerService(com.intellij.openapi.command.CommandProcessor.class, commands);
        application.registerService(com.intellij.openapi.progress.ProgressManager.class, new com.intellij.openapi.progress.impl.CoreProgressManager());
        try { javax.swing.SwingUtilities.invokeAndWait(() -> commands.runUndoTransparentAction(SelectionContextTest::runChecks)); }
        finally { com.intellij.openapi.util.Disposer.dispose(lifetime); }
        // 2024.3's mock application leaves platform executor threads alive.
        // This standalone test process may exit only after assertions and cleanup succeed.
        System.exit(0);
    }
    private static void runChecks() {
        var original = new DocumentImpl("header\nreturn value;\nfooter\n");
        var other = new DocumentImpl("unrelated tab");
        var selected = new SelectionContext(original, 7, 20);
        check(selected.document == original && selected.document != other && selected.text().equals("return value;"), "selection stays bound to its original document");
        check(selected.label().equals("line 2"), "selection line label excludes a following unselected line");
        original.insertString(0, "// note\n");
        check(selected.text().equals("return value;") && selected.label().equals("line 3"), "insertion before selected code moves its anchor without changing its contents");
        original.replaceString(22, 27, "answer");
        check(selected.text().equals("return answer;"), "unsaved edits inside the selected range are captured on follow-up");
        other.setText("a different active editor");
        check(selected.text().equals("return answer;"), "changes in other editors do not replace inline context");
        original.deleteString(15, 29);
        check(selected.text().isEmpty() && selected.label().contains("file context"), "deleted selection falls back explicitly to original-file context");
        selected.dispose();
        check(selected.text().isEmpty(), "disposed selection no longer supplies stale text");
        var lines = new SelectionContext(new DocumentImpl("a\nb\nc\n"), 0, 4);
        check(lines.label().equals("lines 1–2"), "multi-line selection ending at a line boundary reports only selected lines");
        lines.dispose();
        try { new SelectionContext(original, 0, 0); throw new AssertionError("empty selection accepted"); }
        catch (IllegalArgumentException expected) { check(true, "empty selection is rejected"); }
        System.out.println("Passed " + passed + " selection context checks (real IntelliJ documents; no UI or model calls).");
    }
}
