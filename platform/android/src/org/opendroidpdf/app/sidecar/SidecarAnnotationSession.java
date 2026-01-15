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
import java.util.UUID;
import java.io.OutputStream;

/**
 * Document-scoped in-memory view of sidecar annotations with a backing store.
 *
 * <p>This acts as the "single place to ask" for overlay-rendered annotations for formats that
 * cannot (or should not) be modified in-place (EPUB, or PDFs without write access).</p>
 */
public final class SidecarAnnotationSession implements SidecarAnnotationProvider {
    private final String docId;
    @Nullable private final String layoutProfileId;
    private final SidecarAnnotationStore store;
    @Nullable private final ReflowPrefsStore reflowPrefsStore;
    @Nullable private final ReflowPrefsSnapshot reflowPrefsSnapshot;

    private final ArrayDeque<UndoOp> undoStack = new ArrayDeque<>();
    private final ArrayDeque<UndoOp> redoStack = new ArrayDeque<>();

    private final Map<Integer, List<SidecarInkStroke>> inkCache = new HashMap<>();
    private final Map<Integer, List<SidecarHighlight>> highlightCache = new HashMap<>();
    private final Map<Integer, List<SidecarNote>> noteCache = new HashMap<>();

    public interface UndoOp {
        void undo();
        void redo();
    }

    private static final class DualOp implements UndoOp {
        private final Runnable undo;
        private final Runnable redo;

        DualOp(@NonNull Runnable undo, @NonNull Runnable redo) {
            this.undo = undo;
            this.redo = redo;
        }

        @Override public void undo() { undo.run(); }
        @Override public void redo() { redo.run(); }
    }

