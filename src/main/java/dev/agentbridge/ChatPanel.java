package dev.agentbridge;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.*;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.requests.SimpleDiffRequest;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.StringSelection;
import java.lang.ref.WeakReference;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

final class ChatPanel implements Disposable {
    private final Project project;
    private final SelectionContext selection;
    private final ChatSurface view = new ChatSurface();
    // At most twelve documents retained while explicitly attached; released on removal/disposal.
    private final Map<VirtualFile, Document> pinned = new LinkedHashMap<>();
    private final Set<VirtualFile> folders = new LinkedHashSet<>();
    private Future<?> contextTask;
    private final ExecutorService worker = executor("agent-bridge-turn");
    private final ExecutorService loader = executor("agent-bridge-context");
    private final ExecutorService authWorker = executor("agent-bridge-auth");
    private final ExecutorService modelWorker = executor("agent-bridge-models");
    private final Map<AgentSession.Provider, LoginLauncher.Handle> logins = new EnumMap<>(AgentSession.Provider.class);
    private Future<?> authTask;
    private long authGeneration;
    private boolean checkingLogin;
    private final Set<CompletableFuture<String>> permissions = ConcurrentHashMap.newKeySet();
    private final AtomicReference<String> pendingStatus = new AtomicReference<>();
    private final StringBuilder pendingText = new StringBuilder();
    private final javax.swing.Timer timer;
    private volatile long epoch;
    private volatile boolean disposed;
    private boolean busy;
    private boolean loading;
    private String executable;
    private String model;
    private final ConversationHistory history = new ConversationHistory();
    private AgentSession.Provider activeProvider;
    private boolean updatingProvider;
    private boolean updatingModels;
    private long modelsGeneration;
    private Future<?> modelsTask;
    private List<ModelCatalog.Option> modelOptions = List.of();
    private String loginMode;
    private CodexAuth.Instructions codexInstructions;
    private AgentSession session;
    private Future<?> task;
    private ChatSurface.Card activeCard;
    private record Snapshot(VirtualFile file, WeakReference<Document> document, long stamp, String text) { }
    private record Prepared(ContextPacket packet, Map<String, Snapshot> snapshots) { }

