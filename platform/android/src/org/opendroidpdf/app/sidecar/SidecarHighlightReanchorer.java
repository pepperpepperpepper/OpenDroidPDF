package org.opendroidpdf.app.sidecar;

import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.TextWord;
import org.opendroidpdf.app.reflow.ReflowAnnotatedLayout;
import org.opendroidpdf.app.reflow.ReflowPrefsSnapshot;
import org.opendroidpdf.app.reflow.ReflowPrefsStore;
import org.opendroidpdf.app.sidecar.model.SidecarHighlight;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort highlight re-anchoring for reflow docs after a relayout.
 *
 * <p>This uses the stored highlight {@code quote} text to search for the selection in the current
 * layout and then rewrites the highlight geometry for the current layout profile id.</p>
 */
public final class SidecarHighlightReanchorer {
    private static final int HIGHLIGHT_REANCHOR_RADIUS_PAGES = 48;
    private static final int HIGHLIGHT_REANCHOR_MIN_CONTEXT_SCORE = 6;

    private SidecarHighlightReanchorer() {}

    /** Minimal page-text capability used to re-anchor highlights across reflow relayouts. */
    public interface PageTextProvider {
        int pageCount();
        @Nullable TextWord[][] textLines(int pageIndex);
        /** Returns a page number for an encoded MuPDF {@code fz_location}, or {@code -1} if unsupported. */
        int pageNumberFromReflowLocation(long encodedLocation);
    }

    /**
     * Re-anchors highlights into the provided layout profile id, returning the number updated.
     *
     * <p>This is intentionally conservative: it only updates highlights when a search hit is found,
     * leaving unmatched highlights in their original layout profile so the user can still switch
     * back to the annotated layout.</p>
     */
    public static int reanchorHighlightsForCurrentLayout(@NonNull String docId,
                                                         @NonNull String layoutProfileId,
                                                         @NonNull SidecarAnnotationStore store,
                                                         @NonNull PageTextProvider pageText) {
        if (layoutProfileId == null) return 0;
        int pageCount = Math.max(0, pageText.pageCount());
        if (pageCount <= 0) return 0;

        List<SidecarHighlight> all;
        try {
            all = store.listAllHighlights(docId);
        } catch (Throwable t) {
            return 0;
        }
        if (all.isEmpty()) return 0;

        int updated = 0;
        for (SidecarHighlight h : all) {
            if (h == null) continue;
            if (layoutProfileId.equals(h.layoutProfileId)) continue;
            String quote = h.quote;
            if (quote == null) continue;
            quote = quote.trim();
            if (quote.isEmpty()) continue;

            SearchHit hit = findHighlightSearchHit(pageText, h, pageCount, quote);
            if (hit == null || hit.quadPoints == null || hit.quadPoints.length < 4) continue;

            SidecarHighlight reanchored = new SidecarHighlight(
                    h.id,
                    hit.pageIndex,
                    layoutProfileId,
                    h.type,
                    h.color,
                    h.opacity,
                    h.createdAtEpochMs,
                    hit.quadPoints,
                    h.quote,
                    h.quotePrefix,
                    h.quoteSuffix,
                    h.docProgress01,
                    h.reflowLocation,
                    h.anchorStartWord,
                    h.anchorEndWordExclusive);
            store.insertHighlight(docId, reanchored);
            updated++;
        }

        return updated;
    }

    @Nullable
    private static SearchHit findHighlightSearchHit(@NonNull PageTextProvider pageText,
                                                   @NonNull SidecarHighlight highlight,
                                                   int pageCount,
                                                   @NonNull String query) {
        int target = computeReanchorTargetPageIndex(pageText, highlight, pageCount);
        return searchAround(pageText, highlight, target, pageCount, query);
    }

    private static int computeReanchorTargetPageIndex(@NonNull PageTextProvider pageText,
                                                      @NonNull SidecarHighlight h,
                                                      int pageCount) {
        int max = Math.max(0, pageCount - 1);
        long loc = h.reflowLocation;
        if (loc != -1L) {
            try {
                int fromLoc = pageText.pageNumberFromReflowLocation(loc);
                if (fromLoc >= 0) return clampInt(fromLoc, 0, max);
            } catch (Throwable ignore) {
            }
        }
        float p = h.docProgress01;
        if (p >= 0f && p <= 1f) {
            int t = Math.round(p * max);
            return clampInt(t, 0, max);
        }
        return clampInt(h.pageIndex, 0, max);
    }

