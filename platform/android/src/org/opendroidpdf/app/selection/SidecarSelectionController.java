package org.opendroidpdf.app.selection;

import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.util.List;

/**
 * Page-scoped controller that owns sidecar selection/hit-testing behavior for a single page view.
 *
 * <p>Kept separate from {@code MuPDFPageView} so the view can stay focused on rendering/layout while
 * selection rules and sidecar-specific behavior live in one place.</p>
 */
public final class SidecarSelectionController {

    public interface Host {
        @Nullable SidecarAnnotationSession sidecarSessionOrNull();

        int pageNumber();

        /** Whether sidecar annotations should be interactable (tappable). */
        boolean commentsVisible();

        float scale();

        int viewLeft();

        int viewTop();

        void setItemSelectBox(@Nullable RectF rect);

        void forwardTextAnnotation(@NonNull Annotation annotation);
    }

    public enum Kind { NOTE, HIGHLIGHT, INK }

    public static final class Selection {
        public final Kind kind;
        @NonNull public final String id;
        @NonNull public final RectF bounds;
        public final long createdAtEpochMs;

        public Selection(@NonNull Kind kind, @NonNull String id, @NonNull RectF bounds, long createdAtEpochMs) {
            this.kind = kind;
            this.id = id;
            this.bounds = bounds;
            this.createdAtEpochMs = createdAtEpochMs;
        }
    }

    private final Host host;
    @Nullable private Selection selection;
    private boolean stickyNotesOnly = false;

    public SidecarSelectionController(@NonNull Host host) {
        this.host = host;
    }

    public boolean hasSelection() { return selection != null; }

    @Nullable
    public Selection selectionOrNull() { return selection; }

    public boolean isSelectionEditable() {
        Selection sel = selection;
        return sel != null && (sel.kind == Kind.NOTE || sel.kind == Kind.INK);
    }

    /**
     * Selects an ink stroke group by {@code createdAtEpochMs} on the current page.
     *
     * <p>Fill & Sign places signatures as sidecar ink strokes on non-writable PDFs. Selecting the
     * freshly inserted group makes resize/delete immediately available.</p>
     */
    public boolean selectInkGroupByCreatedAt(long createdAtEpochMs) {
        if (createdAtEpochMs <= 0L) return false;
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        if (!host.commentsVisible()) return false;

        List<SidecarInkStroke> strokes = sidecar.inkStrokesForPage(host.pageNumber());
        if (strokes == null || strokes.isEmpty()) return false;

        RectF group = null;
        for (SidecarInkStroke s : strokes) {
            if (s == null || s.points == null || s.points.length < 2) continue;
            if (s.createdAtEpochMs != createdAtEpochMs) continue;
            RectF r = boundsForPointsOrNull(s.points);
            if (r == null) continue;
            if (group == null) group = new RectF(r);
            else group.union(r);
        }
        if (group == null) return false;

        Selection sel = new Selection(Kind.INK, "ink:" + createdAtEpochMs, group, createdAtEpochMs);
        selection = sel;
        host.setItemSelectBox(new RectF(sel.bounds));
        return true;
    }

    /** When enabled, sidecar notes are selectable only via their marker icon (sticky-note mode). */
    public void setStickyNotesOnly(boolean enabled) {
        stickyNotesOnly = enabled;
    }

    /**
     * Attempts to edit the current sidecar selection.
     *
     * @return {@code true} if the selection was handled (and an editor was requested).
     */
    public boolean editSelected() {
        Selection sel = selection;
        if (sel == null) return false;
        if (sel.kind != Kind.NOTE) return false;
        maybeShowSidecarNoteEditor(sel.id);
        return true;
    }

