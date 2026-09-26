package dev.agentbridge;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Documentation imagery. Real UI components; synthetic conversations and account state. */
public final class RenderPreviews {
    private static Color background, ink, secondary;
    private static void theme(boolean dark) throws Exception {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        background = new Color(dark ? 0x24262b : 0xf7f8fa);
        ink = new Color(dark ? 0xe1e4eb : 0x252a34);
        secondary = new Color(dark ? 0xa4aebe : 0x536176);
        for (String key : new String[]{"Panel.background", "PopupMenu.background", "MenuItem.background", "Menu.background", "CheckBox.background", "TextArea.background"}) UIManager.put(key, background);
        for (String key : new String[]{"Label.foreground", "TextArea.foreground", "TextPane.foreground", "MenuItem.foreground", "Menu.foreground", "CheckBoxMenuItem.foreground", "RadioButtonMenuItem.foreground"}) UIManager.put(key, ink);
        for (String key : new String[]{"Label.font", "Button.font", "CheckBox.font", "TextArea.font", "TextPane.font", "MenuItem.font", "Menu.font"}) UIManager.put(key, new Font("SansSerif", Font.PLAIN, 14));
        UIManager.put("Component.focusColor", new Color(0x2563eb));
    }
    private static ChatSurface chat(boolean inline, boolean conversation) {
        ChatSurface surface = new ChatSurface();
        surface.provider.setSelectedItem(AgentSession.Provider.Claude);
        for (var option : ModelCatalog.choices(ModelCatalog.defaults(AgentSession.Provider.Claude), "sonnet")) {
            surface.model.addItem(option);
            if (option.id().equals("sonnet")) surface.model.setSelectedItem(option);
        }
        surface.reloadModels.setVisible(false);
        surface.account.setText("Signed in (sample)");
        surface.currentName.setText(inline ? "TaskQueue.java · lines 15–22" : "TaskQueue.java");
        if (inline) surface.inlineMode();
        if (conversation) {
            var question = surface.message("You", "Can an expired lease still acknowledge this task?"); question.render();
            question.context(List.of("src/TaskQueue.java"), inline ? "Selection + file" : "Current file");
            var answer = surface.message("Claude", "Yes. It checks the token but never checks expiry. Reject expired leases before removing the task:\n\n```java\nif (!now.isBefore(lease.expiresAt())) {\n    return false;\n}\n```\nReview the suggested change below.");
            answer.render(); answer.changes(List.of("src/TaskQueue.java"));
            surface.status.setText("Ready · Sample conversation");
        }
        return surface;
    }
    private static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested); }
    private static BufferedImage canvas() {
        BufferedImage image = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics(); prepare(g);
        g.setColor(background); g.fillRect(0, 0, 1280, 800);
        g.setColor(new Color(0x2563eb)); g.fillRoundRect(48, 50, 6, 27, 6, 6);
        g.setColor(ink); g.setFont(new Font("SansSerif", Font.BOLD, 17)); g.drawString("AGENT BRIDGE", 70, 71);
        g.setColor(secondary); g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.drawString("0.9.1  •  UI component preview  •  Sample conversation  •  Not a live IDE capture", 48, 771);
        g.dispose(); return image;
    }
    private static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    private static void text(BufferedImage image, int x, int y, int size, boolean bold, Color color, String... lines) {
        Graphics2D g = image.createGraphics(); prepare(g); g.setColor(color);
        g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        for (String line : lines) { g.drawString(line, x, y); y += size + 12; } g.dispose();
    }
    private static void component(BufferedImage image, JComponent component, int x, int y, int w, int h) {
        component.setSize(w, h); for (int i = 0; i < 7; i++) layout(component);
        Graphics2D g = image.createGraphics(); prepare(g); g.translate(x, y); g.clipRect(0, 0, w, h);
        component.printAll(g); g.dispose();
    }
    private static void sidebar(Path output, boolean dark) throws Exception {
        theme(dark); BufferedImage image = canvas();
        text(image, 48, 166, 38, true, ink, "Ask about your code.", "Review the change.");
        text(image, 48, 295, 20, false, secondary, "Your current file is already in context.", "Add project files when you need them.", "Read the answer, then inspect the diff.");
        text(image, 48, 505, 16, true, ink, "Your existing CLI account");
        text(image, 48, 544, 16, false, secondary, "Claude  /  OpenAI Codex  /  GitHub Copilot", "Provider access and usage limits apply.");
        component(image, chat(false, true), 630, 55, 590, 670);
        ImageIO.write(image, "png", output.resolve(dark ? "01-sidebar-dark.png" : "04-sidebar-light.png").toFile());
    }
    private static void inline(Path output) throws Exception {
        theme(true); BufferedImage image = canvas();
        text(image, 48, 166, 36, true, ink, "Select code.", "Start a conversation.");
        text(image, 48, 293, 19, false, secondary, "A focused composer with the file", "and selected range attached.", "No account toolbar above the chat.");
        text(image, 48, 471, 15, false, secondary, "Opened from Chat About Selection", "in the IntelliJ editor.");
        component(image, chat(true, true), 540, 80, 680, 625);
        ImageIO.write(image, "png", output.resolve("02-selection-chat.png").toFile());
    }
    private static void menus(Path output) throws Exception {
        theme(true); BufferedImage image = canvas();
        text(image, 48, 160, 35, true, ink, "Small controls.", "Clear choices.");
        text(image, 48, 282, 19, false, secondary, "Choose provider and model together.", "Account actions live under the gear.", "A blue row shows the active choice.");
        ChatSurface surface = chat(true, false);
        component(image, surface, 560, 90, 660, 180);
        JPopupMenu models = surface.selectionMenu(), settings = surface.settingsMenu();
        for (Component c : models.getComponents()) if (c instanceof JMenuItem item && item.getText().equals("Sonnet")) item.setArmed(true);
        for (Component c : settings.getComponents()) if (c instanceof JMenuItem item && item.getText().equals("Settings…")) item.setArmed(true);
        text(image, 566, 318, 15, true, ink, "PROVIDER & MODEL");
        text(image, 912, 318, 15, true, ink, "SETTINGS & ACCOUNT");
        component(image, models, 560, 340, 318, models.getPreferredSize().height);
        component(image, settings, 902, 340, 318, settings.getPreferredSize().height);
        text(image, 48, 567, 15, false, secondary, "Menu highlights shown for illustration.", "Account state is sample data.");
        ImageIO.write(image, "png", output.resolve("03-model-and-settings.png").toFile());
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            try { sidebar(output, true); inline(output); menus(output); sidebar(output, false); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        System.out.println("Rendered four labeled component previews to " + output);
    }
}
