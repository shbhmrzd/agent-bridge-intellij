package dev.agentbridge;

import com.intellij.mock.MockApplication;
import com.intellij.mock.MockProject;
import com.intellij.openapi.application.AsyncExecutionService;
import com.intellij.openapi.application.impl.AsyncExecutionServiceImpl;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.Disposer;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the real non-blocking read implementation; no IDE window or provider calls. */
public final class IdeReadTest {
    private static int passed;
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        passed++; System.out.println("PASS " + label);
    }
    public static void main(String[] args) throws Exception {
        int exitCode = 0;
        var lifetime = Disposer.newDisposable();
        try {
            var application = MockApplication.setUp(lifetime);
            application.registerService(AsyncExecutionService.class, new AsyncExecutionServiceImpl());
            application.registerService(ProgressManager.class, new CoreProgressManager());
            var project = new MockProject(application.getPicoContainer(), lifetime);
            var document = new DocumentImpl("unsaved editor buffer");
            check(IdeRead.compute(project, document::getText).equals("unsaved editor buffer"), "background read captures the editor buffer");
            try { IdeRead.compute(project, () -> { throw new IllegalArgumentException("invalid file"); }); throw new AssertionError("exception swallowed"); }
            catch (IllegalArgumentException expected) { check(expected.getMessage().equals("invalid file"), "read failures reach the context preparation handler"); }
            AtomicBoolean rejectedOnEdt = new AtomicBoolean();
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try { IdeRead.compute(project, document::getText); }
                catch (IllegalStateException expected) { rejectedOnEdt.set(true); }
            });
            check(rejectedOnEdt.get(), "context helper rejects UI-thread file reads");
            AtomicBoolean ran = new AtomicBoolean();
            Thread.currentThread().interrupt();
            try { IdeRead.compute(project, () -> { ran.set(true); return null; }); throw new AssertionError("interrupted read executed"); }
            catch (CancellationException expected) { check(!ran.get(), "Stop interrupts preparation before reading more code"); }
            finally { Thread.interrupted(); }
            Disposer.dispose(project);
            try { IdeRead.compute(project, () -> { ran.set(true); return null; }); throw new AssertionError("disposed project read executed"); }
            catch (CancellationException expected) { check(!ran.get(), "closed project prevents subsequent context reads"); }
            System.out.println("Passed " + passed + " background read checks (real platform read implementation; no provider requests).");
        } catch (Throwable error) { error.printStackTrace(); exitCode = 1; }
        finally { Disposer.dispose(lifetime); }
        System.exit(exitCode);
    }
}
