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
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * Document-scoped in-memory view of sidecar annotations with a backing store.
 *
 * <p>This acts as the "single place to ask" for overlay-rendered annotations for formats that
 * cannot (or should not) be modified in-place (EPUB, or PDFs without write access).</p>
 */
public final class SidecarAnnotationSession implements SidecarAnnotationProvider {
    private static final Pattern LAYOUT_ID_PAGE_SIZE =
            Pattern.compile("w(-?\\d+)_h(-?\\d+)_");
    private static final int HIGHLIGHT_REANCHOR_RADIUS_PAGES = 48;
    private static final int HIGHLIGHT_REANCHOR_MIN_CONTEXT_SCORE = 6;
    private final String docId;
    @Nullable private final String layoutProfileId;
    private final SidecarAnnotationStore store;
    @Nullable private final ReflowPrefsStore reflowPrefsStore;
    @Nullable private final ReflowPrefsSnapshot reflowPrefsSnapshot;

    private final ArrayDeque<UndoOp> undoStack = new ArrayDeque<>();

    private final Map<Integer, List<SidecarInkStroke>> inkCache = new HashMap<>();
    private final Map<Integer, List<SidecarHighlight>> highlightCache = new HashMap<>();
    private final Map<Integer, List<SidecarNote>> noteCache = new HashMap<>();

    public interface UndoOp {
        void undo();
    }

    /** Minimal page-text capability used to re-anchor highlights across reflow relayouts. */
    public interface PageTextProvider {
        int pageCount();
        @Nullable TextWord[][] textLines(int pageIndex);
    }

    public SidecarAnnotationSession(@NonNull String docId,
                                    @Nullable String layoutProfileId,
                                    @NonNull SidecarAnnotationStore store) {
        this(docId, null, layoutProfileId, store, null, null);
    }

    public SidecarAnnotationSession(@NonNull String docId,
                                    @Nullable String legacyDocId,
                                    @Nullable String layoutProfileId,
                                    @NonNull SidecarAnnotationStore store,
                                    @Nullable ReflowPrefsStore reflowPrefsStore,
                                    @Nullable ReflowPrefsSnapshot reflowPrefsSnapshot) {
        // Migration: older versions keyed sidecar rows by the URI string. When we can compute a
        // stable content id, migrate rows forward on first open.
        if (legacyDocId != null && !legacyDocId.isEmpty() && !legacyDocId.equals(docId)) {
            try {
                store.migrateDocId(legacyDocId, docId);
            } catch (Throwable ignore) {
            }
        }
        this.docId = docId;
        this.layoutProfileId = layoutProfileId;
        this.store = store;
        this.reflowPrefsStore = reflowPrefsStore;
        this.reflowPrefsSnapshot = reflowPrefsSnapshot;
    }

    @NonNull public String docId() { return docId; }
    @Nullable public String layoutProfileId() { return layoutProfileId; }

    public boolean hasUndo() { return !undoStack.isEmpty(); }

    public boolean undoLast() {
        UndoOp op = undoStack.poll();
        if (op == null) return false;
        op.undo();
        return true;
    }

