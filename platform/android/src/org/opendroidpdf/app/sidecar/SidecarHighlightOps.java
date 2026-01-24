package org.opendroidpdf.app.sidecar;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.app.reflow.ReflowPrefsSnapshot;
import org.opendroidpdf.app.reflow.ReflowPrefsStore;
import org.opendroidpdf.app.sidecar.model.SidecarHighlight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SidecarHighlightOps {
    private final String docId;
    @Nullable private final String layoutProfileId;
    private final SidecarAnnotationStore store;
    @Nullable private final ReflowPrefsStore reflowPrefsStore;
    @Nullable private final ReflowPrefsSnapshot reflowPrefsSnapshot;

    private final Map<Integer, List<SidecarHighlight>> highlightCache = new HashMap<>();

    SidecarHighlightOps(@NonNull String docId,
                        @Nullable String layoutProfileId,
                        @NonNull SidecarAnnotationStore store,
                        @Nullable ReflowPrefsStore reflowPrefsStore,
                        @Nullable ReflowPrefsSnapshot reflowPrefsSnapshot) {
        this.docId = docId;
        this.layoutProfileId = layoutProfileId;
        this.store = store;
        this.reflowPrefsStore = reflowPrefsStore;
        this.reflowPrefsSnapshot = reflowPrefsSnapshot;
    }

    void clearCache() { highlightCache.clear(); }

    @NonNull
    List<SidecarHighlight> highlightsForPage(int pageIndex) {
        List<SidecarHighlight> cached = highlightCache.get(pageIndex);
        if (cached != null) return cached;
        List<SidecarHighlight> loaded = store.listHighlights(docId, pageIndex, layoutProfileId);
        List<SidecarHighlight> ro = Collections.unmodifiableList(loaded);
        highlightCache.put(pageIndex, ro);
        return ro;
    }

    @NonNull
    SidecarHighlight addHighlight(int pageIndex,
                                 @NonNull Annotation.Type type,
                                 @NonNull PointF[] quadPoints,
                                 int color,
                                 float opacity,
                                 long createdAtEpochMs,
                                 long reflowLocation,
                                 @Nullable TextWord[][] pageTextLines,
                                 @Nullable String quote,
                                 float docProgress01) {
        String quotePrefix = null;
        String quoteSuffix = null;
        int anchorStartWord = -1;
        int anchorEndWordExclusive = -1;
        if (quote != null && pageTextLines != null) {
            String normalizedQuote = TextAnchorUtils.normalizeWhitespace(quote);
            if (normalizedQuote != null) {
                TextAnchorUtils.PageTextIndex index = TextAnchorUtils.buildIndex(pageTextLines);
                RectF selectionBounds = TextAnchorUtils.boundsFromQuads(quadPoints);
                if (selectionBounds != null) {
                    TextAnchorUtils.QuoteMatch match = TextAnchorUtils.bestMatchByBounds(index, normalizedQuote, selectionBounds);
                    if (match != null) {
                        quotePrefix = TextAnchorUtils.prefixContext(index, match.start, TextAnchorUtils.DEFAULT_CONTEXT_CHARS);
                        quoteSuffix = TextAnchorUtils.suffixContext(index, match.end, TextAnchorUtils.DEFAULT_CONTEXT_CHARS);
                        TextAnchorUtils.WordRange range = TextAnchorUtils.wordRangeForCharRange(index, match.start, match.end);
                        if (range != null) {
                            anchorStartWord = range.startWord;
                            anchorEndWordExclusive = range.endWordExclusive;
                        }
                    }
                }
            }
        }
        SidecarHighlight hl = new SidecarHighlight(
                UUID.randomUUID().toString(),
                pageIndex,
                layoutProfileId,
                type,
                color,
                opacity,
                createdAtEpochMs,
                quadPoints,
                quote,
                quotePrefix,
                quoteSuffix,
                docProgress01,
                reflowLocation,
                anchorStartWord,
                anchorEndWordExclusive);
        store.insertHighlight(docId, hl);
        SidecarReflowUtils.recordAnnotatedLayoutIfPossible(docId, layoutProfileId, reflowPrefsStore, reflowPrefsSnapshot);
        List<SidecarHighlight> current = new ArrayList<>(highlightsForPage(pageIndex));
        current.add(hl);
        highlightCache.put(pageIndex, Collections.unmodifiableList(current));
        return hl;
    }

    @Nullable
    SidecarHighlight removeHighlight(int pageIndex, @NonNull String highlightId) {
        List<SidecarHighlight> current = new ArrayList<>(highlightsForPage(pageIndex));
        SidecarHighlight removed = null;
        for (int i = 0; i < current.size(); i++) {
            SidecarHighlight h = current.get(i);
            if (h != null && highlightId.equals(h.id)) {
                removed = h;
                current.remove(i);
                break;
            }
        }
        if (removed != null) {
            store.deleteHighlight(docId, highlightId);
            highlightCache.put(pageIndex, Collections.unmodifiableList(current));
        }
        return removed;
    }

    void restoreHighlight(@NonNull SidecarHighlight highlight) {
        store.insertHighlight(docId, highlight);
        List<SidecarHighlight> current = new ArrayList<>(highlightsForPage(highlight.pageIndex));
        current.add(highlight);
        highlightCache.put(highlight.pageIndex, Collections.unmodifiableList(current));
    }

    /** Best-effort highlight re-anchoring for reflow docs after a relayout. */
    int reanchorHighlightsForCurrentLayout(@NonNull SidecarHighlightReanchorer.PageTextProvider pageText) {
        String layout = layoutProfileId;
        if (layout == null) return 0;
        int updated = SidecarHighlightReanchorer.reanchorHighlightsForCurrentLayout(docId, layout, store, pageText);
        if (updated > 0) {
            highlightCache.clear();
        }
        return updated;
    }
}

