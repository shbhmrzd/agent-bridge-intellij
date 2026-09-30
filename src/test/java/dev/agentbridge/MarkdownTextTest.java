package dev.agentbridge;

import java.awt.Font;
import javax.swing.SwingUtilities;
import javax.swing.text.*;

/** Regression cases for provider responses, including fences split across streaming chunks. */
public final class MarkdownTextTest {
    private static int passed;
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        passed++; System.out.println("PASS " + label);
    }
    private static String text(StyledDocument doc) throws Exception { return doc.getText(0, doc.getLength()); }
    private static StyledDocument render(String text) {
        SimpleAttributeSet normal = new SimpleAttributeSet(), code = new SimpleAttributeSet(), bold = new SimpleAttributeSet();
        StyleConstants.setFontFamily(normal, Font.SANS_SERIF); StyleConstants.setFontFamily(code, Font.MONOSPACED);
        StyleConstants.setBold(bold, true);
        return MarkdownText.render(text, normal, code, bold);
    }
    public static void main(String[] args) throws Exception {
        var tilde = render("Before\n~~~java\n    return value;\n~~~\nAfter");
        check(text(tilde).equals("Before\n    return value;\nAfter\n"), "tilde fence hides delimiters and preserves code indentation");
        check(StyleConstants.getFontFamily(tilde.getCharacterElement(7).getAttributes()).equals(Font.MONOSPACED), "tilde block uses code font");
        check(StyleConstants.getFontFamily(tilde.getCharacterElement(text(tilde).indexOf("After")).getAttributes()).equals(Font.SANS_SERIF), "normal prose resumes after closing fence");
        check(StyleConstants.getLeftIndent(tilde.getParagraphElement(text(tilde).indexOf("After")).getAttributes()) == 0f, "prose following code does not inherit block indentation");
        check(text(render("````md\n```java\n**literal**\n```\n````")).equals("```java\n**literal**\n```\n"), "long fence preserves shorter fences and literal Markdown within code");
        check(text(render("~~~\n```\nx\n~~~")).equals("```\nx\n"), "backticks cannot close a tilde block");
        check(text(render("```\n~~~\nx\n```suffix\n```\nDone")).equals("~~~\nx\n```suffix\nDone\n"), "mismatched marker and trailing text do not close a block");
        check(text(render("  ~~~java\r\n    x\r\n  ~~~~  \r\nend")).equals("  x\nend\n"), "indented CRLF fence accepts a longer closing delimiter");
        check(text(render("~~~java\npartial")).equals("partial\n"), "unfinished streaming block remains formatted as code");
        var inline = render("Use ``a`b`` and **bold**.");
        check(text(inline).equals("Use a`b and bold.\n"), "inline code allows embedded backticks");
        check(StyleConstants.isBold(inline.getCharacterElement(text(inline).indexOf("bold")).getAttributes()), "bold prose still renders");
        check(text(render("<script>literal</script>\n~~~html\n<img src='https://example.invalid'>\n~~~")).contains("<img src="), "HTML remains inert literal text");
        SwingUtilities.invokeAndWait(() -> {
            var card = new ChatSurface.Card("Claude", "~~");
            card.append("~java\nint answer = 42;\n~~"); card.render();
            check(card.body.getText().equals("int answer = 42;\n~~\n"), "partial closing fence is kept as code until complete");
            card.append("~\nDone"); card.render();
            check(card.body.getText().equals("int answer = 42;\nDone\n"), "chunk-split fences disappear after completion");
            var attrs = card.body.getStyledDocument().getCharacterElement(0).getAttributes();
            check(StyleConstants.getFontFamily(attrs).equals(Font.MONOSPACED) && attrs.isDefined(StyleConstants.Background), "chat code has monospace font and a shaded background");
            card.setText("Before\n```agent-bridge-edit\n{\"edits\":[]}");
            check(!card.body.getText().contains("edits") && card.body.getText().contains("Preparing"), "rendering during streaming never reveals structured edit JSON");
            var surface = new ChatSurface(); surface.provider.setSelectedItem(AgentSession.Provider.Claude);
            for (var option : ModelCatalog.choices(ModelCatalog.defaults(AgentSession.Provider.Claude), "opus")) surface.model.addItem(option);
            for (var item : surface.selectionMenu().getComponents())
                if (item instanceof javax.swing.JMenuItem row && row.getText().equals("Opus 5.5")) row.doClick();
            check(((ModelCatalog.Option) surface.model.getSelectedItem()).id().equals("claude-opus-5-5"), "clicking versioned menu row selects the exact ID");
            check(surface.selector.getText().contains("Opus 5.5") && surface.selector.getToolTipText().contains("claude-opus-5-5"), "composer retains version label and exposes exact ID");
        });
        System.out.println("Passed " + passed + " Markdown rendering checks.");
    }
}
