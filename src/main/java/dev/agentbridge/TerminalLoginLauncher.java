package dev.agentbridge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.ui.TerminalWidget;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.ShellStartupOptions;
import org.jetbrains.plugins.terminal.TerminalTabState;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loaded only when the bundled Terminal plugin is enabled. */
public final class TerminalLoginLauncher implements LoginLauncher {
    @Override public Handle launch(Project project, String title, List<String> command, Disposable owner, Runnable terminated) {
        var manager = TerminalToolWindowManager.getInstance(project);
        // This runner API exists in both 2024.3 and 2026.1. The newer overload
        // createNewSession(directory, title, command, ...) does not exist in 2024.3.
        // The CLI itself is the terminal process: no shell interpolation or typed commands.
        var runner = new LoginRunner(project, title, command);
        var state = new TerminalTabState();
        state.myWorkingDirectory = System.getProperty("user.home");
        state.myTabName = title;
        manager.createNewSession(runner, state);
        TerminalWidget created = null;
        for (var content : manager.getToolWindow().getContentManager().getContents()) {
            if (TerminalToolWindowManager.getRunnerByContent(content) == runner) {
                created = TerminalToolWindowManager.findWidgetByContent(content); break;
            }
        }
        if (created == null) throw new IllegalStateException("The IDE did not create the login terminal. Sign in through your provider CLI and retry Check login.");
        final TerminalWidget terminal = created;
        AtomicBoolean completed = new AtomicBoolean();
        Runnable finish = () -> { if (completed.compareAndSet(false, true)) terminated.run(); };
        terminal.addTerminationCallback(finish, owner);
        return () -> {
            for (var content : manager.getToolWindow().getContentManager().getContents()) {
                if (TerminalToolWindowManager.findWidgetByContent(content) == terminal) {
                    manager.closeTab(content); break;
                }
            }
            finish.run();
        };
    }

    static final class LoginRunner extends LocalTerminalDirectRunner {
        private final String title;
        private final List<String> command;
        LoginRunner(Project project, String title, List<String> command) {
            super(project); this.title = title; this.command = List.copyOf(command);
            if (command.isEmpty()) throw new IllegalArgumentException("Login command is empty");
        }
        @Override public String getDefaultTabTitle() { return title; }
        @Override protected boolean enableShellIntegration() { return false; }
        @Override public ShellStartupOptions configureStartupOptions(ShellStartupOptions options) {
            // Keep the IDE's PTY/environment setup, but ensure terminal customizers
            // cannot replace the requested CLI with an interactive shell.
            return super.configureStartupOptions(options.builder()
                .workingDirectory(System.getProperty("user.home")).shellCommand(command).build())
                .builder().shellCommand(command).shellIntegration(null).build();
        }
    }
}