    private void pushUndo(@NonNull UndoOp op) {
        undoStack.push(op);
        redoStack.clear();
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

    /**
     * Exports all sidecar annotations for this document across layouts as a JSON bundle.
     *
     * <p>Intended for backup/sync. This does not include pending (uncommitted) ink.</p>
     */
    public void writeBundleJson(@NonNull OutputStream outputStream) throws Exception {
        SidecarBundleJson.writeBundleJson(docId, store, outputStream);
    }
    /**
     * Imports the provided bundle into this session's document (ignores {@link SidecarBundleJson.SidecarBundle#docId}).
     */
    @NonNull
    public SidecarBundleJson.ImportStats importBundleIntoThisDoc(@NonNull SidecarBundleJson.SidecarBundle bundle) {
        SidecarBundleJson.ImportStats stats = SidecarBundleJson.importIntoDoc(docId, store, bundle);
        if (stats.total() == 0) return stats;

        // Drop any cached per-page results so the next draw/query picks up imported rows.
        inkCache.clear();
        highlightCache.clear();
        noteCache.clear();
        undoStack.clear();
        redoStack.clear();

        SidecarReflowUtils.recordAnnotatedLayoutIfPossible(docId, layoutProfileId, reflowPrefsStore, reflowPrefsSnapshot);
        return stats;
    }

    public boolean hasUndo() { return !undoStack.isEmpty(); }

    public boolean hasRedo() { return !redoStack.isEmpty(); }

    public boolean undoLast() {
        UndoOp op = undoStack.poll();
        if (op == null) return false;
        op.undo();
        redoStack.push(op);
        return true;
    }

    public boolean redoLast() {
        UndoOp op = redoStack.poll();
        if (op == null) return false;
        op.redo();
        undoStack.push(op);
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
            SidecarReflowUtils.recordAnnotatedLayoutIfPossible(docId, layoutProfileId, reflowPrefsStore, reflowPrefsSnapshot);
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
        pushUndo(new DualOp(
                () -> {
                    for (String id : ids) {
                        if (id == null) continue;
                        removeInkStroke(pageIndex, id);
                    }
                },
                () -> {
                    for (SidecarInkStroke s : inserted) {
                        if (s == null) continue;
                        restoreInkStroke(s);
                    }
                }
        ));
    }

    public void recordUndoInkReplaced(int pageIndex, @NonNull SidecarInkStroke original, @NonNull List<SidecarInkStroke> inserted) {
        ArrayList<String> insertedIds = new ArrayList<>();
        for (SidecarInkStroke s : inserted) {
            if (s != null && s.id != null) insertedIds.add(s.id);
        }
        pushUndo(new DualOp(
                () -> {
                    for (String id : insertedIds) {
                        if (id == null) continue;
                        removeInkStroke(pageIndex, id);
                    }
                    restoreInkStroke(original);
                },
                () -> {
                    if (original != null && original.id != null) {
                        removeInkStroke(pageIndex, original.id);
                    }
                    for (SidecarInkStroke s : inserted) {
                        if (s == null) continue;
                        restoreInkStroke(s);
                    }
                }
        ));
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
        recordUndoHighlightAdded(hl);
        return hl;
    }

    public void recordUndoHighlightAdded(@NonNull SidecarHighlight highlight) {
        pushUndo(new DualOp(
                () -> removeHighlight(highlight.pageIndex, highlight.id),
                () -> restoreHighlight(highlight)
        ));
    }

    public void recordUndoHighlightDeleted(@NonNull SidecarHighlight highlight) {
        pushUndo(new DualOp(
                () -> restoreHighlight(highlight),
                () -> removeHighlight(highlight.pageIndex, highlight.id)
        ));
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

    /** Best-effort highlight re-anchoring for reflow docs after a relayout. */
    public int reanchorHighlightsForCurrentLayout(@NonNull SidecarHighlightReanchorer.PageTextProvider pageText) {
        String layout = layoutProfileId;
        if (layout == null) return 0;
        int updated = SidecarHighlightReanchorer.reanchorHighlightsForCurrentLayout(docId, layout, store, pageText);
        if (updated > 0) {
            highlightCache.clear();
        }
        return updated;
    }

    @NonNull
    public SidecarNote addNote(int pageIndex,
                               @NonNull RectF bounds,
                               @Nullable String text,
                               long createdAtEpochMs) {
        float fontSize = (bounds.height()) * 0.18f;
        fontSize = Math.max(10.0f, Math.min(18.0f, fontSize));
        SidecarNote note = new SidecarNote(
                UUID.randomUUID().toString(),
                pageIndex,
                layoutProfileId,
                new RectF(bounds),
                text,
                createdAtEpochMs,
                SidecarNote.DEFAULT_COLOR,
                SidecarNote.DEFAULT_FONT_FAMILY,
                fontSize);
        store.insertNote(docId, note);
        SidecarReflowUtils.recordAnnotatedLayoutIfPossible(docId, layoutProfileId, reflowPrefsStore, reflowPrefsSnapshot);
        List<SidecarNote> current = new ArrayList<>(notesForPage(pageIndex));
        current.add(note);
        noteCache.put(pageIndex, Collections.unmodifiableList(current));
        recordUndoNoteAdded(note);
        return note;
    }

    public void recordUndoNoteAdded(@NonNull SidecarNote note) {
        pushUndo(new DualOp(
                () -> removeNote(note.pageIndex, note.id),
                () -> restoreNote(note)
        ));
    }

    public void recordUndoNoteDeleted(@NonNull SidecarNote note) {
        pushUndo(new DualOp(
                () -> restoreNote(note),
                () -> removeNote(note.pageIndex, note.id)
        ));
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

    @Nullable
    public SidecarNote updateNoteBounds(int pageIndex, @NonNull String noteId, @NonNull RectF bounds) {
        return updateNoteBounds(pageIndex, noteId, bounds, false);
    }

    @Nullable
    public SidecarNote updateNoteBounds(int pageIndex, @NonNull String noteId, @NonNull RectF bounds, boolean markUserResized) {
        if (bounds == null) return null;
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        boolean userResized = prior.userResized || markUserResized;
        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    public SidecarNote updateNoteText(int pageIndex, @NonNull String noteId, @Nullable String text) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    public SidecarNote updateNoteStyle(int pageIndex, @NonNull String noteId, int color, float fontSize) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                color,
                prior.fontFamily,
                prior.fontStyleFlags,
                fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    public SidecarNote updateNoteFontFamily(int pageIndex, @NonNull String noteId, int fontFamily) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        int fam = fontFamily;
        if (fam < 0 || fam > 2) fam = SidecarNote.DEFAULT_FONT_FAMILY;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                fam,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    public SidecarNote updateNoteFontStyleFlags(int pageIndex, @NonNull String noteId, int fontStyleFlags) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        int flags = fontStyleFlags & 0x0F;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                flags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    public SidecarNote updateNoteParagraph(int pageIndex, @NonNull String noteId, float lineHeight, float textIndentPt) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                lineHeight,
                textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    public SidecarNote updateNoteBackground(int pageIndex, @NonNull String noteId, int backgroundColor, float backgroundOpacity) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        // Clamp opacity to a sane range; color is stored as-is (ARGB).
        float opacity = Math.max(0.0f, Math.min(1.0f, backgroundOpacity));
        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                backgroundColor,
                opacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    public SidecarNote updateNoteBorder(int pageIndex,
                                        @NonNull String noteId,
                                        int borderColor,
                                        float borderWidthPt,
                                        boolean dashed,
                                        float borderRadiusPt) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        float width = Math.max(0.0f, Math.min(24.0f, borderWidthPt));
        float radius = Math.max(0.0f, Math.min(48.0f, borderRadiusPt));
        int style = dashed ? 1 : 0;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                borderColor,
                width,
                style,
                radius,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    public SidecarNote updateNoteLocks(int pageIndex,
                                       @NonNull String noteId,
                                       boolean lockPositionSize,
                                       boolean lockContents) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                lockPositionSize,
                lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    public SidecarNote updateNoteRotation(int pageIndex,
                                          @NonNull String noteId,
                                          int rotationDeg) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        if (rotationDeg < 0 || rotationDeg >= 360) {
            rotationDeg %= 360;
            if (rotationDeg < 0) rotationDeg += 360;
        }
        int snapped = ((rotationDeg + 45) / 90) * 90;
        if (snapped >= 360) snapped = 0;
        rotationDeg = snapped;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    public void restoreNote(@NonNull SidecarNote note) {
        store.insertNote(docId, note);
        putNoteInCache(note);
    }

    private void recordUndoNoteUpdated(@NonNull SidecarNote prior, @NonNull SidecarNote updated) {
        pushUndo(new DualOp(
                () -> {
                    store.insertNote(docId, prior);
                    putNoteInCache(prior);
                },
                () -> {
                    store.insertNote(docId, updated);
                    putNoteInCache(updated);
                }
        ));
    }

    @Nullable
    private SidecarNote findNote(int pageIndex, @NonNull String noteId) {
        List<SidecarNote> current = notesForPage(pageIndex);
        if (current == null || current.isEmpty()) return null;
        for (SidecarNote n : current) {
            if (n != null && noteId.equals(n.id)) return n;
        }
        return null;
    }

    private void putNoteInCache(@NonNull SidecarNote note) {
        List<SidecarNote> current = new ArrayList<>(notesForPage(note.pageIndex));
        boolean replaced = false;
        for (int i = 0; i < current.size(); i++) {
            SidecarNote n = current.get(i);
            if (n != null && note.id.equals(n.id)) {
                current.set(i, note);
                replaced = true;
                break;
            }
        }
        if (!replaced) current.add(note);
        noteCache.put(note.pageIndex, Collections.unmodifiableList(current));
    }
}