    /** Selects a sidecar note by id (no UI side-effects). */
    public boolean selectNoteById(@NonNull String noteId) {
        if (noteId == null || noteId.trim().isEmpty()) return false;
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        List<SidecarNote> notes;
        try {
            notes = sidecar.notesForPage(host.pageNumber());
        } catch (Throwable ignore) {
            return false;
        }
        if (notes == null || notes.isEmpty()) return false;
        for (SidecarNote n : notes) {
            if (n == null || n.id == null || n.bounds == null) continue;
            if (!noteId.equals(n.id)) continue;
            Selection sel = new Selection(Kind.NOTE, n.id, new RectF(n.bounds), -1L);
            selection = sel;
            host.setItemSelectBox(new RectF(sel.bounds));
            return true;
        }
        return false;
    }

    /** Selects a sidecar highlight by id (no UI side-effects). */
    public boolean selectHighlightById(@NonNull String highlightId) {
        if (highlightId == null || highlightId.trim().isEmpty()) return false;
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        List<SidecarHighlight> highlights;
        try {
            highlights = sidecar.highlightsForPage(host.pageNumber());
        } catch (Throwable ignore) {
            return false;
        }
        if (highlights == null || highlights.isEmpty()) return false;
        for (SidecarHighlight h : highlights) {
            if (h == null || h.id == null || h.quadPoints == null || h.quadPoints.length < 4) continue;
            if (!highlightId.equals(h.id)) continue;
            RectF bounds = null;
            int n = h.quadPoints.length - (h.quadPoints.length % 4);
            for (int i = 0; i < n; i += 4) {
                RectF r = quadRect(h.quadPoints, i);
                if (r == null) continue;
                if (bounds == null) bounds = new RectF(r);
                else bounds.union(r);
            }
            if (bounds == null) return false;
            Selection sel = new Selection(Kind.HIGHLIGHT, h.id, bounds, -1L);
            selection = sel;
            host.setItemSelectBox(new RectF(sel.bounds));
            return true;
        }
        return false;
    }

    /**
     * Deletes the current sidecar selection from the backing store and records an undo entry.
     *
     * @return {@code true} if a sidecar selection existed (even if the backing store had already pruned it).
     */
    public boolean deleteSelected() {
        Selection sel = selection;
        if (sel == null) return false;
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;

        try {
            switch (sel.kind) {
                case NOTE: {
                    SidecarNote removed = sidecar.removeNote(host.pageNumber(), sel.id);
                    if (removed != null) sidecar.recordUndoNoteDeleted(removed);
                    break;
                }
                case HIGHLIGHT: {
                    SidecarHighlight removed = sidecar.removeHighlight(host.pageNumber(), sel.id);
                    if (removed != null) sidecar.recordUndoHighlightDeleted(removed);
                    break;
                }
                case INK: {
                    long createdAt = sel.createdAtEpochMs;
                    if (createdAt <= 0L) break;
                    List<SidecarInkStroke> strokes = sidecar.inkStrokesForPage(host.pageNumber());
                    if (strokes == null || strokes.isEmpty()) break;
                    java.util.ArrayList<SidecarInkStroke> removed = new java.util.ArrayList<>();
                    for (SidecarInkStroke s : strokes) {
                        if (s == null || s.id == null) continue;
                        if (s.createdAtEpochMs != createdAt) continue;
                        SidecarInkStroke r = sidecar.removeInkStroke(host.pageNumber(), s.id);
                        if (r != null) removed.add(r);
                    }
                    if (!removed.isEmpty()) sidecar.recordUndoInkDeleted(host.pageNumber(), removed);
                    break;
                }
            }
        } catch (Throwable ignore) {
            // Best-effort: keep UI consistent even if backing store throws.
        }

        clearSelection();
        return true;
    }

    /** Clears selection state and clears the view selection box. */
    public void clearSelection() {
        if (selection == null) return;
        selection = null;
        host.setItemSelectBox(null);
    }

    /**
     * Clears selection state only, preserving any selection box populated by another system
     * (e.g., embedded PDF hit-testing).
     */
    public void clearSelectionStateOnly() {
        selection = null;
    }

