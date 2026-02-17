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

    private final SidecarAnnotationUndo undo = new SidecarAnnotationUndo();

    private final SidecarInkOps inkOps;
    private final SidecarHighlightOps highlightOps;
    private final SidecarNoteOps noteOps;

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

        inkOps = new SidecarInkOps(docId, layoutProfileId, store, reflowPrefsStore, reflowPrefsSnapshot);
        highlightOps = new SidecarHighlightOps(docId, layoutProfileId, store, reflowPrefsStore, reflowPrefsSnapshot);
        noteOps = new SidecarNoteOps(docId, layoutProfileId, store, undo, reflowPrefsStore, reflowPrefsSnapshot);
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
        inkOps.clearCache();
        highlightOps.clearCache();
        noteOps.clearCache();
        undo.clear();

        SidecarReflowUtils.recordAnnotatedLayoutIfPossible(docId, layoutProfileId, reflowPrefsStore, reflowPrefsSnapshot);
        return stats;
    }

    public boolean hasUndo() { return undo.hasUndo(); }

    public boolean hasRedo() { return undo.hasRedo(); }

    public boolean undoLast() { return undo.undoLast(); }

    public boolean redoLast() { return undo.redoLast(); }

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
        return inkOps.inkStrokesForPage(pageIndex);
    }

    @Override
    @NonNull
    public List<SidecarHighlight> highlightsForPage(int pageIndex) {
        return highlightOps.highlightsForPage(pageIndex);
    }

    @Override
    @NonNull
    public List<SidecarNote> notesForPage(int pageIndex) {
        return noteOps.notesForPage(pageIndex);
    }

    @NonNull
    public List<SidecarInkStroke> addInkFromArcs(int pageIndex,
                                                 @NonNull PointF[][] arcs,
                                                 int color,
                                                 float thickness,
                                                 long createdAtEpochMs) {
        return inkOps.addInkFromArcs(pageIndex, arcs, color, thickness, createdAtEpochMs);
    }

    public void recordUndoInkAdded(int pageIndex, @NonNull List<SidecarInkStroke> inserted) {
        SidecarAnnotationUndoOps.recordUndoInkAdded(undo, this, pageIndex, inserted);
    }

    public void recordUndoInkDeleted(int pageIndex, @NonNull List<SidecarInkStroke> removed) {
        SidecarAnnotationUndoOps.recordUndoInkDeleted(undo, this, pageIndex, removed);
    }

    public void recordUndoInkUpdated(int pageIndex, @NonNull List<SidecarInkStroke> original, @NonNull List<SidecarInkStroke> updated) {
        SidecarAnnotationUndoOps.recordUndoInkUpdated(undo, this, pageIndex, original, updated);
    }

    /**
     * Records an undo entry for an ink operation that changes page index (cross-page move).
     *
     * <p>This is implemented as a pair of multi-page upserts so stroke ids remain stable.</p>
     */
    public void recordUndoInkMoved(@NonNull List<SidecarInkStroke> original, @NonNull List<SidecarInkStroke> updated) {
        if (original.isEmpty() || updated.isEmpty()) return;
        undo.pushDual(
                () -> upsertInkStrokesAnyPage(original),
                () -> upsertInkStrokesAnyPage(updated)
        );
    }

    public void recordUndoInkReplaced(int pageIndex, @NonNull SidecarInkStroke original, @NonNull List<SidecarInkStroke> inserted) {
        SidecarAnnotationUndoOps.recordUndoInkReplaced(undo, this, pageIndex, original, inserted);
    }

    @Nullable
    public SidecarInkStroke removeInkStroke(int pageIndex, @NonNull String strokeId) {
        return inkOps.removeInkStroke(pageIndex, strokeId);
    }

    public void restoreInkStroke(@NonNull SidecarInkStroke stroke) {
        inkOps.restoreInkStroke(stroke);
    }

    public void upsertInkStrokes(int pageIndex, @NonNull List<SidecarInkStroke> strokes) {
        inkOps.upsertInkStrokes(pageIndex, strokes);
    }

    /** Upserts ink strokes that may span multiple pages (e.g., cross-page move). */
    public void upsertInkStrokesAnyPage(@NonNull List<SidecarInkStroke> strokes) {
        inkOps.upsertInkStrokesAnyPage(strokes);
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
        SidecarHighlight hl = highlightOps.addHighlight(
                pageIndex,
                type,
                quadPoints,
                color,
                opacity,
                createdAtEpochMs,
                reflowLocation,
                pageTextLines,
                quote,
                docProgress01);
        recordUndoHighlightAdded(hl);
        return hl;
    }

    public void recordUndoHighlightAdded(@NonNull SidecarHighlight highlight) {
        SidecarAnnotationUndoOps.recordUndoHighlightAdded(undo, this, highlight);
    }

    public void recordUndoHighlightDeleted(@NonNull SidecarHighlight highlight) {
        SidecarAnnotationUndoOps.recordUndoHighlightDeleted(undo, this, highlight);
    }

    @Nullable
    public SidecarHighlight removeHighlight(int pageIndex, @NonNull String highlightId) {
        return highlightOps.removeHighlight(pageIndex, highlightId);
    }

    public void restoreHighlight(@NonNull SidecarHighlight highlight) {
        highlightOps.restoreHighlight(highlight);
    }

    /** Best-effort highlight re-anchoring for reflow docs after a relayout. */
    public int reanchorHighlightsForCurrentLayout(@NonNull SidecarHighlightReanchorer.PageTextProvider pageText) {
        return highlightOps.reanchorHighlightsForCurrentLayout(pageText);
    }

    @NonNull
    public SidecarNote addNote(int pageIndex,
                               @NonNull RectF bounds,
                               @Nullable String text,
                               long createdAtEpochMs) {
        SidecarNote note = noteOps.addNote(pageIndex, bounds, text, createdAtEpochMs);
        recordUndoNoteAdded(note);
        return note;
    }

    public void recordUndoNoteAdded(@NonNull SidecarNote note) {
        SidecarAnnotationUndoOps.recordUndoNoteAdded(undo, this, note);
    }

    public void recordUndoNoteDeleted(@NonNull SidecarNote note) {
        SidecarAnnotationUndoOps.recordUndoNoteDeleted(undo, this, note);
    }

    @Nullable
    public SidecarNote removeNote(int pageIndex, @NonNull String noteId) {
        return noteOps.removeNote(pageIndex, noteId);
    }

    @Nullable
    public SidecarNote updateNoteBounds(int pageIndex, @NonNull String noteId, @NonNull RectF bounds) {
        return noteOps.updateNoteBounds(pageIndex, noteId, bounds);
    }

    @Nullable
    public SidecarNote updateNoteBounds(int pageIndex, @NonNull String noteId, @NonNull RectF bounds, boolean markUserResized) {
        return noteOps.updateNoteBounds(pageIndex, noteId, bounds, markUserResized);
    }

    /**
     * Moves a note to another page (preserves id) and records an undo entry.
     *
     * <p>This is used for cross-page drag-move in continuous scrolling mode.</p>
     */
    @Nullable
    public SidecarNote moveNoteToPage(int fromPageIndex,
                                      int toPageIndex,
                                      @NonNull String noteId,
                                      @NonNull RectF bounds,
                                      boolean markUserResized) {
        return noteOps.moveNoteToPage(fromPageIndex, toPageIndex, noteId, bounds, markUserResized);
    }

    @Nullable
    public SidecarNote updateNoteText(int pageIndex, @NonNull String noteId, @Nullable String text) {
        return noteOps.updateNoteText(pageIndex, noteId, text);
    }

    @Nullable
    public SidecarNote updateNoteStyle(int pageIndex, @NonNull String noteId, int color, float fontSize) {
        return noteOps.updateNoteStyle(pageIndex, noteId, color, fontSize);
    }

    @Nullable
    public SidecarNote updateNoteFontFamily(int pageIndex, @NonNull String noteId, int fontFamily) {
        return noteOps.updateNoteFontFamily(pageIndex, noteId, fontFamily);
    }

    @Nullable
    public SidecarNote updateNoteFontStyleFlags(int pageIndex, @NonNull String noteId, int fontStyleFlags) {
        return noteOps.updateNoteFontStyleFlags(pageIndex, noteId, fontStyleFlags);
    }

    @Nullable
    public SidecarNote updateNoteParagraph(int pageIndex, @NonNull String noteId, float lineHeight, float textIndentPt) {
        return noteOps.updateNoteParagraph(pageIndex, noteId, lineHeight, textIndentPt);
    }

    @Nullable
    public SidecarNote updateNoteBackground(int pageIndex, @NonNull String noteId, int backgroundColor, float backgroundOpacity) {
        return noteOps.updateNoteBackground(pageIndex, noteId, backgroundColor, backgroundOpacity);
    }

    @Nullable
    public SidecarNote updateNoteBorder(int pageIndex,
                                        @NonNull String noteId,
                                        int borderColor,
                                        float borderWidthPt,
                                        boolean dashed,
                                        float borderRadiusPt) {
        return noteOps.updateNoteBorder(pageIndex, noteId, borderColor, borderWidthPt, dashed, borderRadiusPt);
    }

    @Nullable
    public SidecarNote updateNoteLocks(int pageIndex,
                                       @NonNull String noteId,
                                       boolean lockPositionSize,
                                       boolean lockContents) {
        return noteOps.updateNoteLocks(pageIndex, noteId, lockPositionSize, lockContents);
    }

    @Nullable
    public SidecarNote updateNoteRotation(int pageIndex,
                                          @NonNull String noteId,
                                          int rotationDeg) {
        return noteOps.updateNoteRotation(pageIndex, noteId, rotationDeg);
    }

    public void restoreNote(@NonNull SidecarNote note) {
        noteOps.restoreNote(note);
    }
}
