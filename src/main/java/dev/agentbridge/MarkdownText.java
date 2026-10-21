package dev.agentbridge;

import java.util.regex.*;
import javax.swing.text.*;

/** Inert styled text: fenced code is never interpreted as HTML or inline Markdown. */
final class MarkdownText {
    private static final Pattern FENCE = Pattern.compile("^( {0,3})(`{3,}|~{3,})(.*)$");
    private static final Pattern INLINE = Pattern.compile("(`+)(.+?)\\1(?!`)|\\*\\*(.+?)\\*\\*");
    private static final Pattern LIST = Pattern.compile("^( *)([-*+] |[0-9]+[.)] )(.*)$");
    private static final Pattern TABLE_DIVIDER = Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");

    static DefaultStyledDocument render(String text, AttributeSet normal, AttributeSet code, AttributeSet bold) {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        char marker = 0;
        int length = 0, indent = 0;
        try {
            String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
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
                if (index + 1 < lines.length && tableCells(line).length >= 2 && TABLE_DIVIDER.matcher(lines[index + 1]).matches()) {
                    String[] headings = tableCells(line);
                    index++;
                    while (index + 1 < lines.length && lines[index + 1].contains("|")) {
                        String[] values = tableCells(lines[index + 1]);
                        if (values.length != headings.length || TABLE_DIVIDER.matcher(lines[index + 1]).matches()) break;
                        for (int column = 0; column < values.length; column++) {
                            if (values[column].isEmpty()) continue;
                            if (column == 0) appendInline(doc, values[column] + "\n", normal, code, bold, true);
                            else {
                                String prefix = headings[column].isEmpty() ? "" : headings[column] + ": ";
                                appendInline(doc, prefix + values[column] + "\n", normal, code, bold, false);
                            }
                        }
                        index++;
                        doc.insertString(doc.getLength(), "\n", normal);
                    }
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
                Matcher list = LIST.matcher(line);
                if (list.matches()) {
                    int left = 12 + Math.min(3, list.group(1).length() / 2) * 12;
                    StyleConstants.setLeftIndent(prose, left + 16f);
                    StyleConstants.setFirstLineIndent(prose, -16f);
                    doc.setParagraphAttributes(paragraphStart, 0, prose, false);
                    appendInline(doc, (Character.isDigit(list.group(2).charAt(0)) ? list.group(2) : "• ") + list.group(3) + "\n", normal, code, bold, false);
                } else appendInline(doc, line + "\n", normal, code, bold, false);
            }
        } catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
        SimpleAttributeSet paragraphs = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(paragraphs, .14f);
        doc.setParagraphAttributes(0, doc.getLength(), paragraphs, false);
        return doc;
    }

    private static String[] tableCells(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String[] cells = trimmed.split("(?<!\\\\)\\|", -1);
        for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim().replace("\\|", "|");
        return cells;
    }

    private static void appendInline(DefaultStyledDocument doc, String value, AttributeSet normal, AttributeSet code,
                                     AttributeSet bold, boolean boldLine) throws BadLocationException {
        Matcher inline = INLINE.matcher(value);
        int at = 0;
        while (inline.find()) {
            doc.insertString(doc.getLength(), value.substring(at, inline.start()), boldLine ? bold : normal);
            AttributeSet inlineCode = code;
            if (boldLine) {
                SimpleAttributeSet emphasized = new SimpleAttributeSet(code);
                StyleConstants.setBold(emphasized, true);
                inlineCode = emphasized;
            }
            doc.insertString(doc.getLength(), inline.group(2) != null ? inline.group(2) : inline.group(3),
                inline.group(2) != null ? inlineCode : bold);
            at = inline.end();
        }
        doc.insertString(doc.getLength(), value.substring(at), boldLine ? bold : normal);
    }
}
