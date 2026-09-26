package dev.agentbridge;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

/** Short, restartable reads on a background worker; callers must not mutate state in the action. */
final class IdeRead {
    private IdeRead() { }
    static <T> T compute(Project project, Callable<T> action) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Context reads must run on a background thread");
        Thread worker = Thread.currentThread();
        if (worker.isInterrupted() || project.isDisposed()) throw new CancellationException();
        return ReadAction.nonBlocking(action).expireWith(project)
            .expireWhen(worker::isInterrupted).executeSynchronously();
    }
}