    ChatPanel(Project project) {
        this(project, null);
    }
    ChatPanel(Project project, SelectionContext selection) {
        this.project = project;
        this.selection = selection;
        if (selection != null) view.inlineMode();
        String remembered = PropertiesComponent.getInstance().getValue("agentBridge.provider", "Claude");
        try { view.provider.setSelectedItem(AgentSession.Provider.valueOf(remembered)); }
        catch (IllegalArgumentException ignored) { view.provider.setSelectedItem(AgentSession.Provider.Claude); }
        loadSettings();
        activeProvider = chosen();
        view.provider.addActionListener(e -> selectProvider());
        view.fresh.addActionListener(e -> { reset(); history.clear(); view.welcome(); view.composer.requestFocusInWindow(); });
        view.settings.addActionListener(e -> settings());
        view.signIn.addActionListener(e -> signIn());
        view.checkLogin.addActionListener(e -> checkLogin());
        view.send.addActionListener(e -> send());
        view.stop.addActionListener(e -> {
            flush();
            if (activeCard != null) { activeCard.append("\n\nStopped. No suggestions from this partial response will be applied."); activeCard.render(); }
            reset(); setStatus("Stopped · Next message starts a new session");
        });
        view.addFiles.addActionListener(e -> addFiles());
        view.currentFile.addActionListener(e -> refreshContext());
        view.putClientProperty("agentBridge.attach", (java.util.function.Consumer<List<VirtualFile>>) this::attachContext);
        view.model.addActionListener(e -> selectModel());
        view.reloadModels.addActionListener(e -> refreshModels());
        for (int mask : new int[]{0, InputEvent.META_DOWN_MASK, InputEvent.CTRL_DOWN_MASK})
            view.composer.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, mask), "send");
        view.composer.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), javax.swing.text.DefaultEditorKit.insertBreakAction);
        view.composer.getActionMap().put("send", new AbstractAction() { public void actionPerformed(ActionEvent e) { send(); } });
        project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                SwingUtilities.invokeLater(() -> { if (!disposed) refreshContext(); });
            }
        });
        if (selection != null) selection.document.addDocumentListener(new com.intellij.openapi.editor.event.DocumentListener() {
            @Override public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
                SwingUtilities.invokeLater(() -> { if (!disposed) refreshContext(); });
            }
        }, this);
        timer = new javax.swing.Timer(50, e -> flush()); timer.start(); refreshContext();
        setStatus((selection == null ? "Related project files on" : "Selection + file included") + " · Enter to send · Shift+Enter for new line");
    }
    private static ExecutorService executor(String name) {
        return Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; });
    }
    JComponent component() { return view; }
    JComponent preferredFocus() { return view.composer; }
    private AgentSession.Provider chosen() { return (AgentSession.Provider)view.provider.getSelectedItem(); }
    private void setStatus(String text) { view.status.setText(text); view.status.setToolTipText(text); }
    private ConversationHistory.Choice switchChoice(String target) {
        if (!history.hasConversation()) return ConversationHistory.Choice.FRESH;
        int result = Messages.showDialog(project,
            "Switch to " + target + "?\n\nContinue with context keeps this chat and sends recent completed exchanges\n"
                + "(including code in messages) to " + target + " with your next question.\n"
                + "Up to 12 exchanges / 48,000 text characters are carried over; long messages are shortened.\n"
                + "Interrupted or failed responses and earlier file snapshots are not carried over.\n\n"
                + "Start fresh clears the conversation. Both choices keep your draft and attached files.",
            "Switch provider or model", new String[]{"Continue with context", "Start fresh", "Cancel"},
            0, Messages.getQuestionIcon());
        return ConversationHistory.choice(result);
    }
    private void switched(ConversationHistory.Choice choice) {
        history.apply(choice);
        if (choice == ConversationHistory.Choice.FRESH) view.welcome();
        setStatus(choice == ConversationHistory.Choice.FRESH ? "Ready · New conversation"
            : "Conversation kept · Recent completed exchanges included with your next question");
        SwingUtilities.invokeLater(() -> { if (!disposed) view.composer.requestFocusInWindow(); });
    }
    private void selectProvider() {
        if (updatingProvider || disposed || chosen() == activeProvider) return;
        AgentSession.Provider next = chosen();
        // Restore the committed selection while the modal dialog runs its nested event loop.
        updatingProvider = true;
        try { view.provider.setSelectedItem(activeProvider); view.refreshSelector(); }
        finally { updatingProvider = false; }
        if (busy || loading) return;
        ConversationHistory.Choice choice = switchChoice(next.name());
        if (choice == ConversationHistory.Choice.CANCEL || disposed) return;
        reset();
        updatingProvider = true;
        try { view.provider.setSelectedItem(next); activeProvider = next; loadSettings(); }
        finally { updatingProvider = false; }
        PropertiesComponent.getInstance().setValue("agentBridge.provider", next.name());
        switched(choice);
    }
    private void loadSettings() {
        String name = chosen().name().toLowerCase(); String path = name;
        for (String dir : List.of(System.getProperty("user.home") + "/.local/bin", "/opt/homebrew/bin", "/usr/local/bin")) {
            Path candidate = Path.of(dir, name); if (Files.isExecutable(candidate)) { path = candidate.toString(); break; }
        }
        executable = PropertiesComponent.getInstance().getValue("agentBridge.executable." + name, path);
        model = PropertiesComponent.getInstance().getValue("agentBridge.model." + name, "");
        loginMode = PropertiesComponent.getInstance().getValue("agentBridge.loginMode." + name, "default");
        configureModels(); resetAccount();
    }
    private void configureModels() {
        modelsGeneration++;
        if (modelsTask != null) modelsTask.cancel(true);
        modelOptions = ModelCatalog.defaults(chosen()); renderModels();
        view.reloadModels.setVisible(chosen() == AgentSession.Provider.Codex);
        view.reloadModels.setEnabled(true);
        if (chosen() == AgentSession.Provider.Codex) refreshModels();
    }
    private void renderModels() {
        updatingModels = true;
        try {
            view.model.removeAllItems();
            for (var option : ModelCatalog.choices(modelOptions, model)) view.model.addItem(option);
            for (int i = 0; i < view.model.getItemCount(); i++)
                if (view.model.getItemAt(i).id().equals(model)) { view.model.setSelectedIndex(i); break; }
            view.model.setToolTipText("Choose model");
            view.refreshSelector();
        } finally { updatingModels = false; }
    }
    private void selectModel() {
        if (updatingModels || busy || loading || disposed) return;
        ModelCatalog.Option option = (ModelCatalog.Option)view.model.getSelectedItem();
        if (option == null) return;
        String next = option.id();
        if (option.equals(ModelCatalog.CUSTOM)) {
            next = Messages.showInputDialog(project, "Enter the model ID supported by " + chosen() + ". Leave blank to use your CLI setting.", "Choose model", null, model, null);
            if (next == null) { renderModels(); return; }
        }
        try { next = ModelCatalog.validate(next); }
        catch (IllegalArgumentException invalid) { setStatus(invalid.getMessage()); renderModels(); return; }
        if (!next.equals(model)) {
            String label = option.equals(ModelCatalog.CUSTOM) ? next : option.label();
            renderModels();
            ConversationHistory.Choice choice = switchChoice(chosen().name() + " · " + label);
            if (choice == ConversationHistory.Choice.CANCEL || disposed) { renderModels(); return; }
            reset(); model = next;
            PropertiesComponent.getInstance().setValue("agentBridge.model." + chosen().name().toLowerCase(), model);
            switched(choice);
        }
        renderModels();
        SwingUtilities.invokeLater(() -> { if (!disposed) view.composer.requestFocusInWindow(); });
    }
    private void refreshModels() {
        if (chosen() != AgentSession.Provider.Codex || disposed) return;
        long generation = ++modelsGeneration;
        if (modelsTask != null) modelsTask.cancel(true);
        String command = executable;
        Path cwd = Path.of(project.getBasePath() == null ? System.getProperty("user.home") : project.getBasePath());
        view.reloadModels.setEnabled(false); view.reloadModels.setToolTipText("Loading models from Codex…");
        modelsTask = modelWorker.submit(() -> {
            try {
                List<ModelCatalog.Option> options = ModelCatalog.codex(command, cwd);
                SwingUtilities.invokeLater(() -> {
                    if (disposed || generation != modelsGeneration) return;
                    modelOptions = options; renderModels(); view.reloadModels.setEnabled(true);
                    view.reloadModels.setToolTipText("Refresh available Codex models");
                });
            } catch (Exception error) {
                SwingUtilities.invokeLater(() -> {
                    if (disposed || generation != modelsGeneration) return;
                    view.reloadModels.setEnabled(true);
                    view.reloadModels.setToolTipText("Could not load models. Check login and retry. Default and Custom model remain available.");
                    view.model.setToolTipText("Could not load Codex models. Use Default, enter a Custom model, or refresh after checking login.");
                    if (!busy && !loading) setStatus("Could not load models · Use ⚙ to check login, then refresh models");
                });
            }
        });
    }
    private void resetAccount() {
        authGeneration++;
        if (authTask != null) authTask.cancel(true);
        checkingLogin = false;
        view.checkLogin.setEnabled(true);
        view.account.setText(logins.containsKey(chosen()) ? "Sign-in in progress" : "CLI login reused");
        view.account.setToolTipText("Check login to inspect the CLI's account status. No model request is made.");
        updateButtons();
        renderAuthHint();
    }
    private void checkLogin() {
        if (disposed || logins.containsKey(chosen())) return;
        long generation = ++authGeneration;
        if (authTask != null) authTask.cancel(true);
        AgentSession.Provider provider = chosen(); String command = executable;
        checkingLogin = true; view.checkLogin.setEnabled(false); view.account.setText("Checking…");
        authTask = authWorker.submit(() -> {
            AccountAuth.Result result = AccountAuth.check(provider, command);
            SwingUtilities.invokeLater(() -> {
                if (disposed || project.isDisposed() || generation != authGeneration) return;
                view.account.setText(result.label()); view.account.setToolTipText(result.detail());
                checkingLogin = false; updateButtons();
                if (result.status() != AccountAuth.Status.SIGNED_IN) setStatus(result.detail());
            });
        });
    }
    private void signIn() {
        if (busy || disposed) return;
        AgentSession.Provider provider = chosen();
        LoginLauncher.Handle existing = logins.get(provider);
        if (existing != null) { existing.cancel(); return; }
        if (provider == AgentSession.Provider.Codex) { signInCodex(); return; }
        try {
            List<String> command = AccountAuth.loginCommand(provider, executable, loginMode.equals("sso"));
            LoginLauncher launcher = ApplicationManager.getApplication().getService(LoginLauncher.class);
            if (launcher == null) {
                Messages.showInfoMessage(project, "Enable the bundled Terminal plugin in Settings → Plugins, then restart IntelliJ.\nSign in uses the installed CLI's browser flow. Your existing login still works without Terminal.", "Enable Terminal for sign-in");
                return;
            }
            // Restart the agent runtime after a possible account change; keep visible chat/context.
            reset(); resetAccount(); String launchedExecutable = executable;
            LoginLauncher.Handle[] launched = new LoginLauncher.Handle[1];
            LoginLauncher.Handle handle = launcher.launch(project, provider + " sign-in", command, this, () ->
                SwingUtilities.invokeLater(() -> {
                    if (disposed || project.isDisposed()) return;
                    if (launched[0] == null || logins.get(provider) != launched[0]) return;
                    logins.remove(provider);
                    updateButtons();
                    if (chosen() == provider && executable.equals(launchedExecutable)) {
                        // Termination is not proof of successful authentication (it may be cancelled).
                        checkLogin();
                        setStatus("Sign-in process ended · Checking CLI login; see its terminal for details");
                    }
                }));
            launched[0] = handle;
            logins.put(provider, handle);
            updateButtons();
            view.account.setText("Sign-in in progress");
            view.account.setToolTipText("Complete the browser flow. If a code is requested, enter it in the sign-in terminal. Ctrl+C there cancels login.");
            setStatus("Complete " + provider + " sign-in in your browser; the CLI terminal shows any remaining steps");
        } catch (RuntimeException | LinkageError error) {
            view.account.setText("Sign-in not started");
            setStatus("Could not open sign-in. Check the executable in Settings and that Terminal is enabled.");
        }
    }
    private void signInCodex() {
        reset(); resetAccount();
        String launchedExecutable = executable;
        CodexAuth login;
        try { login = new CodexAuth(executable); }
        catch (IllegalArgumentException invalid) { setStatus(invalid.getMessage()); return; }
        logins.put(AgentSession.Provider.Codex, login); updateButtons();
        view.account.setText("Starting sign-in…");
        setStatus("Connecting to Codex for ChatGPT sign-in…");
        login.start(loginMode.equals("device"), instructions -> SwingUtilities.invokeLater(() -> {
            if (disposed || project.isDisposed() || logins.get(AgentSession.Provider.Codex) != login || !login.isActive()) return;
            codexInstructions = instructions;
            if (chosen() == AgentSession.Provider.Codex) {
                view.account.setText("Awaiting sign-in"); renderAuthHint();
                openCodexBrowser(login, instructions);
            }
        }), outcome -> SwingUtilities.invokeLater(() -> {
            if (disposed || project.isDisposed() || logins.get(AgentSession.Provider.Codex) != login) return;
            logins.remove(AgentSession.Provider.Codex); codexInstructions = null;
            renderAuthHint(); updateButtons();
            if (chosen() == AgentSession.Provider.Codex && executable.equals(launchedExecutable)) {
                if (outcome.account() != null) {
                    view.account.setText(outcome.account().label()); view.account.setToolTipText(outcome.account().detail());
                } else {
                    view.account.setText(outcome.cancelled() ? "Sign-in cancelled" : "Sign-in failed");
                    view.account.setToolTipText(outcome.message());
                }
                setStatus(outcome.message());
            }
        }));
    }
    private void openCodexBrowser(CodexAuth login, CodexAuth.Instructions instructions) {
        if (!login.isActive()) return;
        try { BrowserUtil.browse(instructions.url().toASCIIString()); }
        catch (RuntimeException failure) { setStatus("Browser could not open. Retry Open browser, or cancel and choose Device code in Settings."); }
    }
    private void renderAuthHint() {
        view.authHint.removeAll();
        if (chosen() == AgentSession.Provider.Codex && codexInstructions != null
            && logins.get(AgentSession.Provider.Codex) instanceof CodexAuth login && login.isActive()) {
            CodexAuth.Instructions instructions = codexInstructions;
            String message = instructions.userCode().isEmpty() ? "Complete ChatGPT sign-in in your browser."
                : "Enter this code on OpenAI's sign-in page: " + instructions.userCode();
            JTextArea note = new JTextArea(message); note.setEditable(false); note.setOpaque(false);
            note.setFont(view.account.getFont()); note.setForeground(view.account.getForeground());
            note.setLineWrap(true); note.setWrapStyleWord(true); note.setRows(2);
            view.authHint.add(note, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            JButton browser = new JButton("Open browser"); browser.addActionListener(e -> openCodexBrowser(login, instructions)); buttons.add(browser);
            if (!instructions.userCode().isEmpty()) {
                JButton copy = new JButton("Copy code");
                copy.addActionListener(e -> { if (login.isActive()) CopyPasteManager.getInstance().setContents(new StringSelection(instructions.userCode())); });
                buttons.add(copy);
            }
            view.authHint.add(buttons, BorderLayout.SOUTH);
        }
        view.authHint.revalidate(); view.authHint.repaint();
    }
    private void settings() {
        JTextField command = new JTextField(executable, 36);
        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 6));
        fields.add(new JLabel(chosen() + " executable")); fields.add(command);
        JComboBox<String> loginMethod = new JComboBox<>(chosen() == AgentSession.Provider.Codex
            ? new String[]{"ChatGPT — browser", "ChatGPT — device code"}
            : new String[]{"Claude account", "Claude account — organization SSO"});
        loginMethod.setSelectedIndex(loginMode.equals("device") || loginMode.equals("sso") ? 1 : 0);
        if (chosen() != AgentSession.Provider.Copilot) { fields.add(new JLabel("Sign-in method")); fields.add(loginMethod); }
        JButton login = new JButton("Save and sign in…"); fields.add(login);
        class SettingsDialog extends DialogWrapper {
            boolean startLogin;
            SettingsDialog() {
                super(project); setTitle("Agent Bridge settings"); setOKButtonText("Save"); init();
                login.addActionListener(e -> { startLogin = true; doOKAction(); });
            }
            @Override protected JComponent createCenterPanel() { return fields; }
            @Override protected void doOKAction() {
                try { AccountAuth.executable(command.getText()); }
                catch (IllegalArgumentException invalid) { setErrorText(invalid.getMessage()); startLogin = false; return; }
                super.doOKAction();
            }
        }
        SettingsDialog dialog = new SettingsDialog();
        if (dialog.showAndGet()) {
            if (command.getText().isBlank()) { setStatus("Executable cannot be empty."); return; }
            reset(); executable = command.getText().trim();
            loginMode = loginMethod.getSelectedIndex() == 0 ? "default" : chosen() == AgentSession.Provider.Codex ? "device" : "sso";
            String name = chosen().name().toLowerCase();
            PropertiesComponent.getInstance().setValue("agentBridge.executable." + name, executable);
            PropertiesComponent.getInstance().setValue("agentBridge.model." + name, model);
            PropertiesComponent.getInstance().setValue("agentBridge.loginMode." + name, loginMode);
            configureModels(); resetAccount();
            view.message("Session", "Settings saved. Your next message starts a new agent session.").render();
            if (dialog.startLogin) signIn();
        }
    }
    private String relative(VirtualFile file) {
        if (project.getBasePath() == null) throw new IllegalArgumentException("Open a project folder first.");
        Path root = Path.of(project.getBasePath()).toAbsolutePath().normalize(), path = Path.of(file.getPath()).toAbsolutePath().normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Choose a file inside this project: " + file.getName());
        return root.relativize(path).toString().replace('\\', '/');
    }
    private void checkFile(VirtualFile file) {
        if (!file.isValid() || file.isDirectory() || !file.isInLocalFileSystem() || file.getFileType().isBinary())
            throw new IllegalArgumentException("Only local text files can be attached.");
        relative(file);
        checkCanonical(file);
        if (file.getLength() > 256000) throw new IllegalArgumentException(file.getName() + " is too large to attach.");
    }
    private void checkCanonical(VirtualFile file) {
        String canonical = file.getCanonicalPath();
        String base = Objects.requireNonNull(project.getBasePath());
        VirtualFile root = LocalFileSystem.getInstance().findFileByPath(base);
        String canonicalRoot = root == null ? base : Optional.ofNullable(root.getCanonicalPath()).orElse(base);
        if (canonical == null || !Path.of(canonical).normalize().startsWith(Path.of(canonicalRoot).normalize()))
            throw new IllegalArgumentException("Choose a file or folder inside this project.");
    }
    private VirtualFile activeFile() {
        if (selection != null) return FileDocumentManager.getInstance().getFile(selection.document);
        var editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        return editor == null ? null : FileDocumentManager.getInstance().getFile(editor.getDocument());
    }
    private void refreshContext() {
        VirtualFile file = activeFile();
        String name = !view.currentFile.isSelected() ? "Not included" : file == null ? "No text file open" : file.getName();
        if (selection != null) name += " · " + selection.label();
        view.currentName.setText(name); view.currentName.setToolTipText(file == null ? name : file.getPath());
        view.chips.removeAll();
        for (VirtualFile attached : java.util.stream.Stream.concat(pinned.keySet().stream(), folders.stream()).toList()) {
            JPanel chip = new JPanel(new BorderLayout(4, 0)); chip.setOpaque(false);
            String path;
            try { path = relative(attached); } catch (IllegalArgumentException moved) { path = attached.getName() + " (outside project)"; }
            if (path.isEmpty()) path = project.getName();
            JLabel label = new JLabel(path + (attached.isDirectory() ? "/" : "")); label.setForeground(ChatSurface.muted()); label.setToolTipText(attached.getPath()); chip.add(label, BorderLayout.CENTER);
            JButton remove = ChatSurface.quiet("×"); remove.setToolTipText("Remove " + attached.getName() + " from context");
            remove.getAccessibleContext().setAccessibleName("Remove " + attached.getName() + " from context");
            remove.setPreferredSize(new Dimension(remove.getFontMetrics(remove.getFont()).charWidth('×') + 20, Math.max(26, label.getPreferredSize().height + 6)));
            remove.addActionListener(e -> { pinned.remove(attached); folders.remove(attached); refreshContext(); }); chip.add(remove, BorderLayout.EAST);
            chip.setAlignmentX(Component.LEFT_ALIGNMENT);
            chip.setMaximumSize(chip.getPreferredSize()); view.chips.add(chip);
        }
        view.chips.revalidate(); view.chips.repaint();
    }
    private void addFiles() {
        if (loading) return;
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true)
            .withTitle("Add project context").withDescription("Choose files or folders from the open project. Folders provide relevant files when you send.")
            .withShowFileSystemRoots(false).withTreeRootVisible(true).withHideIgnored(true).withShowHiddenFiles(false);
        descriptor.setForcedToUseIdeaFileChooser(true);
        VirtualFile root = project.getBasePath() == null ? null : LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
        if (root == null) { setStatus("Open a project folder first."); return; }
        descriptor.withRoots(root);
        VirtualFile[] files = FileChooser.chooseFiles(descriptor, project, root);
        attachContext(Arrays.asList(files));
    }
    private void attachContext(List<VirtualFile> files) {
        if (loading || disposed || files.isEmpty()) return;
        if (new HashSet<>(java.util.stream.Stream.concat(pinned.keySet().stream(), files.stream().filter(f -> !f.isDirectory())).toList()).size() > 12
            || new HashSet<>(java.util.stream.Stream.concat(folders.stream(), files.stream().filter(VirtualFile::isDirectory)).toList()).size() > 12) {
            setStatus("Choose up to 12 files and 12 folders, or enable Find related project files."); return;
        }
        long generation = epoch; loading = true; updateButtons(); setStatus("Loading file context…");
        contextTask = loader.submit(() -> {
            try {
                Map<VirtualFile, Document> loaded = new LinkedHashMap<>();
                List<VirtualFile> selectedFolders = new ArrayList<>();
                for (VirtualFile f : files) {
                    if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                    if (f.isDirectory()) {
                        IdeRead.compute(project, () -> { relative(f); checkCanonical(f); if (!f.isValid()) throw new IllegalArgumentException("Folder is no longer available."); return null; });
                        selectedFolders.add(f); continue;
                    }
                    Document doc = IdeRead.compute(project, () -> {
                        checkFile(f); Document d = FileDocumentManager.getInstance().getDocument(f);
                        if (d == null || d.getTextLength() > ContextPacket.FILE_LIMIT) throw new IllegalArgumentException(f.getName() + " is not a supported text file or exceeds 64,000 characters.");
                        return d;
                    });
                    loaded.put(f, doc);
                }
                ui(generation, () -> { loading = false; pinned.putAll(loaded); folders.addAll(selectedFolders); refreshContext(); updateButtons(); setStatus("Files added · Ask your question below"); view.composer.requestFocusInWindow(); });
            } catch (Exception error) {
                ui(generation, () -> { loading = false; updateButtons(); setStatus(error.getMessage()); });
            }
        });
    }
    private Prepared capture(com.intellij.openapi.editor.Editor editor, boolean includeCurrent,
                             Map<VirtualFile, Document> attachments) {
        return IdeRead.compute(project, () -> {
            Map<String, Snapshot> snapshots = new LinkedHashMap<>();
            List<ContextPacket.File> files = new ArrayList<>();
            String active = "", selected = "";
            if (selection != null) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(selection.document);
                if (file == null || !file.isValid()) throw new IllegalArgumentException("The selected file is no longer available. Reopen inline chat from a source file.");
                checkFile(file); active = relative(file); snapshot(file, selection.document, snapshots);
                selected = selection.text();
            } else if (includeCurrent && editor != null && !editor.isDisposed()) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
                if (file != null) {
                    checkFile(file); active = relative(file); snapshot(file, editor.getDocument(), snapshots);
                    selected = Optional.ofNullable(editor.getSelectionModel().getSelectedText()).orElse("");
                }
            }
            for (var entry : attachments.entrySet()) { checkFile(entry.getKey()); snapshot(entry.getKey(), entry.getValue(), snapshots); }
            snapshots.forEach((path, value) -> files.add(new ContextPacket.File(path, value.text())));
            return new Prepared(new ContextPacket(files, active, selected), Map.copyOf(snapshots));
        });
    }
    private void snapshot(VirtualFile file, Document document, Map<String, Snapshot> result) {
        if (document.getTextLength() > ContextPacket.FILE_LIMIT) throw new IllegalArgumentException(file.getName() + " exceeds 64,000 characters. "
            + (selection == null ? "Turn off Current file or remove it." : "Open selection chat in a smaller file."));
        result.put(relative(file), new Snapshot(file, new WeakReference<>(document), document.getModificationStamp(), document.getText()));
    }
    private void send() {
        if (busy || loading || disposed || logins.containsKey(chosen()) || view.composer.getText().isBlank()) return;
        if (project.getBasePath() == null) { setStatus("Open a project folder first."); return; }
        String text = view.composer.getText().trim();
        if (text.length() > 16000) { setStatus("Message exceeds 16,000 characters."); return; }
        refreshContext();
        // Capture UI choices on EDT. Document/VFS reads run on the context worker.
        var editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        boolean includeCurrent = view.currentFile.isSelected();
        Map<VirtualFile, Document> attachments = Collections.unmodifiableMap(new LinkedHashMap<>(pinned));
        List<VirtualFile> scopes = new ArrayList<>(folders);
        boolean wholeProject = view.projectContext.isSelected();
        long generation = epoch; loading = true; updateButtons(); setStatus("Preparing file context…");
        contextTask = loader.submit(() -> {
            try {
                Prepared prepared = capture(editor, includeCurrent, attachments);
                if (wholeProject) {
                    VirtualFile root = IdeRead.compute(project, () -> LocalFileSystem.getInstance().findFileByPath(project.getBasePath()));
                    if (root != null) scopes.add(root);
                }
                if (scopes.isEmpty()) {
                    ui(generation, () -> { loading = false; dispatch(text, prepared, "Chosen files"); }); return;
                }
                ProjectContextResolver.Result result = ProjectContextResolver.resolve(project, scopes, text, prepared.packet());
                Map<String, Snapshot> snapshots = new LinkedHashMap<>(prepared.snapshots());
                List<ContextPacket.File> files = new ArrayList<>(prepared.packet().files());
                for (var buffer : result.files()) {
                    snapshots.put(buffer.path(), new Snapshot(buffer.file(), new WeakReference<>(buffer.document()), buffer.stamp(), buffer.text()));
                    files.add(new ContextPacket.File(buffer.path(), buffer.text()));
                }
                ContextPacket packet = new ContextPacket(files, prepared.packet().activePath(), prepared.packet().selectedText(), result.inventory());
                Prepared expanded = new Prepared(packet, Map.copyOf(snapshots));
                ui(generation, () -> { loading = false; dispatch(text, expanded, (wholeProject ? "Related project files" : "Chosen folders") + (result.limited() ? " · partial discovery" : "")); });
            } catch (Exception error) {
                ui(generation, () -> { loading = false; updateButtons(); setStatus("Could not prepare context: " + error.getMessage()); });
            }
        });
    }
    private void dispatch(String text, Prepared prepared, String scopeLabel) {
        String contextLabel = prepared.packet().files().size() + " files attached";
        ConversationHistory.Snapshot handoff = history.forSession(session == null);
        String prompt = prepared.packet().prompt(text, false, handoff);
        String turnProvider = chosen().name(), turnModel = model;
        history.sent();
        ChatSurface.Card question = view.message("You", text); question.render();
        question.context(prepared.packet().files().stream().map(ContextPacket.File::path).toList(), scopeLabel);
        view.composer.setText(""); activeCard = view.message(chosen().name(), "");
        ChatSurface.Card card = activeCard;
        long generation = epoch;
        if (session == null) session = new AgentSession(chosen(), executable, Path.of(project.getBasePath()), model, false, listener(generation));
        AgentSession current = session;
        busy = true; updateButtons(); setStatus("Connecting to " + chosen() + "…");
        task = worker.submit(() -> {
            try {
                current.prompt(prompt);
                ui(generation, () -> {
                    flush(); if (generation != epoch) return;
                    history.completed(turnProvider, turnModel, text, card.source.toString());
                    busy = false; activeCard = null; updateButtons(); finish(card, prepared); setStatus("Done · " + contextLabel);
                });
            } catch (Exception error) {
                current.close();
                ui(generation, () -> {
                    flush(); if (generation != epoch) return;
                    session = null; busy = false; activeCard = null; updateButtons();
                    Throwable cause = error; while (cause.getCause() != null) cause = cause.getCause();
                    card.append("\n\nRequest failed: " + Optional.ofNullable(cause.getMessage()).orElse(cause.getClass().getSimpleName())); card.render();
                    setStatus("Request failed · Open ⚙ to check login or sign in");
                });
            }
        });
    }
    private void finish(ChatSurface.Card card, Prepared prepared) {
        try {
            EditProposal.Result result = EditProposal.parse(card.source.toString(), prepared.packet().buffers());
            if (result.changes().isEmpty()) { card.render(); return; }
            card.setText(result.explanation().isBlank() ? "Suggested changes are ready for review." : result.explanation());
            JButton review = card.changes(result.changes().stream().map(EditProposal.Change::path).toList());
            review.addActionListener(e -> review(result.changes(), prepared.snapshots(), review)); card.revalidate();
        } catch (IllegalArgumentException invalid) {
            String text = card.source.toString(); int block = text.indexOf("```agent-bridge-edit");
            if (block >= 0) card.setText(text.substring(0, block) + "\n\nThe suggested changes could not be prepared for review.");
            else card.render();
            view.message("Edit review", invalid.getMessage() + "\n\nAsk the agent to regenerate the suggestion against the attached files.").render();
        }
    }
    private void review(List<EditProposal.Change> changes, Map<String, Snapshot> snapshots, JButton button) {
        if (busy || loading) { setStatus("Wait for the current response before applying changes."); return; }
        class ReviewDialog extends DialogWrapper {
            ReviewDialog() { super(project); setTitle("Review suggested changes"); setOKButtonText(changes.size() == 1 ? "Apply change" : "Apply all changes"); init(); }
            @Override protected JComponent createCenterPanel() {
                JPanel content = new JPanel(new BorderLayout(6, 6)); content.setPreferredSize(new Dimension(920, 600));
                JComboBox<String> files = new JComboBox<>(changes.stream().map(EditProposal.Change::path).toArray(String[]::new));
                content.add(files, BorderLayout.NORTH);
                DiffRequestPanel diff = DiffManager.getInstance().createRequestPanel(project, getDisposable(), null);
                content.add(diff.getComponent(), BorderLayout.CENTER);
                Runnable select = () -> {
                    EditProposal.Change c = changes.get(files.getSelectedIndex()); var f = DiffContentFactory.getInstance();
                    var type = snapshots.get(c.path()).file().getFileType();
                    diff.setRequest(new SimpleDiffRequest(c.path(), f.create(project, c.before(), type), f.create(project, c.after(), type), "Buffer sent to agent", "Suggested changes"));
                };
                files.addActionListener(e -> select.run()); select.run();
                content.add(new JLabel("Applies to editor buffers with Undo. Files changed since the request will be rejected."), BorderLayout.SOUTH);
                return content;
            }
            @Override protected void doOKAction() {
                try {
                    List<Document> docs = new ArrayList<>();
                    for (EditProposal.Change c : changes) {
                        Snapshot s = snapshots.get(c.path()); Document d = s.document().get();
                        if (d == null) d = FileDocumentManager.getInstance().getCachedDocument(s.file());
                        if (!s.file().isValid() || d == null || !s.file().isWritable() || !d.isWritable())
                            throw new IllegalArgumentException(c.path() + " is no longer open/writable. Reopen it and request a fresh suggestion.");
                        if (!EditGuard.sameBuffer(s.stamp(), d.getModificationStamp(), c.path(), relative(s.file()), c.before(), d.getText()))
                            throw new IllegalArgumentException(c.path() + " changed since you sent the request. Ask for a fresh suggestion; your edits were preserved.");
                        if (d.getRangeGuard(0, d.getTextLength()) != null) throw new IllegalArgumentException(c.path() + " contains guarded regions and cannot be replaced as a whole file.");
                        docs.add(d);
                    }
                    // All validation and writes occur on the EDT, in one undoable command.
                    WriteCommandAction.runWriteCommandAction(project, "Apply Agent Bridge suggestions", null, () -> {
                        for (int i = 0; i < changes.size(); i++) docs.get(i).setText(changes.get(i).after());
                    });
                    button.setText("Applied · Undo available"); button.setEnabled(false);
                    setStatus("Changes applied to editor buffers · Save when ready"); super.doOKAction();
                } catch (IllegalArgumentException invalid) { setErrorText(invalid.getMessage()); }
            }
        }
        new ReviewDialog().show();
    }
    private AgentSession.Listener listener(long generation) {
        return new AgentSession.Listener() {
            public void text(String text) {
                synchronized (pendingText) {
                    if (generation != epoch || disposed) return;
                    if (pendingText.length() + text.length() > 256000) throw new IllegalStateException("Output buffer exceeded its limit");
                    pendingText.append(text);
                }
            }
            public void status(String text) { if (generation == epoch && !disposed) pendingStatus.set(text); }
            public CompletableFuture<String> permission(String title, String detail, List<AgentSession.Choice> choices) {
                // Chat never authorizes agent-side writes. Suggestions are applied by the IDE.
                return CompletableFuture.completedFuture(null);
            }
        };
    }
    private void flush() {
        String text; synchronized (pendingText) { text = pendingText.toString(); pendingText.setLength(0); }
        if (!text.isEmpty() && activeCard != null) {
            boolean follow = view.followsBottom();
            try { activeCard.append(text); }
            catch (IllegalStateException tooLarge) { reset(); setStatus(tooLarge.getMessage()); return; }
            if (follow) view.followBottom();
        }
        String s = pendingStatus.getAndSet(null); if (s != null) setStatus(s);
    }
    private void updateButtons() {
        boolean canSend = !busy && !loading && !logins.containsKey(chosen());
        view.send.setEnabled(canSend); view.send.setVisible(!busy && !loading); view.stop.setEnabled(busy || loading); view.stop.setVisible(busy || loading); view.provider.setEnabled(!busy && !loading);
        view.projectContext.setEnabled(!busy && !loading); view.model.setEnabled(!busy && !loading); view.composer.setEnabled(!loading);
        view.settings.setEnabled(!busy && !loading && !logins.containsKey(chosen()));
        view.addFiles.setEnabled(!loading);
        view.signIn.setEnabled(!busy);
        view.checkLogin.setEnabled(!checkingLogin && !logins.containsKey(chosen()));
        view.signIn.setText(logins.containsKey(chosen()) ? "Cancel" : "Sign in");
        view.signIn.setToolTipText(logins.containsKey(chosen())
            ? chosen() == AgentSession.Provider.Codex ? "Cancel the pending Codex-managed sign-in; saved credentials are not removed" : "Cancel this sign-in process and close its terminal tab; your saved login is not removed"
            : chosen() == AgentSession.Provider.Codex ? "Sign in with ChatGPT using Codex-managed OAuth" : "Start the installed CLI's browser login");
    }
    private void reset() {
        epoch++; permissions.forEach(p -> p.complete(null)); permissions.clear();
        if (session != null) { AgentSession old = session; session = null; old.close(); }
        if (task != null) task.cancel(true);
        if (contextTask != null) contextTask.cancel(true);
        synchronized (pendingText) { pendingText.setLength(0); }
        pendingStatus.set(null); activeCard = null; busy = false; loading = false; updateButtons(); setStatus("Ready · New session");
    }
    private void ui(long generation, Runnable action) {
        SwingUtilities.invokeLater(() -> { if (!disposed && epoch == generation && !project.isDisposed()) action.run(); });
    }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true; modelsGeneration++;
        if (modelsTask != null) modelsTask.cancel(true);
        reset(); history.clear(); pinned.clear(); folders.clear();
        if (selection != null) selection.dispose();
        for (LoginLauncher.Handle login : List.copyOf(logins.values())) {
            try { login.cancel(); } catch (RuntimeException ignored) { }
        }
        logins.clear(); codexInstructions = null; timer.stop();
        if (authTask != null) authTask.cancel(true);
        worker.shutdownNow(); loader.shutdownNow(); authWorker.shutdownNow(); modelWorker.shutdownNow();
    }
}