    /** Updates the current selection bounds if the selected id matches. */
    public void updateSelectionBounds(@NonNull String id, @NonNull RectF bounds) {
        Selection sel = selection;
        if (sel == null) return;
        if (!id.equals(sel.id)) return;
        selection = new Selection(sel.kind, sel.id, new RectF(bounds), sel.createdAtEpochMs);
        host.setItemSelectBox(new RectF(bounds));
    }

    /** Returns whether a tap would hit a sidecar annotation without mutating selection state. */
    public boolean wouldHit(@Nullable MotionEvent e) {
        return findHit(e) != null;
    }

    /**
     * Handles a tap: selects the sidecar annotation (if hit) or clears selection otherwise.
     *
     * @return the selected annotation, or {@code null} if none hit.
     */
    @Nullable
    public Selection handleTap(@Nullable MotionEvent e) {
        Selection prior = selection;
        Selection hit = findHit(e);
        if (hit == null) {
            if (prior != null) clearSelection();
            return null;
        }

        selection = hit;
        host.setItemSelectBox(new RectF(hit.bounds));

        // Keep single-tap as "select" so toolbar delete works. If the user taps the same note
        // again, open the note text editor.
        if (hit.kind == Kind.NOTE && prior != null && prior.kind == Kind.NOTE && hit.id.equals(prior.id)) {
            maybeShowSidecarNoteEditor(hit.id);
        }
        return hit;
    }

    private void maybeShowSidecarNoteEditor(@NonNull String noteId) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return;

        final List<SidecarNote> notes;
        try {
            notes = sidecar.notesForPage(host.pageNumber());
        } catch (Throwable ignore) {
            return;
        }
        if (notes == null || notes.isEmpty()) return;

        SidecarNote match = null;
        for (SidecarNote n : notes) {
            if (n != null && noteId.equals(n.id)) {
                match = n;
                break;
            }
        }
        if (match == null || match.bounds == null) return;