    @Nullable
    private static SearchHit searchAround(@NonNull PageTextProvider pageText,
                                          @NonNull SidecarHighlight highlight,
                                          int target,
                                          int pageCount,
                                          @NonNull String query) {
        int max = Math.max(0, pageCount - 1);
        int center = clampInt(target, 0, max);
        int radius = Math.min(HIGHLIGHT_REANCHOR_RADIUS_PAGES, max);
        int bestScore = Integer.MIN_VALUE;
        SearchHit best = null;
        for (int d = 0; d <= radius; d++) {
            int left = center - d;
            if (left >= 0) {
                SearchHit candidate = findPageMatch(pageText, highlight, left, query);
                if (candidate != null) {
                    int score = candidate.score * 10 - d;
                    if (score > bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
            int right = center + d;
            if (d != 0 && right <= max) {
                SearchHit candidate = findPageMatch(pageText, highlight, right, query);
                if (candidate != null) {
                    int score = candidate.score * 10 - d;
                    if (score > bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private static SearchHit findPageMatch(@NonNull PageTextProvider pageText,
                                          @NonNull SidecarHighlight highlight,
                                          int pageIndex,
                                          @NonNull String quote) {
        org.opendroidpdf.TextWord[][] lines = pageText.textLines(pageIndex);
        TextAnchorUtils.PageTextIndex index = TextAnchorUtils.buildIndex(lines);
        if (index.text.isEmpty()) return null;

        TextAnchorUtils.QuoteMatch match = highlight.anchorStartWord >= 0
                ? TextAnchorUtils.bestMatchByContextAndWordAnchor(index, quote, highlight.quotePrefix, highlight.quoteSuffix, highlight.anchorStartWord)
                : TextAnchorUtils.bestMatchByContext(index, quote, highlight.quotePrefix, highlight.quoteSuffix);
        if (match == null || match.quadPoints.length < 4) return null;

        boolean anchored = (highlight.quotePrefix != null && !highlight.quotePrefix.isEmpty())
                || (highlight.quoteSuffix != null && !highlight.quoteSuffix.isEmpty());
        if (anchored && match.score < HIGHLIGHT_REANCHOR_MIN_CONTEXT_SCORE) {
            return null;
        }

        return new SearchHit(pageIndex, match.score, match.quadPoints);
    }

    private static int clampInt(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static final class SearchHit {
        final int pageIndex;
        final int score;
        @NonNull final PointF[] quadPoints;
        SearchHit(int pageIndex, int score, @NonNull PointF[] quadPoints) {
            this.pageIndex = pageIndex;
            this.score = score;
            this.quadPoints = quadPoints;
        }
    }
}

final class SidecarReflowUtils {
    private static final Pattern LAYOUT_ID_PAGE_SIZE =
            Pattern.compile("w(-?\\d+)_h(-?\\d+)_");

    private SidecarReflowUtils() {}

    static void recordAnnotatedLayoutIfPossible(@NonNull String docId,
                                                @Nullable String layoutProfileId,
                                                @Nullable ReflowPrefsStore store,
                                                @Nullable ReflowPrefsSnapshot snap) {
        if (layoutProfileId == null) return;
        if (store == null || snap == null) return;
        try {
            PointF pageSize = parsePageSizeFromLayoutProfileId(layoutProfileId);
            if (pageSize == null || pageSize.x <= 0f || pageSize.y <= 0f) return;
            store.saveAnnotatedLayout(docId, new ReflowAnnotatedLayout(
                    snap,
                    pageSize.x / 2f,
                    pageSize.y / 2f));
        } catch (Throwable ignore) {
        }
    }

    @Nullable
    static PointF parsePageSizeFromLayoutProfileId(@NonNull String layoutProfileId) {
        Matcher m = LAYOUT_ID_PAGE_SIZE.matcher(layoutProfileId);
        if (!m.find()) return null;
        try {
            int w10 = Integer.parseInt(m.group(1));
            int h10 = Integer.parseInt(m.group(2));
            return new PointF(w10 / 10f, h10 / 10f);
        } catch (Throwable t) {
            return null;
        }
    }
}
