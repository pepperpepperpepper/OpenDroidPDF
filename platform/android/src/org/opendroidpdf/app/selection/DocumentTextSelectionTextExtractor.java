package org.opendroidpdf.app.selection;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.TextSelector;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Extracts plain text for a document-level text selection, potentially spanning pages.
 */
public final class DocumentTextSelectionTextExtractor {
    private DocumentTextSelectionTextExtractor() {}

    @NonNull
    public static String extract(@NonNull MuPdfRepository repo,
                                 @NonNull DocumentTextSelection selection,
                                 boolean smartSelectionEnabled,
                                 int maxChars) {
        if (repo == null || selection == null) return "";
        if (maxChars <= 0) maxChars = Integer.MAX_VALUE;

        StringBuilder out = new StringBuilder(Math.min(maxChars, 16_384));
        float xmin = selection.globalXMin();
        float xmax = selection.globalXMax();

        for (int page = selection.startPage; page <= selection.endPage; page++) {
            if (out.length() >= maxChars) break;
            TextWord[][] lines;
            try {
                lines = repo.extractTextLines(page);
            } catch (Throwable ignore) {
                lines = null;
            }
            if (lines == null) continue;

            RectF box = selection.selectionBoxForPage(page);
            final StringBuilder pageText = new StringBuilder();
            TextSelector selector = smartSelectionEnabled
                    ? new TextSelector(lines, box, xmin, xmax)
                    : new TextSelector(lines, box);
            selector.select(new TextProcessor() {
                StringBuilder line;

                @Override public void onStartLine() { line = new StringBuilder(); }
                @Override public void onWord(TextWord word) { if (line != null && word != null) line.append(word.w); }
                @Override public void onEndLine() {
                    if (line == null) return;
                    if (pageText.length() > 0) pageText.append('\n');
                    pageText.append(line);
                }
                @Override public void onEndText() {}
            });

            String pageOut = pageText.toString().trim();
            if (pageOut.isEmpty()) continue;

            if (out.length() > 0) out.append("\n\n");
            out.append(pageOut);
        }

        String s = out.toString().trim();
        if (s.length() > maxChars) s = s.substring(0, maxChars);
        return s;
    }
}

