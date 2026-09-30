package dev.agentbridge;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;

/** Native, width-aware chat layout. Kept independent of IDE services for layout verification. */
final class ChatSurface extends JPanel {
    final JComboBox<AgentSession.Provider> provider = new JComboBox<>(AgentSession.Provider.values());
    final JCheckBox projectContext = new JCheckBox("Find related project files", true);
    final JComboBox<ModelCatalog.Option> model = new JComboBox<>();
    final JButton reloadModels = quiet("↻");
    final JButton settings = quiet("Settings"), fresh = quiet("New chat");
    final JLabel account = new JLabel("Uses your CLI account");
    final JButton signIn = quiet("Sign in"), checkLogin = quiet("Check login");
    final JPanel authHint = new JPanel(new BorderLayout(4, 4));
    final JCheckBox currentFile = new JCheckBox("", true);
    final JLabel currentName = new JLabel("No file open");
    final JButton addFiles = quiet("+ Files");
    final JPanel chips = new JPanel();
    final JTextArea composer = new PromptArea();
    private final JScrollPane input = new JScrollPane(composer);
    final JButton send = primary("Send"), stop = quiet("Stop");
    final JLabel status = new JLabel("Ready");
    final Transcript transcript = new Transcript();
    final JScrollPane scroll = new JScrollPane(transcript);
    final JButton selector = quiet("Claude · Default ▾"), gear = quiet("");
    final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    private final JPanel header = new JPanel();
    private final JPanel submit = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));

    ChatSurface() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
        setMinimumSize(new Dimension(280, 160));
        fresh.setToolTipText("New conversation"); fresh.getAccessibleContext().setAccessibleName("New conversation");
        toolbar.add(fresh); toolbar.add(gear);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        toolbar.setAlignmentX(LEFT_ALIGNMENT); header.add(toolbar);
        authHint.setAlignmentX(LEFT_ALIGNMENT); header.add(authHint); add(header, BorderLayout.NORTH);
        authHint.addContainerListener(new java.awt.event.ContainerAdapter() {
            private void update() {
                header.setVisible(toolbar.isVisible() || authHint.getComponentCount() > 0);
                header.revalidate();
                if (authHint.getComponentCount() > 0 && getClientProperty("agentBridge.conversation") instanceof Runnable grow) grow.run();
            }
            public void componentAdded(java.awt.event.ContainerEvent e) { update(); }
            public void componentRemoved(java.awt.event.ContainerEvent e) { update(); }
        });
        gear.getAccessibleContext().setAccessibleName("Chat settings and account"); gear.setToolTipText("Settings and account");
        gear.putClientProperty("agentBridge.iconButton", true); gear.setIcon(new SettingsIcon());
        gear.addActionListener(e -> showMenu(gear, settingsMenu()));
        selector.setHorizontalAlignment(SwingConstants.LEFT);
        selector.getAccessibleContext().setAccessibleName("Choose provider and model");
        selector.addActionListener(e -> showMenu(selector, selectionMenu()));
        provider.addActionListener(e -> refreshSelector()); model.addActionListener(e -> refreshSelector());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(getBackground()); scroll.setMinimumSize(new Dimension(0, 100));
        scroll.setBorder(BorderFactory.createEmptyBorder()); add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(); bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        JPanel prompt = new RoundedPanel(); prompt.setLayout(new BoxLayout(prompt, BoxLayout.Y_AXIS));
        prompt.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6)); prompt.setAlignmentX(LEFT_ALIGNMENT);
        JPanel active = new JPanel(new BorderLayout(4, 0)); active.setOpaque(false);
        JPanel activeName = new JPanel(new BorderLayout(3, 0)); activeName.setOpaque(false);
        currentFile.setOpaque(false); currentFile.getAccessibleContext().setAccessibleName("Include current editor file");
        currentName.setFont(currentName.getFont().deriveFont(Font.PLAIN, 11f)); currentName.setForeground(muted()); activeName.add(currentFile, BorderLayout.WEST); activeName.add(currentName, BorderLayout.CENTER);
        active.add(activeName, BorderLayout.CENTER); active.add(addFiles, BorderLayout.EAST); active.setAlignmentX(LEFT_ALIGNMENT); prompt.add(active);
        chips.setOpaque(false); chips.setLayout(new BoxLayout(chips, BoxLayout.Y_AXIS));
        JScrollPane attachments = new JScrollPane(chips); attachments.setBorder(BorderFactory.createEmptyBorder());
        attachments.setOpaque(false); attachments.getViewport().setOpaque(false);
        attachments.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        attachments.setAlignmentX(LEFT_ALIGNMENT); attachments.setPreferredSize(new Dimension(0, 0));
        attachments.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84)); prompt.add(attachments);
        chips.addContainerListener(new java.awt.event.ContainerAdapter() {
            private void update() {
                int height = 0; for (Component chip : chips.getComponents()) height += chip.getPreferredSize().height;
                attachments.setPreferredSize(new Dimension(0, Math.min(84, height))); bottom.revalidate();
            }
            public void componentAdded(java.awt.event.ContainerEvent e) { update(); }
            public void componentRemoved(java.awt.event.ContainerEvent e) { update(); }
        });
        composer.setLineWrap(true); composer.setWrapStyleWord(true); composer.setMargin(new Insets(8, 2, 6, 2));
        composer.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 14f)); composer.setForeground(foreground());
        composer.setOpaque(false); composer.getAccessibleContext().setAccessibleName("Ask about your project or describe a change");
        composer.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent e) { prompt.repaint(); }
            public void focusLost(java.awt.event.FocusEvent e) { prompt.repaint(); }
        });
        input.setAlignmentX(LEFT_ALIGNMENT); input.setBorder(BorderFactory.createEmptyBorder());
        input.setOpaque(false); input.getViewport().setOpaque(false); input.setPreferredSize(new Dimension(0, 76)); prompt.add(input);
        JPanel footer = new JPanel(new BorderLayout(4, 0)); footer.setOpaque(false); footer.setAlignmentX(LEFT_ALIGNMENT);
        JPanel selectorSlot = new JPanel(new GridBagLayout()); selectorSlot.setOpaque(false);
        GridBagConstraints selectorLayout = new GridBagConstraints(); selectorLayout.weightx = 1;
        selectorLayout.fill = GridBagConstraints.HORIZONTAL;
        selectorSlot.add(selector, selectorLayout);
        footer.add(selectorSlot, BorderLayout.CENTER); submit.setOpaque(false);
        submit.add(stop); submit.add(send); stop.setVisible(false); footer.add(submit, BorderLayout.EAST);
        prompt.add(footer); bottom.add(prompt);
        status.setAlignmentX(LEFT_ALIGNMENT); status.setForeground(muted()); status.setFont(status.getFont().deriveFont(Font.PLAIN, 11f));
        status.setBorder(BorderFactory.createEmptyBorder(3, 2, 0, 0)); bottom.add(status); add(bottom, BorderLayout.SOUTH);
        provider.setToolTipText("Provider");
        currentFile.setToolTipText("Include current file");
        addFiles.setToolTipText("Add project files or folders");
        send.setToolTipText("Send · Enter (Shift+Enter for a new line)"); welcome();
    }
    void refreshSelector() {
        var selected = (ModelCatalog.Option) model.getSelectedItem();
        String name = selected == null || selected.id().isEmpty() ? "Default" : selected.label();
        selector.setText(provider.getSelectedItem() + " · " + shorten(name, 25) + " ▾");
        selector.setToolTipText(selected == null ? "Provider and model" : selected.label() + " · "
            + (selected.id().isEmpty() ? "Uses CLI configuration" : selected.id()));
        selector.revalidate();
    }
    private static String shorten(String text, int length) { return text.length() <= length ? text : text.substring(0, length - 1) + "…"; }
    private static void showMenu(JButton button, JPopupMenu menu) {
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            private void active(boolean open) { button.putClientProperty("agentBridge.menuOpen", open); button.repaint(); }
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { active(true); }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { active(false); }
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { active(false); }
        });
        menu.show(button, 0, button.getHeight());
    }
    private static <T extends JMenuItem> T menuFont(T item) {
        item.putClientProperty("html.disable", true);
        // Use Swing's menu interaction/keyboard handling, with explicit per-item paint.
        // Native IDE menu delegates can ignore rollover colors supplied by a plugin.
        if (item instanceof JMenu) item.setUI(new javax.swing.plaf.basic.BasicMenuUI() {
            @Override protected void paintBackground(Graphics g, JMenuItem i, Color bg) { selectionForeground = Color.WHITE; paintMenuBackground(g, i); }
        });
        else if (item instanceof JCheckBoxMenuItem) item.setUI(new javax.swing.plaf.basic.BasicCheckBoxMenuItemUI() {
            @Override protected void paintBackground(Graphics g, JMenuItem i, Color bg) { selectionForeground = Color.WHITE; paintMenuBackground(g, i); }
        });
        else if (item instanceof JRadioButtonMenuItem) item.setUI(new javax.swing.plaf.basic.BasicRadioButtonMenuItemUI() {
            @Override protected void paintBackground(Graphics g, JMenuItem i, Color bg) { selectionForeground = Color.WHITE; paintMenuBackground(g, i); }
        });
        else item.setUI(new javax.swing.plaf.basic.BasicMenuItemUI() {
            @Override protected void paintBackground(Graphics g, JMenuItem i, Color bg) { selectionForeground = Color.WHITE; paintMenuBackground(g, i); }
        });
        item.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 12f));
        item.setForeground(foreground());
        item.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return item;
    }
    private static void paintMenuBackground(Graphics graphics, JMenuItem item) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(color("Panel.background", new Color(0x25272b)));
        g.fillRect(0, 0, item.getWidth(), item.getHeight());
        if (item.isEnabled() && (item.isArmed() || item instanceof JMenu && item.isSelected())) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(accent());
            g.fillRoundRect(2, 1, item.getWidth() - 4, item.getHeight() - 2, 7, 7);
        }
        g.dispose();
    }
    /** A crisp 20px vector gear, independent of the platform font's tiny gear glyph. */
    private static final class SettingsIcon implements Icon {
        public int getIconWidth() { return 20; }
        public int getIconHeight() { return 20; }
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x + 10, y + 10);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.getForeground());
            java.awt.geom.Area shape = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(-7, -7, 14, 14));
            for (int tooth = 0; tooth < 8; tooth++) {
                java.awt.Shape tab = new java.awt.geom.RoundRectangle2D.Double(-2, -10, 4, 6, 1, 1);
                shape.add(new java.awt.geom.Area(java.awt.geom.AffineTransform.getRotateInstance(tooth * Math.PI / 4).createTransformedShape(tab)));
            }
            shape.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(-3, -3, 6, 6)));
            g.fill(shape); g.dispose();
        }
    }
    JPopupMenu selectionMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenu providers = menuFont(new JMenu("Provider · " + provider.getSelectedItem()));
        providers.setEnabled(provider.isEnabled());
        for (var value : AgentSession.Provider.values()) {
            String label = value == AgentSession.Provider.Codex ? "OpenAI Codex" : value == AgentSession.Provider.Copilot ? "GitHub Copilot" : "Claude";
            JRadioButtonMenuItem item = menuFont(new JRadioButtonMenuItem(label, value == provider.getSelectedItem()));
            item.addActionListener(e -> { if (value != provider.getSelectedItem()) provider.setSelectedItem(value); }); providers.add(item);
        }
        menu.add(providers); menu.addSeparator();
        JMenuItem label = menuFont(new JMenuItem("Model")); label.setEnabled(false); menu.add(label);
        boolean versionsLabel = false;
        for (int i = 0; i < model.getItemCount(); i++) {
            ModelCatalog.Option option = model.getItemAt(i);
            if (!versionsLabel && provider.getSelectedItem() == AgentSession.Provider.Claude && option.id().startsWith("claude-")) {
                menu.addSeparator();
                JMenuItem versions = menuFont(new JMenuItem("Versions · account access applies"));
                versions.setEnabled(false); menu.add(versions); versionsLabel = true;
            }
            JRadioButtonMenuItem item = menuFont(new JRadioButtonMenuItem(shorten(option.label(), 60), option.equals(model.getSelectedItem())));
            item.setToolTipText(option.equals(ModelCatalog.CUSTOM) ? "Enter an exact model ID from your provider"
                : option.id().isEmpty() ? "Uses your CLI's configured model"
                : option.label().contains("CLI alias") ? option.id() + ": version resolved by your CLI and provider configuration"
                : option.id() + " · Requires CLI and account support");
            item.setEnabled(model.isEnabled()); item.addActionListener(e -> model.setSelectedItem(option)); menu.add(item);
        }
        if (reloadModels.isVisible()) { menu.addSeparator(); menu.add(command("Refresh models", reloadModels)); }
        return menu;
    }
    JPopupMenu settingsMenu() {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem info = menuFont(new JMenuItem(provider.getSelectedItem() + " · " + account.getText())); info.setEnabled(false); menu.add(info);
        menu.add(command("Check login", checkLogin)); menu.add(command(signIn.getText(), signIn)); menu.addSeparator();
        menu.add(command("Settings…", settings));
        JCheckBoxMenuItem context = menuFont(new JCheckBoxMenuItem("Find related project files", projectContext.isSelected()));
        context.setEnabled(projectContext.isEnabled()); context.addActionListener(e -> projectContext.setSelected(context.isSelected()));
        menu.add(context); return menu;
    }
    private static JMenuItem command(String label, JButton action) {
        JMenuItem item = menuFont(new JMenuItem(label)); item.setEnabled(action.isEnabled());
        item.addActionListener(e -> action.doClick()); return item;
    }
    static JButton quiet(String text) { return new RoundButton(text, false); }
    static JButton primary(String text) { return new RoundButton(text, true); }
    static Color foreground() { return color("Label.foreground", new Color(0xdce1e8)); }
    static Color muted() { return blend(color("Panel.background", new Color(0x25272b)), foreground(), .68f); }
    static Color accent() { return color("Component.focusColor", new Color(0x3977e6)); }
    static Color blend(Color from, Color to, float amount) {
        return new Color(Math.round(from.getRed() * (1-amount) + to.getRed() * amount), Math.round(from.getGreen() * (1-amount) + to.getGreen() * amount), Math.round(from.getBlue() * (1-amount) + to.getBlue() * amount));
    }
    private final class RoundedPanel extends JPanel {
        RoundedPanel() { setOpaque(false); }
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = color("Panel.background", new Color(0x25272b));
            g.setColor(blend(base, foreground(), .035f)); g.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 16, 16);
            g.setColor(composer.hasFocus() ? accent() : blend(base, foreground(), .22f)); g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 16, 16); g.dispose();
            super.paintComponent(graphics);
        }
    }
    static final class RoundButton extends JButton {
        private final boolean primary;
        RoundButton(String text, boolean primary) {
            super(text); this.primary = primary; setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
            setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7)); setMargin(new Insets(0, 0, 0, 0)); setFont(getFont().deriveFont(Font.PLAIN, 12f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override public void updateUI() {
            setUI(new javax.swing.plaf.basic.BasicButtonUI());
            setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
            setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
            setRolloverEnabled(true);
            setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN,
                Boolean.TRUE.equals(getClientProperty("agentBridge.iconButton")) ? 16f : 12f));
        }
        @Override public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            if (Boolean.TRUE.equals(getClientProperty("agentBridge.iconButton"))) {
                size.width = Math.max(34, size.width); size.height = Math.max(34, size.height);
            }
            return size;
        }
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = color("Panel.background", new Color(0x25272b));
            boolean pressed = isEnabled() && getModel().isPressed() && getModel().isArmed();
            boolean active = isEnabled() && (getModel().isRollover() || Boolean.TRUE.equals(getClientProperty("agentBridge.menuOpen")));
            Color fill = primary && isEnabled()
                ? pressed ? blend(accent(), Color.BLACK, .20f) : active ? blend(accent(), Color.WHITE, .18f) : accent()
                : pressed ? blend(accent(), Color.BLACK, .20f) : active ? accent() : blend(base, foreground(), .055f);
            g.setColor(fill); g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            if (hasFocus() || active || pressed) { g.setColor(accent()); g.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 10, 10); }
            g.dispose(); setForeground(isEnabled() && (primary || active || pressed) ? Color.WHITE : isEnabled() ? foreground() : muted()); super.paintComponent(graphics);
        }
    }
    private static final class PromptArea extends JTextArea {
        PromptArea() { super(3, 20); }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                g.setColor(muted()); g.setFont(getFont());
                g.drawString("Ask a question or describe a change…", getInsets().left, getInsets().top + g.getFontMetrics().getAscent());
            }
        }
    }
    void welcome() {
        transcript.removeAll();
        scroll.setVisible(false); transcript.revalidate(); transcript.repaint();
    }
    void inlineMode() {
        projectContext.setSelected(false);
        toolbar.setVisible(false); header.setVisible(false);
        toolbar.remove(gear); submit.add(gear, 0);
        composer.setRows(2);
        input.setPreferredSize(new Dimension(0, 66));
        currentFile.setVisible(false);
        currentName.setToolTipText("This conversation stays with the file and selection where you opened it");
        welcome();
    }
    Card message(String author, String text) {
        boolean first = transcript.getComponentCount() == 0;
        scroll.setVisible(true);
        boolean follow = first || followsBottom(); Card card = transcript.addCard(author, text);
        if (first && getClientProperty("agentBridge.conversation") instanceof Runnable grow) grow.run();
        revalidate();
        if (follow) followBottom(); return card;
    }
    boolean followsBottom() { JScrollBar b = scroll.getVerticalScrollBar(); return b.getValue() + b.getVisibleAmount() >= b.getMaximum() - 40; }
    void followBottom() { SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum())); }

    static final class Transcript extends JPanel implements Scrollable {
        Transcript() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }
        Card addCard(String author, String text) {
            Card card = new Card(author, text); add(card);
            while (getComponentCount() > 30) remove(0);
            revalidate(); repaint(); return card;
        }
        public Dimension getPreferredScrollableViewportSize() { return new Dimension(450, 400); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(24, r.height - 24); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
    static final class Card extends JPanel {
        final JTextPane body = new JTextPane();
        final JPanel actions = new JPanel(new BorderLayout(4, 4));
        final StringBuilder source = new StringBuilder();
        private boolean editStream, renderPending;
        private long lastRender;
        private final boolean user;
        final JPanel metadata = new JPanel(new BorderLayout(4, 4));
        Card(String author, String text) {
            super(new BorderLayout(0, 8)); setAlignmentX(LEFT_ALIGNMENT);
            user = author.equals("You") || author.startsWith("You ·");
            setOpaque(false); actions.setOpaque(false); metadata.setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(12, 12, 18, 12));
            JLabel label = new JLabel(author); label.setFont(label.getFont().deriveFont(Font.BOLD, 12f)); label.setForeground(user ? muted() : foreground()); add(label, BorderLayout.NORTH);
            body.setEditable(false); body.setOpaque(false); body.setBorder(null);
            body.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            body.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN)); body.setForeground(UIManager.getColor("Label.foreground"));
            ((DefaultCaret)body.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            body.getAccessibleContext().setAccessibleName(author + " message");
            JPanel message = new JPanel(new BorderLayout(0, 6)); message.setOpaque(false);
            message.add(body, BorderLayout.CENTER); message.add(metadata, BorderLayout.SOUTH);
            add(message, BorderLayout.CENTER); add(actions, BorderLayout.SOUTH); append(text);
        }
        @Override protected void paintComponent(Graphics graphics) {
            if (user) {
                Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(blend(color("Panel.background", new Color(0x25272b)), accent(), .09f));
                g.fillRoundRect(0, 0, getWidth(), getHeight()-7, 14, 14); g.dispose();
            }
            super.paintComponent(graphics);
        }
        void context(java.util.List<String> paths, String scope) {
            JToggleButton toggle = new JToggleButton(paths.size() + " files attached · " + scope);
            toggle.setContentAreaFilled(false); toggle.setBorderPainted(false); toggle.setHorizontalAlignment(SwingConstants.LEFT);
            toggle.setFont(toggle.getFont().deriveFont(Font.PLAIN, 11f)); toggle.setForeground(muted()); toggle.setMargin(new Insets(0, 0, 0, 0));
            JTextArea details = new JTextArea(String.join("\n", paths)); details.setEditable(false); details.setOpaque(false);
            details.setFont(details.getFont().deriveFont(Font.PLAIN, 11f)); details.setForeground(muted()); details.setLineWrap(true);
            JScrollPane area = new JScrollPane(details); area.setBorder(null); area.setOpaque(false); area.getViewport().setOpaque(false);
            area.setPreferredSize(new Dimension(0, Math.min(100, paths.size()*18 + 8))); area.setVisible(false);
            toggle.addActionListener(e -> { area.setVisible(toggle.isSelected()); revalidate(); });
            metadata.add(toggle, BorderLayout.NORTH); metadata.add(area, BorderLayout.CENTER);
        }
        JButton changes(java.util.List<String> paths) {
            JPanel files = new JPanel(); files.setOpaque(false); files.setLayout(new BoxLayout(files, BoxLayout.Y_AXIS));
            JLabel title = new JLabel("Suggested changes · " + paths.size() + (paths.size() == 1 ? " file" : " files"));
            title.setBorder(BorderFactory.createEmptyBorder(8, 0, 6, 0)); files.add(title);
            for (String path : paths) { JLabel label = new JLabel(path); label.putClientProperty("html.disable", true); files.add(label); }
            actions.add(files, BorderLayout.CENTER);
            JButton review = primary("Review changes"), discard = quiet("Discard");
            JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); choices.setOpaque(false); choices.add(review); choices.add(discard);
            actions.add(choices, BorderLayout.SOUTH);
            discard.addActionListener(e -> { actions.removeAll(); actions.add(new JLabel("Suggestions discarded")); revalidate(); repaint(); });
            review.addPropertyChangeListener("enabled", e -> { if (!review.isEnabled()) discard.setEnabled(false); });
            revalidate(); return review;
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override public void setBounds(int x, int y, int width, int height) {
            boolean resized = width != getWidth();
            super.setBounds(x, y, width, height);
            if (resized && body != null) {
                invalidate();
                if (getParent() != null) getParent().invalidate();
            }
        }
        @Override public Dimension getPreferredSize() {
            int width = getParent() == null ? 430 : getParent().getWidth();
            if (width > 40) body.setSize(Math.max(40, width - 24), 1000000);
            Dimension result = super.getPreferredSize(); result.width = Math.max(0, width); return result;
        }
        void append(String text) {
            if (source.length() + text.length() > 256000) throw new IllegalStateException("Response exceeds the 256,000 character display limit");
            source.append(text);
            renderPending = true;
            if (!editStream && source.indexOf("```agent-bridge-edit") >= 0) render();
            else renderPending();
        }
        boolean renderPending() {
            // The panel's existing flush timer coalesces tokens; avoid rebuilding a large document for every token.
            long interval = source.length() > 32000 ? 350_000_000L : 150_000_000L;
            if (!renderPending || System.nanoTime() - lastRender < interval) return false;
            render(); return true;
        }
        void setText(String text) { source.setLength(0); editStream = false; append(text); render(); }
        void render() {
            SimpleAttributeSet normal = new SimpleAttributeSet(); StyleConstants.setFontSize(normal, body.getFont().getSize());
            StyleConstants.setBold(normal, false); StyleConstants.setFontFamily(normal, body.getFont().getFamily()); StyleConstants.setForeground(normal, foreground());
            SimpleAttributeSet code = new SimpleAttributeSet(normal); StyleConstants.setFontFamily(code, Font.MONOSPACED);
            StyleConstants.setBackground(code, blend(color("Panel.background", new Color(0x25272b)), foreground(), .08f));
            SimpleAttributeSet bold = new SimpleAttributeSet(normal); StyleConstants.setBold(bold, true);
            String display = source.toString();
            int editAt = display.indexOf("```agent-bridge-edit");
            if (editAt >= 0) { display = display.substring(0, editAt) + "\nPreparing suggested changes…"; editStream = true; }
            body.setDocument(MarkdownText.render(display, normal, code, bold));
            renderPending = false; lastRender = System.nanoTime(); revalidate(); repaint();
        }
    }
    private static Color color(String key, Color fallback) { Color c = UIManager.getColor(key); return c == null ? fallback : c; }
}