    public boolean hasAnyInk() {
        try {
            return store.hasAnyInk(docId);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public boolean hasAnyAnnotationsInCurrentLayout() {
        try {
            return store.hasAnyAnnotationsInLayout(docId, layoutProfileId);
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * For reflowable docs, returns true if annotations exist under a different layout profile id.
     * Used by UI to prompt the user to switch back to an annotated layout profile.
     */
    public boolean hasAnnotationsInOtherLayouts() {
        if (layoutProfileId == null) return false;
        try {
            return store.hasAnyAnnotationsOutsideLayout(docId, layoutProfileId);
        } catch (Throwable ignore) {
            return false;
        }
    }

    @Override
    @NonNull
    public List<SidecarInkStroke> inkStrokesForPage(int pageIndex) {
        List<SidecarInkStroke> cached = inkCache.get(pageIndex);
        if (cached != null) return cached;
        List<SidecarInkStroke> loaded = store.listInk(docId, pageIndex, layoutProfileId);
        List<SidecarInkStroke> ro = Collections.unmodifiableList(loaded);
        inkCache.put(pageIndex, ro);
        return ro;
    }

    @Override
    @NonNull
    public List<SidecarHighlight> highlightsForPage(int pageIndex) {
        List<SidecarHighlight> cached = highlightCache.get(pageIndex);
        if (cached != null) return cached;
        List<SidecarHighlight> loaded = store.listHighlights(docId, pageIndex, layoutProfileId);
        List<SidecarHighlight> ro = Collections.unmodifiableList(loaded);
        highlightCache.put(pageIndex, ro);
        return ro;
    }

    @Override
    @NonNull
    public List<SidecarNote> notesForPage(int pageIndex) {
        List<SidecarNote> cached = noteCache.get(pageIndex);
        if (cached != null) return cached;
        List<SidecarNote> loaded = store.listNotes(docId, pageIndex, layoutProfileId);
        List<SidecarNote> ro = Collections.unmodifiableList(loaded);
        noteCache.put(pageIndex, ro);
        return ro;
    }

    @NonNull
    public List<SidecarInkStroke> addInkFromArcs(int pageIndex,
                                                 @NonNull PointF[][] arcs,
                                                 int color,
                                                 float thickness,
                                                 long createdAtEpochMs) {
        ArrayList<SidecarInkStroke> toInsert = new ArrayList<>();
        for (PointF[] arc : arcs) {
            if (arc == null || arc.length < 2) continue;
            String id = UUID.randomUUID().toString();
            toInsert.add(new SidecarInkStroke(id, pageIndex, layoutProfileId, color, thickness, createdAtEpochMs, arc));
        }
        if (!toInsert.isEmpty()) {
            store.insertInk(docId, toInsert);
            recordAnnotatedLayoutIfPossible();
            // Replace cached list with a new copy that includes the insertions.
            List<SidecarInkStroke> current = new ArrayList<>(inkStrokesForPage(pageIndex));
            current.addAll(toInsert);
            inkCache.put(pageIndex, Collections.unmodifiableList(current));
        }
        return toInsert;
    }

    public void recordUndoInkAdded(int pageIndex, @NonNull List<SidecarInkStroke> inserted) {
        if (inserted.isEmpty()) return;
        ArrayList<String> ids = new ArrayList<>();
        for (SidecarInkStroke s : inserted) {
            if (s != null && s.id != null) ids.add(s.id);
        }
        if (ids.isEmpty()) return;
        undoStack.push(() -> {
            for (String id : ids) {
                if (id == null) continue;
                removeInkStroke(pageIndex, id);
            }
        });
    }

    public void recordUndoInkReplaced(int pageIndex, @NonNull SidecarInkStroke original, @NonNull List<SidecarInkStroke> inserted) {
        ArrayList<String> insertedIds = new ArrayList<>();
        for (SidecarInkStroke s : inserted) {
            if (s != null && s.id != null) insertedIds.add(s.id);
        }
        undoStack.push(() -> {
            for (String id : insertedIds) {
                if (id == null) continue;
                removeInkStroke(pageIndex, id);
            }
            restoreInkStroke(original);
        });
    }

    @Nullable
    public SidecarInkStroke removeInkStroke(int pageIndex, @NonNull String strokeId) {
        List<SidecarInkStroke> current = new ArrayList<>(inkStrokesForPage(pageIndex));
        SidecarInkStroke removed = null;
        for (int i = 0; i < current.size(); i++) {
            SidecarInkStroke s = current.get(i);
            if (s != null && strokeId.equals(s.id)) {
                removed = s;
                current.remove(i);
                break;
            }
        }
        if (removed != null) {
            store.deleteInk(docId, strokeId);
            inkCache.put(pageIndex, Collections.unmodifiableList(current));
        }
        return removed;
    }

    public void restoreInkStroke(@NonNull SidecarInkStroke stroke) {
        store.insertInk(docId, java.util.Collections.singletonList(stroke));
        List<SidecarInkStroke> current = new ArrayList<>(inkStrokesForPage(stroke.pageIndex));
        current.add(stroke);
        inkCache.put(stroke.pageIndex, Collections.unmodifiableList(current));
    }

    @NonNull
    public SidecarHighlight addHighlight(int pageIndex,
                                         @NonNull Annotation.Type type,
                                         @NonNull PointF[] quadPoints,
                                         int color,
                                         float opacity,
                                         long createdAtEpochMs,
                                         @Nullable TextWord[][] pageTextLines,
                                         @Nullable String quote,
                                         float docProgress01) {
        String quotePrefix = null;
        String quoteSuffix = null;
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
                docProgress01);
        store.insertHighlight(docId, hl);
        recordAnnotatedLayoutIfPossible();
        List<SidecarHighlight> current = new ArrayList<>(highlightsForPage(pageIndex));
        current.add(hl);
        highlightCache.put(pageIndex, Collections.unmodifiableList(current));
        recordUndoHighlightAdded(hl);
        return hl;
    }

    public void recordUndoHighlightAdded(@NonNull SidecarHighlight highlight) {
        undoStack.push(() -> removeHighlight(highlight.pageIndex, highlight.id));
    }

    public void recordUndoHighlightDeleted(@NonNull SidecarHighlight highlight) {
        undoStack.push(() -> restoreHighlight(highlight));
    }

    @Nullable
    public SidecarHighlight removeHighlight(int pageIndex, @NonNull String highlightId) {
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

    public void restoreHighlight(@NonNull SidecarHighlight highlight) {
        store.insertHighlight(docId, highlight);
        List<SidecarHighlight> current = new ArrayList<>(highlightsForPage(highlight.pageIndex));
        current.add(highlight);
        highlightCache.put(highlight.pageIndex, Collections.unmodifiableList(current));
    }

    /**
     * Best-effort highlight re-anchoring for reflow docs after a relayout. This uses the stored
     * highlight {@code quote} text to search for the selection in the current layout and then
     * rewrites the highlight geometry for {@link #layoutProfileId()}.
     *
     * <p>This is intentionally conservative: it only updates highlights when a search hit is found,
     * leaving unmatched highlights in their original layout profile so the user can still switch
     * back to the annotated layout.</p>
     *
     * @return number of highlights re-anchored into the current layout.
     */
    public int reanchorHighlightsForCurrentLayout(@NonNull PageTextProvider pageText) {
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
                    h.docProgress01);
            store.insertHighlight(docId, reanchored);
            updated++;
        }

        if (updated > 0) {
            highlightCache.clear();
        }
        return updated;
    }

    @Nullable
    private static SearchHit findHighlightSearchHit(@NonNull PageTextProvider pageText,
                                                   @NonNull SidecarHighlight highlight,
                                                   int pageCount,
                                                   @NonNull String query) {
        int target = computeReanchorTargetPageIndex(highlight, pageCount);
        return searchAround(pageText, highlight, target, pageCount, query);
    }

    private static int computeReanchorTargetPageIndex(@NonNull SidecarHighlight h, int pageCount) {
        int max = Math.max(0, pageCount - 1);
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
        TextWord[][] lines = pageText.textLines(pageIndex);
        TextAnchorUtils.PageTextIndex index = TextAnchorUtils.buildIndex(lines);
        if (index.text.isEmpty()) return null;

        TextAnchorUtils.QuoteMatch match = TextAnchorUtils.bestMatchByContext(index, quote, highlight.quotePrefix, highlight.quoteSuffix);
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

    @NonNull
    public SidecarNote addNote(int pageIndex,
                               @NonNull RectF bounds,
                               @Nullable String text,
                               long createdAtEpochMs) {
        SidecarNote note = new SidecarNote(
                UUID.randomUUID().toString(),
                pageIndex,
                layoutProfileId,
                new RectF(bounds),
                text,
                createdAtEpochMs);
        store.insertNote(docId, note);
        recordAnnotatedLayoutIfPossible();
        List<SidecarNote> current = new ArrayList<>(notesForPage(pageIndex));
        current.add(note);
        noteCache.put(pageIndex, Collections.unmodifiableList(current));
        recordUndoNoteAdded(note);
        return note;
    }

    public void recordUndoNoteAdded(@NonNull SidecarNote note) {
        undoStack.push(() -> removeNote(note.pageIndex, note.id));
    }

    public void recordUndoNoteDeleted(@NonNull SidecarNote note) {
        undoStack.push(() -> restoreNote(note));
    }

    @Nullable
    public SidecarNote removeNote(int pageIndex, @NonNull String noteId) {
        List<SidecarNote> current = new ArrayList<>(notesForPage(pageIndex));
        SidecarNote removed = null;
        for (int i = 0; i < current.size(); i++) {
            SidecarNote n = current.get(i);
            if (n != null && noteId.equals(n.id)) {
                removed = n;
                current.remove(i);
                break;
            }
        }
        if (removed != null) {
            store.deleteNote(docId, noteId);
            noteCache.put(pageIndex, Collections.unmodifiableList(current));
        }
        return removed;
    }

    public void restoreNote(@NonNull SidecarNote note) {
        store.insertNote(docId, note);
        List<SidecarNote> current = new ArrayList<>(notesForPage(note.pageIndex));
        current.add(note);
        noteCache.put(note.pageIndex, Collections.unmodifiableList(current));
    }

    private void recordAnnotatedLayoutIfPossible() {
        if (layoutProfileId == null) return;
        ReflowPrefsStore store = reflowPrefsStore;
        ReflowPrefsSnapshot snap = reflowPrefsSnapshot;
        if (store == null || snap == null) return;
        try {
            android.graphics.PointF pageSize = parsePageSizeFromLayoutProfileId(layoutProfileId);
            if (pageSize == null || pageSize.x <= 0f || pageSize.y <= 0f) return;
            store.saveAnnotatedLayout(docId, new org.opendroidpdf.app.reflow.ReflowAnnotatedLayout(
                    snap,
                    pageSize.x / 2f,
                    pageSize.y / 2f));
        } catch (Throwable ignore) {
        }
    }

    @Nullable
    private static android.graphics.PointF parsePageSizeFromLayoutProfileId(@NonNull String layoutProfileId) {
        Matcher m = LAYOUT_ID_PAGE_SIZE.matcher(layoutProfileId);
        if (!m.find()) return null;
        try {
            int w10 = Integer.parseInt(m.group(1));
            int h10 = Integer.parseInt(m.group(2));
            return new android.graphics.PointF(w10 / 10f, h10 / 10f);
        } catch (Throwable t) {
            return null;
        }
    }
}
