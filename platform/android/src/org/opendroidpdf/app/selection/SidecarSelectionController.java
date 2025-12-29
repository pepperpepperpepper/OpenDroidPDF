package org.opendroidpdf.app.selection;

import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
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

        float scale();

        int viewLeft();

        int viewTop();

        void setItemSelectBox(@Nullable RectF rect);

        void forwardTextAnnotation(@NonNull Annotation annotation);
    }

    public enum Kind { NOTE, HIGHLIGHT }

    public static final class Selection {
        public final Kind kind;
        @NonNull public final String id;
        @NonNull public final RectF bounds;

        public Selection(@NonNull Kind kind, @NonNull String id, @NonNull RectF bounds) {
            this.kind = kind;
            this.id = id;
            this.bounds = bounds;
        }
    }

    private final Host host;
    @Nullable private Selection selection;

    public SidecarSelectionController(@NonNull Host host) {
        this.host = host;
    }

    public boolean hasSelection() { return selection != null; }

    @Nullable
    public Selection selectionOrNull() { return selection; }

    public boolean isSelectionEditable() {
        Selection sel = selection;
        return sel != null && sel.kind == Kind.NOTE;
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
        selection = new Selection(sel.kind, sel.id, new RectF(bounds));
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

        final float scale = host.scale();
        if (scale == 0f) return null;
        final float docRelX = (e.getX() - host.viewLeft()) / scale;
        final float docRelY = (e.getY() - host.viewTop()) / scale;

        // Prefer note markers (small/tap-target) over broad highlight rects.
        Selection noteHit = hitTestNotes(sidecar, docRelX, docRelY, scale);
        if (noteHit != null) return noteHit;

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
                return new Selection(Kind.NOTE, n.id, new RectF(n.bounds));
            }
            if (n.bounds.contains(docRelX, docRelY)) {
                return new Selection(Kind.NOTE, n.id, new RectF(n.bounds));
            }
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
                return new Selection(Kind.HIGHLIGHT, h.id, union);
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
}