        // Reuse the existing "edit text annotation" dialog: it deletes the selected annotation
        // and re-adds it with the updated text. For sidecar notes, deleteSelectedAnnotation()
        // routes to the sidecar store when a sidecar selection exists.
        Annotation pseudo = new Annotation(
                match.bounds.left,
                match.bounds.top,
                match.bounds.right,
                match.bounds.bottom,
                Annotation.Type.TEXT,
                null,
                match.text);
        try {
            host.forwardTextAnnotation(pseudo);
        } catch (Throwable ignore) {
        }
    }

    @Nullable
    private Selection findHit(@Nullable MotionEvent e) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null || e == null) return null;
        if (!host.commentsVisible()) return null;

        final float scale = host.scale();
        if (scale == 0f) return null;
        final float docRelX = (e.getX() - host.viewLeft()) / scale;
        final float docRelY = (e.getY() - host.viewTop()) / scale;

        // Prefer note markers (small/tap-target) over broad highlight rects.
        Selection noteHit = hitTestNotes(sidecar, docRelX, docRelY, scale);
        if (noteHit != null) return noteHit;

        Selection inkHit = hitTestInk(sidecar, docRelX, docRelY);
        if (inkHit != null) return inkHit;

        return hitTestHighlights(sidecar, docRelX, docRelY);
    }

    @Nullable
    private Selection hitTestNotes(@NonNull SidecarAnnotationSession sidecar, float docRelX, float docRelY, float scale) {
        List<SidecarNote> notes = sidecar.notesForPage(host.pageNumber());
        if (notes == null || notes.isEmpty()) return null;
        for (SidecarNote n : notes) {
            if (n == null || n.id == null || n.bounds == null) continue;
            RectF marker = noteMarkerRectDoc(n.bounds, scale);
            if (marker != null && marker.contains(docRelX, docRelY)) {
                return new Selection(Kind.NOTE, n.id, new RectF(n.bounds), -1L);
            }
            if (!stickyNotesOnly && n.bounds.contains(docRelX, docRelY)) {
                return new Selection(Kind.NOTE, n.id, new RectF(n.bounds), -1L);
            }
        }
        return null;
    }

    @Nullable
    private Selection hitTestInk(@NonNull SidecarAnnotationSession sidecar, float docRelX, float docRelY) {
        List<SidecarInkStroke> strokes = sidecar.inkStrokesForPage(host.pageNumber());
        if (strokes == null || strokes.isEmpty()) return null;

        // Iterate newest-first so taps prefer the most recently placed stroke group.
        for (int i = strokes.size() - 1; i >= 0; i--) {
            SidecarInkStroke s = strokes.get(i);
            if (s == null || s.points == null || s.points.length < 2) continue;
            RectF strokeBounds = boundsForPointsOrNull(s.points);
            if (strokeBounds == null || !strokeBounds.contains(docRelX, docRelY)) continue;

            long createdAt = s.createdAtEpochMs;
            if (createdAt <= 0L) continue;
            RectF group = null;
            for (SidecarInkStroke other : strokes) {
                if (other == null || other.points == null || other.points.length < 2) continue;
                if (other.createdAtEpochMs != createdAt) continue;
                RectF r = boundsForPointsOrNull(other.points);
                if (r == null) continue;
                if (group == null) group = new RectF(r);
                else group.union(r);
            }
            if (group == null) return null;
            String id = "ink:" + createdAt;
            return new Selection(Kind.INK, id, group, createdAt);
        }
        return null;
    }

    @Nullable
    private Selection hitTestHighlights(@NonNull SidecarAnnotationSession sidecar, float docRelX, float docRelY) {
        List<SidecarHighlight> highlights = sidecar.highlightsForPage(host.pageNumber());
        if (highlights == null || highlights.isEmpty()) return null;
        for (SidecarHighlight h : highlights) {
            if (h == null || h.id == null || h.quadPoints == null || h.quadPoints.length < 4) continue;
            RectF union = null;
            boolean hit = false;
            int n = h.quadPoints.length - (h.quadPoints.length % 4);
            for (int i = 0; i < n; i += 4) {
                RectF r = quadRect(h.quadPoints, i);
                if (r == null) continue;
                if (union == null) union = new RectF(r);
                else union.union(r);
                if (r.contains(docRelX, docRelY)) {
                    hit = true;
                }
            }
            if (hit && union != null) {
                return new Selection(Kind.HIGHLIGHT, h.id, union, -1L);
            }
        }
        return null;
    }

    @Nullable
    private static RectF quadRect(PointF[] points, int start) {
        if (points == null || points.length < start + 4) return null;
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (int j = 0; j < 4; j++) {
            PointF p = points[start + j];
            if (p == null) continue;
            if (p.x < left) left = p.x;
            if (p.y < top) top = p.y;
            if (p.x > right) right = p.x;
            if (p.y > bottom) bottom = p.y;
        }
        if (Float.isNaN(left) || Float.isInfinite(left)
                || Float.isNaN(top) || Float.isInfinite(top)
                || Float.isNaN(right) || Float.isInfinite(right)
                || Float.isNaN(bottom) || Float.isInfinite(bottom)) {
            return null;
        }
        if (right <= left || bottom <= top) return null;
        return new RectF(left, top, right, bottom);
    }

    @Nullable
    private static RectF noteMarkerRectDoc(@NonNull RectF noteBounds, float scale) {
        if (scale <= 0f) return null;
        float sizeDoc = Math.max(10f, 18f / scale);
        float left = noteBounds.left;
        float top = noteBounds.top;
        return new RectF(left, top - sizeDoc, left + sizeDoc, top);
    }

    @Nullable
    private static RectF boundsForPointsOrNull(@Nullable PointF[] points) {
        if (points == null || points.length < 2) return null;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (PointF p : points) {
            if (p == null) continue;
            if (!Float.isFinite(p.x) || !Float.isFinite(p.y)) continue;
            if (p.x < minX) minX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) return null;
        // Allow degenerate strokes (straight lines) by expanding to a minimal non-zero box.
        if (maxX <= minX) maxX = minX + 0.001f;
        if (maxY <= minY) maxY = minY + 0.001f;
        return new RectF(minX, minY, maxX, maxY);
    }
}
