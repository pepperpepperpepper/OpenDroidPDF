package org.opendroidpdf.app.assistant;

import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.PageView;
import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AssistantContextTextExtractor {
    public static final int DEFAULT_MAX_CONTEXT_CHARS = 200_000;

    public static final class TextResult {
        public final String text;
        public final boolean truncated;
        public final int pagesProcessed;

        private TextResult(String text, boolean truncated, int pagesProcessed) {
            this.text = text != null ? text : "";
            this.truncated = truncated;
            this.pagesProcessed = pagesProcessed;
        }
    }

    private AssistantContextTextExtractor() {}

    @Nullable
    public static String selectionTextOrNull(@Nullable PageView pageView) {
        if (!(pageView instanceof MuPDFPageView)) return null;
        final StringBuilder text = new StringBuilder();
        ((MuPDFPageView) pageView).processSelectedText(new TextProcessor() {
            StringBuilder line;

            @Override public void onStartLine() { line = new StringBuilder(); }
            @Override public void onWord(TextWord word) { if (line != null && word != null) line.append(word.w); }
            @Override public void onEndLine() {
                if (line == null) return;
                if (text.length() > 0) text.append('\n');
                text.append(line);
            }
            @Override public void onEndText() {}
        });
        String out = text.toString().trim();
        return out.isEmpty() ? null : out;
    }

    public static TextResult pageText(MuPdfRepository repo, int pageIndex, int maxChars) {
        if (repo == null) return new TextResult("", false, 0);
        StringBuilder sb = new StringBuilder();
        appendPageText(sb, repo, pageIndex, maxChars);
        String out = sb.toString().trim();
        boolean truncated = out.length() >= maxChars;
        if (out.length() > maxChars) out = out.substring(0, maxChars);
        return new TextResult(out, truncated, 1);
    }

    public static TextResult documentText(MuPdfRepository repo, int maxChars, @Nullable AtomicBoolean cancelled) {
        if (repo == null) return new TextResult("", false, 0);
        int total = 0;
        try { total = repo.getPageCount(); } catch (Throwable ignore) { total = 0; }
        if (total <= 0) return new TextResult("", false, 0);

        StringBuilder sb = new StringBuilder(Math.min(maxChars, 16_384));
        boolean truncated = false;
        int pages = 0;
        for (int page = 0; page < total; page++) {
            if (cancelled != null && cancelled.get()) break;
            if (sb.length() >= maxChars) {
                truncated = true;
                break;
            }
            if (page > 0) sb.append("\n\n");
            sb.append("Page ").append(page + 1).append(":\n");
            appendPageText(sb, repo, page, maxChars);
            pages++;
        }

        String out = sb.toString().trim();
        if (out.length() > maxChars) {
            out = out.substring(0, maxChars);
            truncated = true;
        }
        return new TextResult(out, truncated, pages);
    }

    public static TextResult pageRangeText(MuPdfRepository repo,
                                          int startPageIndex,
                                          int endPageIndex,
                                          int maxChars,
                                          @Nullable AtomicBoolean cancelled,
                                          boolean includePageHeaders) {
        if (repo == null) return new TextResult("", false, 0);
        int total = 0;
        try { total = repo.getPageCount(); } catch (Throwable ignore) { total = 0; }
        if (total <= 0) return new TextResult("", false, 0);

        int start = Math.max(0, Math.min(startPageIndex, total - 1));
        int end = Math.max(0, Math.min(endPageIndex, total - 1));
        if (end < start) end = start;

        StringBuilder sb = new StringBuilder(Math.min(maxChars, 16_384));
        boolean truncated = false;
        int pages = 0;

        for (int page = start; page <= end; page++) {
            if (cancelled != null && cancelled.get()) break;
            if (sb.length() >= maxChars) {
                truncated = true;
                break;
            }
            if (pages > 0) sb.append("\n\n");
            if (includePageHeaders) sb.append("Page ").append(page + 1).append(":\n");
            appendPageText(sb, repo, page, maxChars);
            pages++;
        }

        String out = sb.toString().trim();
        if (out.length() > maxChars) {
            out = out.substring(0, maxChars);
            truncated = true;
        }
        return new TextResult(out, truncated, pages);
    }

    private static void appendPageText(StringBuilder sb, MuPdfRepository repo, int pageIndex, int maxChars) {
        if (sb == null || repo == null) return;
        TextWord[][] lines;
        try {
            lines = repo.extractTextLines(pageIndex);
        } catch (Throwable t) {
            return;
        }
        if (lines == null) return;

        boolean firstLine = true;
        for (TextWord[] line : lines) {
            if (sb.length() >= maxChars) return;
            if (line == null) continue;
            if (!firstLine) sb.append('\n');
            firstLine = false;
            for (TextWord word : line) {
                if (sb.length() >= maxChars) return;
                if (word != null) sb.append(word.w);
            }
        }
    }
}
