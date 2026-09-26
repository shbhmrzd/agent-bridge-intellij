package dev.agentbridge;

import java.util.*;
import java.util.List;
import java.nio.file.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class ContextTest {
    private static int passed;
    static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); passed++; System.out.println("PASS " + name); }
    static String edits(Object... items) { return "Suggested fix.\n```agent-bridge-edit\n" + Json.obj("edits", Json.arr(items)) + "\n```\n"; }
    static com.google.gson.JsonObject edit(String path, String old, String replacement) { return Json.obj("path", path, "oldText", old, "newText", replacement); }
    static void rejected(Runnable run, String name) {
        try { run.run(); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalArgumentException expected) { check(true, name); }
    }
    public static void main(String[] args) throws Exception {
        var files = List.of(new ContextPacket.File("Task.java", "class Task { int retries = 0; }"),
            new ContextPacket.File("Test.java", "assert retries == 0;"));
        var packet = new ContextPacket(files, "Task.java", "retries = 0");
        String prompt = packet.prompt("Fix retries", true);
        check(prompt.contains("class Task") && prompt.contains("assert retries") && prompt.contains("selectedText"), "current file, additional files and selection sent together");
        check(packet.buffers().get("Task.java").contains("retries = 0"), "unsaved snapshot preserved without reading disk");
        check(new ContextPacket(List.of(files.get(0), files.get(0)), "Task.java", "").files().size() == 1, "current/pinned duplicate deduplicated");
        rejected(() -> new ContextPacket(List.of(new ContextPacket.File("big", "x".repeat(64001))), "", ""), "large file refused explicitly");
        rejected(() -> new ContextPacket(List.of(new ContextPacket.File("a", "x".repeat(64000)), new ContextPacket.File("b", "x".repeat(64000)), new ContextPacket.File("c", "x".repeat(64000))), "", ""), "aggregate context budget enforced");
        String response = edits(edit("Task.java", "retries = 0", "retries = 3"), edit("Test.java", "retries == 0", "retries == 3"));
        var parsed = EditProposal.parse(response, packet.buffers());
        check(parsed.changes().size() == 2 && parsed.changes().get(0).after().contains("retries = 3"), "multi-file suggestions resolve to exact snapshots");
        check(parsed.explanation().equals("Suggested fix."), "machine edit payload removed from displayed explanation");
        rejected(() -> EditProposal.parse(edits(edit("../outside", "x", "y")), packet.buffers()), "unattached path and traversal rejected");
        rejected(() -> EditProposal.parse(edits(edit("a", "same", "new")), Map.of("a", "same same")), "ambiguous duplicate snippet rejected");
        rejected(() -> EditProposal.parse(edits(edit("a", "missing", "new")), Map.of("a", "existing")), "stale or hallucinated snippet rejected");
        rejected(() -> EditProposal.parse(edits(edit("a", "abc", "x"), edit("a", "bcd", "y")), Map.of("a", "abcdef")), "overlapping edits rejected");
        rejected(() -> EditProposal.parse(edits(edit("a", "", "x")), Map.of("a", "abc")), "empty anchors rejected");
        rejected(() -> EditProposal.parse("```agent-bridge-edit\nnot json\n```", Map.of()), "malformed edit JSON rejected");
        rejected(() -> EditProposal.parse(response + "```agent-bridge-edit\n{", packet.buffers()), "partial trailing edit rejects the entire proposal");
        check(EditProposal.parse(edits(edit("a", "ab", "long"), edit("a", "ef", "z")), Map.of("a", "abcdef")).changes().get(0).after().equals("longcdz"), "multiple edits preserve original coordinates");
        check(EditProposal.parse("Example:\n```java\nreturn 1;\n```", Map.of()).changes().isEmpty(), "ordinary example code is never an applicable edit");
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                for (boolean dark : new boolean[]{true, false}) {
                    Color background = new Color(dark ? 0x24262b : 0xf7f8fa), foreground = new Color(dark ? 0xe1e4eb : 0x252a34);
                    Color surfaceColor = new Color(dark ? 0x2d3037 : 0xffffff);
                    UIManager.put("Panel.background", background); UIManager.put("Label.foreground", foreground);
                    UIManager.put("TextArea.background", surfaceColor); UIManager.put("TextArea.foreground", foreground);
                    UIManager.put("TextPane.foreground", foreground); UIManager.put("CheckBox.background", surfaceColor);
                    UIManager.put("ComboBox.background", surfaceColor); UIManager.put("ComboBox.foreground", foreground);
                    UIManager.put("ComboBox.selectionBackground", surfaceColor); UIManager.put("ComboBox.selectionForeground", foreground);
                    UIManager.put("Component.focusColor", new Color(0x2563eb));
                    for (String key : new String[]{"Label.font", "Button.font", "ComboBox.font", "CheckBox.font", "TextArea.font", "TextPane.font"})
                        UIManager.put(key, new Font("SansSerif", Font.PLAIN, 13));
                    ChatSurface hoverSurface = new ChatSurface();
                    hoverSurface.gear.setSize(hoverSurface.gear.getPreferredSize());
                    check(hoverSurface.gear.getIcon().getIconWidth() >= 20 && hoverSurface.gear.getWidth() >= 34,
                        "gear has a visible vector icon and target " + dark);
                    int idle = pixel(hoverSurface.gear, 5, 5);
                    hoverSurface.gear.dispatchEvent(new java.awt.event.MouseEvent(hoverSurface.gear,
                        java.awt.event.MouseEvent.MOUSE_ENTERED, 0, 0, 5, 5, 0, false));
                    check(pixel(hoverSurface.gear, 5, 5) != idle && hoverSurface.gear.getForeground().equals(Color.WHITE),
                        "pointer hover changes button fill and foreground " + dark);
                    hoverSurface.gear.dispatchEvent(new java.awt.event.MouseEvent(hoverSurface.gear,
                        java.awt.event.MouseEvent.MOUSE_EXITED, 0, 0, -1, -1, 0, false));
                    check(pixel(hoverSurface.gear, 5, 5) == idle, "pointer exit restores button background " + dark);
                    for (JPopupMenu popup : new JPopupMenu[]{hoverSurface.settingsMenu(), hoverSurface.selectionMenu()}) {
                        for (Component component : popup.getComponents()) {
                            if (!(component instanceof JMenuItem item) || !item.isEnabled()) continue;
                            item.setSize(300, 30);
                            int normal = pixel(item, 6, 6);
                            item.setArmed(true);
                            check(pixel(item, 6, 6) == ChatSurface.accent().getRGB() && pixel(item, 6, 6) != normal,
                                "menu hover/keyboard selection has accent fill: " + item.getText() + " " + dark);
                            item.setArmed(false);
                        }
                    }
                    for (int width : new int[]{360, 600}) {
                        String variant = (dark ? "dark" : "light") + "-" + width;
                        ChatSurface surface = new ChatSurface(); populateModels(surface); surface.provider.setSelectedItem(AgentSession.Provider.Claude);
                        surface.currentName.setText("TaskQueue.java"); surface.transcript.removeAll();
                        var question = surface.message("You", "How can we prevent the same task from running twice?"); question.render();
                        question.context(List.of("src/TaskQueue.java", "src/Lease.java", "test/TaskQueueTest.java"), "Project");
                        var answer = surface.message("Claude", "The queue needs to check the lease before acknowledging a task.\n\nI suggest rejecting expired tokens and adding a test for duplicate acknowledgements.\n\n```java\nif (lease.isExpired(now)) {\n    return false;\n}\n```\n\nThe changes below are ready for your review."); answer.render();
                        JButton review = answer.changes(List.of("src/TaskQueue.java", "test/TaskQueueTest.java"));
                        surface.composer.setText(""); surface.setSize(width, 850);
                        for (int i = 0; i < 6; i++) layout(surface);
                        check(surface.scroll.getHeight() >= 500, "conversation retains space " + variant);
                        check(surface.send.getWidth() >= 40 && surface.selector.getWidth() >= 120 && surface.selector.getHeight() <= 30, "composer controls fit without wrapping " + variant);
                        check(surface.composer.getHeight() >= 50, "question box remains visible " + variant);
                        check(answer.body.getHeight() > 100 && review.getWidth() > 70, "response and review action have usable size " + variant);
                        check(answer.body.modelToView2D(answer.body.getDocument().getLength()-1).getMaxY() <= answer.body.getHeight(), "last response line is visible " + variant + " (last=" + answer.body.modelToView2D(answer.body.getDocument().getLength()-1).getMaxY() + ", height=" + answer.body.getHeight() + ", preferred=" + answer.body.getPreferredSize().height + ")");
                        check(surface.settings.getParent() == null && surface.signIn.getParent() == null && !surface.stop.isVisible(), "secondary account and stop controls are tucked away " + variant);
                        BufferedImage image = new BufferedImage(width, 850, BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = image.createGraphics();
                        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        surface.printAll(graphics); graphics.dispose();
                        ImageIO.write(image, "png", Path.of(args[0], "chat-preview-" + variant + ".png").toFile());
                    }
                    for (int width : new int[]{380, 540}) {
                        int height = width == 380 ? 440 : 570;
                        String variant = (dark ? "dark" : "light") + "-" + width;
                        ChatSurface inline = new ChatSurface(); populateModels(inline); inline.inlineMode();
                        inline.putClientProperty("agentBridge.conversation", (Runnable) () -> inline.setSize(width, 540));
                        inline.provider.setSelectedItem(AgentSession.Provider.Claude);
                        inline.currentName.setText("TaskQueue.java · lines 24–26"); inline.setSize(width, inline.getPreferredSize().height);
                        for (int i = 0; i < 6; i++) layout(inline);
                        check(!inline.projectContext.isSelected() && !inline.currentFile.isVisible(), "inline context defaults to the anchored file " + variant);
                        check(inline.transcript.getComponentCount() == 0 && !inline.scroll.isVisible() && inline.composer.getHeight() >= 50 && inline.send.getWidth() >= 40,
                            "inline welcome and composer fit compact popup " + variant);
                        BufferedImage emptyImage = new BufferedImage(width, inline.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D emptyGraphics = emptyImage.createGraphics();
                        emptyGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        inline.printAll(emptyGraphics); emptyGraphics.dispose();
                        ImageIO.write(emptyImage, "png", Path.of(args[0], "inline-empty-" + variant + ".png").toFile());
                        check(!inline.toolbar.isVisible() && inline.gear.getParent().isVisible() && inline.getHeight() <= 220,
                            "inline has no top toolbar and opens within 220 pixels " + variant);
                        inline.transcript.removeAll();
                        inline.message("You", "Why do we need an unexpired lease here?").render();
                        var answer = inline.message("Claude", "An expired lease may belong to a worker that lost ownership. Checking the token prevents it from acknowledging another worker's task."); answer.render();
                        var review = answer.changes(List.of("src/TaskQueue.java"));
                        for (int i = 0; i < 6; i++) layout(inline);
                        check(review.getWidth() > 70 && inline.scroll.getHeight() >= 100, "inline answers keep review accessible through the transcript " + variant);
                        BufferedImage image = new BufferedImage(width, inline.getHeight(), BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = image.createGraphics();
                        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        inline.printAll(graphics); graphics.dispose();
                        ImageIO.write(image, "png", Path.of(args[0], "inline-preview-" + variant + ".png").toFile());
                    }
                }
                for (String key : new String[]{"Label.font", "Button.font", "ComboBox.font", "CheckBox.font", "TextArea.font", "TextPane.font"})
                    UIManager.put(key, new Font("SansSerif", Font.PLAIN, 22));
                ChatSurface scaled = new ChatSurface(); populateModels(scaled); scaled.inlineMode();
                scaled.currentName.setText("TaskQueue.java · lines 9–21");
                scaled.setSize(540, scaled.getPreferredSize().height);
                for (int i = 0; i < 6; i++) layout(scaled);
                check(scaled.transcript.getComponentCount() == 0 && !scaled.scroll.isVisible(), "new chats have no introductory block or starter cards");
                for (var provider : AgentSession.Provider.values()) {
                    scaled.provider.setSelectedItem(provider);
                    for (int i = 0; i < 3; i++) layout(scaled);
                    check(scaled.selector.getPreferredSize().width <= scaled.selector.getWidth() && scaled.selector.getHeight() <= 30,
                        "compact provider/model summary fits at larger IDE font: " + provider);
                }
                check(scaled.model.getParent() == null && scaled.provider.getParent() == null, "large native dropdowns are absent from the chat layout");
                var options = scaled.selectionMenu();
                JMenu providers = (JMenu) options.getComponent(0);
                check(providers.getItemCount() == 3 && providers.getItem(0).getText().equals("OpenAI Codex") && providers.getItem(2).getText().equals("GitHub Copilot"), "provider menu retains complete names");
                providers.getItem(1).doClick();
                for (Component item : scaled.selectionMenu().getComponents())
                    if (item instanceof JMenuItem menuItem && menuItem.getText().equals("Sonnet")) menuItem.doClick();
                check(scaled.provider.getSelectedItem() == AgentSession.Provider.Claude && ((ModelCatalog.Option) scaled.model.getSelectedItem()).id().equals("sonnet")
                    && scaled.selector.getText().contains("Claude · Sonnet"), "menu selection updates provider/model state and visible summary");
                boolean[] clicked = {false}; scaled.settings.addActionListener(e -> clicked[0] = true);
                for (Component item : scaled.settingsMenu().getComponents()) {
                    if (item instanceof JMenuItem menuItem && menuItem.getText().equals("Settings…")) menuItem.doClick();
                    if (item instanceof JCheckBoxMenuItem checkbox) checkbox.doClick();
                }
                check(clicked[0] && scaled.projectContext.isSelected(), "gear forwards settings and project-context actions");
                scaled.signIn.setEnabled(false);
                check(java.util.Arrays.stream(scaled.settingsMenu().getComponents()).filter(c -> c instanceof JMenuItem item && item.getText().equals("Sign in"))
                    .allMatch(c -> !c.isEnabled()), "gear respects disabled login actions");
                check(scaled.selector.getToolTipText().length() <= 40 && scaled.gear.getToolTipText().length() <= 40, "routine tooltips are brief");
                BufferedImage scaledImage = new BufferedImage(scaled.getWidth(), scaled.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D scaledGraphics = scaledImage.createGraphics(); scaled.printAll(scaledGraphics); scaledGraphics.dispose();
                ImageIO.write(scaledImage, "png", Path.of(args[0], "inline-preview-large-font.png").toFile());
                var card = new ChatSurface.Card("Claude", "A fix.\n```agent-bridge-edit\n"); card.append("{\"edits\":[]}");
                check(!card.body.getText().contains("edits"), "structured edit payload hidden during streaming");
                card.setText("<script>alert('x')</script>"); check(card.body.getText().contains("<script>"), "model HTML displayed as inert text");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.out.println("Passed " + passed + " context, edit and layout checks.");
    }
    static int pixel(JComponent component, int x, int y) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); component.paint(graphics); graphics.dispose();
        return image.getRGB(x, y);
    }
    static void populateModels(ChatSurface surface) {
        surface.reloadModels.setVisible(false);
        for (var option : ModelCatalog.choices(ModelCatalog.defaults(AgentSession.Provider.Claude), "")) surface.model.addItem(option);
    }
    static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested); }
}
