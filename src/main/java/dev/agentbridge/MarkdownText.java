package dev.agentbridge;

import java.util.regex.*;
import javax.swing.text.*;

/** Inert styled text: fenced code is never interpreted as HTML or inline Markdown. */
final class MarkdownText {
    private static final Pattern FENCE = Pattern.compile("^( {0,3})(`{3,}|~{3,})(.*)$");
    private static final Pattern INLINE = Pattern.compile("(`+)(.+?)\\1(?!`)|\\*\\*(.+?)\\*\\*");

    static DefaultStyledDocument render(String text, AttributeSet normal, AttributeSet code, AttributeSet bold) {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        char marker = 0;
        int length = 0, indent = 0;
        try {
            for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                Matcher fence = FENCE.matcher(line);
                if (marker != 0) {
                    if (fence.matches() && fence.group(2).charAt(0) == marker
                        && fence.group(2).length() >= length && fence.group(3).isBlank()) {
                        marker = 0;
                        continue;
                    }
                    int remove = 0;
                    while (remove < indent && remove < line.length() && line.charAt(remove) == ' ') remove++;
                    int start = doc.getLength();
                    doc.insertString(start, line.substring(remove) + "\n", code);
                    SimpleAttributeSet block = new SimpleAttributeSet();
                    StyleConstants.setLeftIndent(block, 10f);
                    StyleConstants.setRightIndent(block, 10f);
                    doc.setParagraphAttributes(start, doc.getLength() - start, block, false);
                    continue;
                }
                if (fence.matches() && (fence.group(2).charAt(0) != '`' || !fence.group(3).contains("`"))) {
                    marker = fence.group(2).charAt(0); length = fence.group(2).length(); indent = fence.group(1).length();
                    continue;
                }
                int paragraphStart = doc.getLength();
                SimpleAttributeSet prose = new SimpleAttributeSet();
                StyleConstants.setLeftIndent(prose, 0f); StyleConstants.setRightIndent(prose, 0f);
                doc.setParagraphAttributes(paragraphStart, 0, prose, false);
                if (line.matches("^ {0,3}#{1,6} .*")) {
                    doc.insertString(doc.getLength(), line.replaceFirst("^ {0,3}#{1,6} ", "") + "\n", bold);
                    continue;
                }
                Matcher inline = INLINE.matcher(line);
                int at = 0;
                while (inline.find()) {
                    doc.insertString(doc.getLength(), line.substring(at, inline.start()), normal);
                    doc.insertString(doc.getLength(), inline.group(2) != null ? inline.group(2) : inline.group(3),
                        inline.group(2) != null ? code : bold);
                    at = inline.end();
                }
                doc.insertString(doc.getLength(), line.substring(at) + "\n", normal);
            }
        } catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        SimpleAttributeSet paragraphs = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(paragraphs, .14f);
        doc.setParagraphAttributes(0, doc.getLength(), paragraphs, false);
        return doc;
    }
}
