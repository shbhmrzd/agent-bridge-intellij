package dev.agentbridge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;

/** Keeps an inline conversation on its original document, independent of editor focus. */
final class SelectionContext implements Disposable {
    final Document document;
    private final RangeMarker range;

    SelectionContext(Document document, int start, int end) {
        if (start < 0 || end <= start || end > document.getTextLength())
            throw new IllegalArgumentException("Select a non-empty range of code first.");
        this.document = document;
        range = document.createRangeMarker(start, end);
    }
    String text() {
        return hasRange() ? document.getText(new TextRange(range.getStartOffset(), range.getEndOffset())) : "";
    }
    boolean hasRange() { return range.isValid() && range.getEndOffset() > range.getStartOffset(); }
    String label() {
        if (!hasRange()) return "selection cleared · file context";
        int first = document.getLineNumber(range.getStartOffset()) + 1;
        int last = document.getLineNumber(range.getEndOffset() - 1) + 1;
        return first == last ? "line " + first : "lines " + first + "–" + last;
    }
    @Override public void dispose() { range.dispose(); }
}
