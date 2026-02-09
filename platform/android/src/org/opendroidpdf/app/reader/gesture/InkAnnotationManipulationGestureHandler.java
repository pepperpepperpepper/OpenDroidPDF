package org.opendroidpdf.app.reader.gesture;

import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.app.overlay.InkDragPreviewOverlay;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;

import java.util.ArrayList;
import java.util.List;

/**
 * Enables direct manipulation (move/resize) of ink annotations:
 * - embedded PDF Ink annotations
 * - sidecar-backed ink stroke groups (read-only PDFs / EPUB)
 *
 * <p>Consumes scroll gestures only when the gesture begins on the selected ink annotation
 * (or on one of its handles) so normal panning remains intact elsewhere.</p>
 */
public final class InkAnnotationManipulationGestureHandler {
    private static final String TAG = "InkAnnotGesture";

    public interface Host {
        @Nullable MuPDFPageView currentPageView();
    }

    private enum Mode { NONE, MOVE, RESIZE }

    // Allow a small hit slop around the selected box so "grab to move" is reliable.
    private static final float MOVE_GRAB_SLOP_DP = 24f;

    private final Resources res;
    private final Host host;

    private Mode mode = Mode.NONE;
    private ItemSelectionHandles.Handle resizeHandle = ItemSelectionHandles.Handle.NONE;
    private long suppressFlingDownTime = -1L;

    @Nullable private RectF startBoundsDoc;
    @Nullable private RectF currentBoundsDoc;
    private float startDocX;
    private float startDocY;
    @Nullable private InkDragPreviewOverlay dragPreviewOverlay;

    // Embedded ink selection (object id + geometry).
    private long activeObjectId = -1L;
    @Nullable private PointF[][] activeOriginalArcsDoc;

    // Sidecar ink selection (grouped by createdAtEpochMs).
    private long activeSidecarCreatedAtEpochMs = -1L;
    @Nullable private List<SidecarInkStroke> activeOriginalSidecarStrokes;

    public InkAnnotationManipulationGestureHandler(@NonNull Resources res, @NonNull Host host) {
        this.res = res;
        this.host = host;
    }

    public boolean isActive() { return mode != Mode.NONE; }

    /** Returns {@code true} if there is a selected ink annotation that should suppress page flings. */
    public boolean hasSelectedInkAnnotation() {
        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return false;
        if (!pageView.areCommentsVisible()) return false;
        try {
            SidecarSelectionController.Selection sel = pageView.selectedSidecarSelectionOrNull();
            if (sel != null && sel.kind == SidecarSelectionController.Kind.INK) return true;
        } catch (Throwable ignore) {
        }
        try {
            Annotation embedded = pageView.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
            return embedded != null && embedded.type == Annotation.Type.INK && embedded.objectNumber > 0L;
        } catch (Throwable ignore) {
        }
        return false;
    }

    /**
     * Returns {@code true} if the gesture started on the currently selected ink annotation.
     * Used to suppress view-switching flings while the user is manipulating the annotation.
     */
    public boolean shouldConsumeFling(@Nullable MotionEvent e1) {
        if (e1 == null) return false;
        if (e1.getPointerCount() != 1) return false;
        if (suppressFlingDownTime > 0L && e1.getDownTime() == suppressFlingDownTime) return true;

        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return false;
        if (!pageView.areCommentsVisible()) return false;
        RectF selectedBounds = pageView.getItemSelectBox();
        if (selectedBounds == null) return false;

        float scale = 0f;
        try { scale = pageView.getScale(); } catch (Throwable ignore) { scale = 0f; }
        if (scale <= 0f) return false;
        float docX = (e1.getX() - pageView.getLeft()) / scale;
        float docY = (e1.getY() - pageView.getTop()) / scale;

        float density = res.getDisplayMetrics().density;
        float slopDoc = (MOVE_GRAB_SLOP_DP * density) / scale;
        RectF expanded = new RectF(selectedBounds);
        expanded.inset(-slopDoc, -slopDoc);
        return expanded.contains(docX, docY);
    }

    public boolean onScroll(@Nullable MotionEvent e1, @Nullable MotionEvent e2) {
        if (e1 == null || e2 == null) return false;
        if (e1.getPointerCount() != 1 || e2.getPointerCount() != 1) return false;

        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return false;
        if (!pageView.areCommentsVisible()) return false;

        float scale = 0f;
        try { scale = pageView.getScale(); } catch (Throwable ignore) { scale = 0f; }
        if (scale <= 0f) return false;

        float docX1 = (e1.getX() - pageView.getLeft()) / scale;
        float docY1 = (e1.getY() - pageView.getTop()) / scale;
        float docX2 = (e2.getX() - pageView.getLeft()) / scale;
        float docY2 = (e2.getY() - pageView.getTop()) / scale;

        RectF selectedBounds = pageView.getItemSelectBox();
        if (selectedBounds == null) return false;

        if (mode == Mode.NONE) {
            // Only start manipulation when the selection is ink.
            boolean sidecarInkSelected = false;
            long createdAtMs = -1L;
            try {
                SidecarSelectionController.Selection sel = pageView.selectedSidecarSelectionOrNull();
                sidecarInkSelected = sel != null && sel.kind == SidecarSelectionController.Kind.INK;
                createdAtMs = sel != null ? sel.createdAtEpochMs : -1L;
            } catch (Throwable ignore) {
                sidecarInkSelected = false;
                createdAtMs = -1L;
            }

            Annotation embeddedInk = null;
            if (!sidecarInkSelected) {
                try {
                    Annotation a = pageView.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
                    if (a != null && a.type == Annotation.Type.INK && a.objectNumber > 0L && a.arcs != null && a.arcs.length > 0) {
                        embeddedInk = a;
                    }
                } catch (Throwable ignore) {
                    embeddedInk = null;
                }
                if (embeddedInk == null) return false;
            } else {
                if (createdAtMs <= 0L) return false;
            }

            float density = res.getDisplayMetrics().density;
            float slopDoc = (MOVE_GRAB_SLOP_DP * density) / scale;
            RectF expanded = new RectF(selectedBounds);
            expanded.inset(-slopDoc, -slopDoc);
            boolean near = expanded.contains(docX1, docY1);

            ItemSelectionHandles.Handle handle = ItemSelectionHandles.hitTestHandle(
                    res,
                    scale,
                    selectedBounds,
                    docX1,
                    docY1,
                    true /* includeResizeHandles */);
            // For signatures, corner resize handles should be easy to grab. The shared hit-test
            // helper intentionally treats "inside-box" touches as move, which makes corner resizes
            // feel impossible on small signatures. Prefer a direct handle hit-test that allows
            // grabbing the corner even if the finger overlaps inside the box.
            if (handle == ItemSelectionHandles.Handle.NONE || handle == ItemSelectionHandles.Handle.MOVE) {
                ItemSelectionHandles.Handle h2 = hitTestAnyHandle(res, scale, selectedBounds, docX1, docY1);
                if (h2 != ItemSelectionHandles.Handle.NONE) handle = h2;
            }

            if (!near && handle == ItemSelectionHandles.Handle.NONE) return false;

            if (handle != ItemSelectionHandles.Handle.NONE && handle != ItemSelectionHandles.Handle.MOVE) {
                mode = Mode.RESIZE;
                resizeHandle = handle;
            } else {
                mode = Mode.MOVE;
                resizeHandle = ItemSelectionHandles.Handle.NONE;
            }

            suppressFlingDownTime = e1.getDownTime();
            startBoundsDoc = new RectF(selectedBounds);
            currentBoundsDoc = new RectF(selectedBounds);
            startDocX = docX1;
            startDocY = docY1;

            if (sidecarInkSelected) {
                activeSidecarCreatedAtEpochMs = createdAtMs;
                activeObjectId = -1L;
                activeOriginalArcsDoc = null;
                activeOriginalSidecarStrokes = snapshotSidecarInkGroupOrNull(pageView, createdAtMs);
                if (activeOriginalSidecarStrokes == null || activeOriginalSidecarStrokes.isEmpty()) {
                    resetState();
                    return false;
                }

                // Prepare an in-overlay preview so the ink appears to resize/move in real time.
                PointF[][] arcs = new PointF[activeOriginalSidecarStrokes.size()][];
                int color = 0xCC000000;
                float thickness = 2.5f;
                for (int i = 0; i < activeOriginalSidecarStrokes.size(); i++) {
                    SidecarInkStroke s = activeOriginalSidecarStrokes.get(i);
                    arcs[i] = (s != null) ? s.points : null;
                    if (i == 0 && s != null) {
                        if ((s.color >>> 24) != 0) color = (s.color & 0x00FFFFFF) | 0xCC000000;
                        if (Float.isFinite(s.thickness) && s.thickness > 0f) thickness = s.thickness;
                    }
                }
                dragPreviewOverlay = new InkDragPreviewOverlay(new RectF(selectedBounds), new RectF(selectedBounds), arcs, color, thickness);
                try { pageView.setInkDragPreviewOverlay(dragPreviewOverlay); } catch (Throwable ignore) {}
            } else if (embeddedInk != null) {
                activeObjectId = embeddedInk.objectNumber;
                activeOriginalArcsDoc = cloneArcs(embeddedInk.arcs);
                activeSidecarCreatedAtEpochMs = -1L;
                activeOriginalSidecarStrokes = null;

                if (activeOriginalArcsDoc != null && activeOriginalArcsDoc.length > 0) {
                    dragPreviewOverlay = new InkDragPreviewOverlay(
                            new RectF(selectedBounds),
                            new RectF(selectedBounds),
                            activeOriginalArcsDoc,
                            0xCC000000,
                            2.5f);
                    try { pageView.setInkDragPreviewOverlay(dragPreviewOverlay); } catch (Throwable ignore) {}
                }
            }
        }

        RectF start = startBoundsDoc;
        if (start == null) return false;

        float dx = docX2 - startDocX;
        float dy = docY2 - startDocY;

        RectF next = new RectF(start);
        if (mode == Mode.MOVE) {
            next.offset(dx, dy);
        } else if (mode == Mode.RESIZE) {
            switch (resizeHandle) {
                case TOP_LEFT:
                    next.left += dx;
                    next.top += dy;
                    break;
                case TOP_RIGHT:
                    next.right += dx;
                    next.top += dy;
                    break;
                case BOTTOM_LEFT:
                    next.left += dx;
                    next.bottom += dy;
                    break;
                case BOTTOM_RIGHT:
                    next.right += dx;
                    next.bottom += dy;
                    break;
                default:
                    next.offset(dx, dy);
                    break;
            }
        }

        next = clampAndNormalize(pageView, scale, next);
        currentBoundsDoc = next;
        pageView.setSelectionBox(next);
        InkDragPreviewOverlay preview = dragPreviewOverlay;
        if (preview != null) {
            try { preview.setCurrentBoundsDoc(next); } catch (Throwable ignore) {}
        }
        try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
        return true;
    }

    /**
     * Called from the reader's raw touch stream so we can commit (or revert) at ACTION_UP/CANCEL.
     */
    public void onTouchEvent(@Nullable MotionEvent event) {
        if (event == null) return;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            suppressFlingDownTime = -1L;
            // Reset per-gesture state. Keep selection, but drop any in-progress manipulation.
            MuPDFPageView pv = host.currentPageView();
            if (pv != null) {
                try { pv.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
            }
            resetState();
            return;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // Multi-touch should always be able to zoom; cancel any manipulation preview.
            if (mode != Mode.NONE) {
                MuPDFPageView pageView = host.currentPageView();
                RectF start = startBoundsDoc;
                if (pageView != null) {
                    try { pageView.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
                }
                resetState();
                if (pageView != null && start != null) {
                    try { pageView.setSelectionBox(start); } catch (Throwable ignore) {}
                    try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
                }
            }
            return;
        }
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) return;
        if (mode == Mode.NONE) return;

        MuPDFPageView pageView = host.currentPageView();
        RectF start = startBoundsDoc;
        RectF cur = currentBoundsDoc;
        long objectId = activeObjectId;
        PointF[][] originalArcs = activeOriginalArcsDoc;
        long createdAtMs = activeSidecarCreatedAtEpochMs;
        List<SidecarInkStroke> originalSidecar = activeOriginalSidecarStrokes;

        if (pageView != null) {
            try { pageView.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
        }
        resetState();

        if (pageView == null || start == null) return;

        if (action == MotionEvent.ACTION_CANCEL || cur == null) {
            pageView.setSelectionBox(start);
            try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
            return;
        }

        if (createdAtMs > 0L && originalSidecar != null && !originalSidecar.isEmpty()) {
            commitSidecarInkTransform(pageView, createdAtMs, originalSidecar, start, cur);
            return;
        }

        if (objectId > 0L && originalArcs != null && originalArcs.length > 0) {
            PointF[][] updatedArcs = transformArcs(originalArcs, start, cur);
            try {
                boolean ok = pageView.replaceEmbeddedInkAnnotationByObjectNumberFromUi(objectId, originalArcs, updatedArcs);
                if (!ok) {
                    pageView.setSelectionBox(start);
                    try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
                }
            } catch (Throwable t) {
                try { pageView.setSelectionBox(start); } catch (Throwable ignore) {}
                try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
                android.util.Log.e(TAG, "Failed to commit ink annotation move/resize", t);
            }
        } else {
            pageView.setSelectionBox(start);
            try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
        }
    }

    private void resetState() {
        mode = Mode.NONE;
        resizeHandle = ItemSelectionHandles.Handle.NONE;
        startBoundsDoc = null;
        currentBoundsDoc = null;
        startDocX = 0f;
        startDocY = 0f;
        dragPreviewOverlay = null;
        activeObjectId = -1L;
        activeOriginalArcsDoc = null;
        activeSidecarCreatedAtEpochMs = -1L;
        activeOriginalSidecarStrokes = null;
    }

    private static ItemSelectionHandles.Handle hitTestAnyHandle(@NonNull Resources res,
                                                                float scale,
                                                                @NonNull RectF itemBoxDoc,
                                                                float docX,
                                                                float docY) {
        if (scale <= 0f) return ItemSelectionHandles.Handle.NONE;

        ItemSelectionHandles.Handle best = ItemSelectionHandles.Handle.NONE;
        float bestDist2 = Float.MAX_VALUE;

        ItemSelectionHandles.Handle[] candidates = new ItemSelectionHandles.Handle[]{
                ItemSelectionHandles.Handle.TOP_LEFT,
                ItemSelectionHandles.Handle.TOP_RIGHT,
                ItemSelectionHandles.Handle.BOTTOM_LEFT,
                ItemSelectionHandles.Handle.BOTTOM_RIGHT,
                ItemSelectionHandles.Handle.MOVE,
        };

        final float left = itemBoxDoc.left;
        final float right = itemBoxDoc.right;
        final float top = itemBoxDoc.top;
        final float bottom = itemBoxDoc.bottom;

        for (ItemSelectionHandles.Handle h : candidates) {
            RectF r = ItemSelectionHandles.handleRectDoc(res, scale, itemBoxDoc, h);
            if (r == null || !r.contains(docX, docY)) continue;

            float cx;
            float cy;
            switch (h) {
                case TOP_LEFT:
                    cx = left; cy = top; break;
                case TOP_RIGHT:
                    cx = right; cy = top; break;
                case BOTTOM_LEFT:
                    cx = left; cy = bottom; break;
                case BOTTOM_RIGHT:
                    cx = right; cy = bottom; break;
                case MOVE:
                    cx = (left + right) * 0.5f; cy = top; break;
                default:
                    continue;
            }

            float dx = docX - cx;
            float dy = docY - cy;
            float dist2 = dx * dx + dy * dy;
            if (dist2 < bestDist2) {
                bestDist2 = dist2;
                best = h;
            }
        }

        return best;
    }

    @NonNull
    private RectF clampAndNormalize(@NonNull MuPDFPageView pageView, float scale, @NonNull RectF r) {
        float left = Math.min(r.left, r.right);
        float right = Math.max(r.left, r.right);
        float top = Math.min(r.top, r.bottom);
        float bottom = Math.max(r.top, r.bottom);

        float docWidth = pageView.getWidth() / scale;
        float docHeight = pageView.getHeight() / scale;

        float minEdgeDoc = ItemSelectionHandles.minEdgePx(res) / scale;
        if ((right - left) < minEdgeDoc) {
            float cx = (left + right) * 0.5f;
            left = cx - minEdgeDoc * 0.5f;
            right = cx + minEdgeDoc * 0.5f;
        }
        if ((bottom - top) < minEdgeDoc) {
            float cy = (top + bottom) * 0.5f;
            top = cy - minEdgeDoc * 0.5f;
            bottom = cy + minEdgeDoc * 0.5f;
        }

        // Clamp to the document bounds.
        if (left < 0f) {
            right -= left;
            left = 0f;
        }
        if (top < 0f) {
            bottom -= top;
            top = 0f;
        }
        if (right > docWidth) {
            float overflow = right - docWidth;
            left -= overflow;
            right = docWidth;
        }
        if (bottom > docHeight) {
            float overflow = bottom - docHeight;
            top -= overflow;
            bottom = docHeight;
        }

        left = Math.max(0f, left);
        top = Math.max(0f, top);
        right = Math.min(docWidth, right);
        bottom = Math.min(docHeight, bottom);

        // Re-enforce min size after clamping.
        if ((right - left) < minEdgeDoc) right = Math.min(docWidth, left + minEdgeDoc);
        if ((bottom - top) < minEdgeDoc) bottom = Math.min(docHeight, top + minEdgeDoc);

        return new RectF(left, top, right, bottom);
    }

    @Nullable
    private static List<SidecarInkStroke> snapshotSidecarInkGroupOrNull(@NonNull MuPDFPageView pageView, long createdAtEpochMs) {
        SidecarAnnotationSession session = null;
        try { session = pageView.sidecarSessionOrNull(); } catch (Throwable ignore) { session = null; }
        if (session == null || createdAtEpochMs <= 0L) return null;
        List<SidecarInkStroke> strokes = session.inkStrokesForPage(pageView.pageNumber());
        if (strokes == null || strokes.isEmpty()) return null;
        ArrayList<SidecarInkStroke> group = new ArrayList<>();
        for (SidecarInkStroke s : strokes) {
            if (s == null || s.points == null || s.points.length < 2) continue;
            if (s.createdAtEpochMs != createdAtEpochMs) continue;
            group.add(s);
        }
        return group.isEmpty() ? null : group;
    }

    private static void commitSidecarInkTransform(@NonNull MuPDFPageView pageView,
                                                 long createdAtEpochMs,
                                                 @NonNull List<SidecarInkStroke> original,
                                                 @NonNull RectF startBoundsDoc,
                                                 @NonNull RectF updatedBoundsDoc) {
        SidecarAnnotationSession session = pageView.sidecarSessionOrNull();
        if (session == null) return;

        ArrayList<SidecarInkStroke> updated = new ArrayList<>();
        for (SidecarInkStroke s : original) {
            if (s == null || s.points == null || s.points.length < 2) continue;
            PointF[] nextPoints = transformPoints(s.points, startBoundsDoc, updatedBoundsDoc);
            updated.add(new SidecarInkStroke(
                    s.id,
                    s.pageIndex,
                    s.layoutProfileId,
                    s.color,
                    s.thickness,
                    s.createdAtEpochMs,
                    nextPoints));
        }
        if (updated.isEmpty()) return;

        try { session.recordUndoInkUpdated(pageView.pageNumber(), original, updated); } catch (Throwable ignore) {}
        try { session.upsertInkStrokes(pageView.pageNumber(), updated); } catch (Throwable ignore) {}
        try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
        try { pageView.refreshUndoState(); } catch (Throwable ignore) {}
    }

    @NonNull
    private static PointF[] transformPoints(@NonNull PointF[] points, @NonNull RectF start, @NonNull RectF dest) {
        float sw = start.width();
        float sh = start.height();
        float dw = dest.width();
        float dh = dest.height();
        if (sw <= 0f || sh <= 0f || dw <= 0f || dh <= 0f) return points;
        PointF[] out = new PointF[points.length];
        for (int i = 0; i < points.length; i++) {
            PointF p = points[i];
            if (p == null) continue;
            float rx = (p.x - start.left) / sw;
            float ry = (p.y - start.top) / sh;
            out[i] = new PointF(dest.left + (rx * dw), dest.top + (ry * dh));
        }
        return out;
    }

    @NonNull
    private static PointF[][] transformArcs(@NonNull PointF[][] arcs, @NonNull RectF start, @NonNull RectF dest) {
        float sw = start.width();
        float sh = start.height();
        float dw = dest.width();
        float dh = dest.height();
        if (sw <= 0f || sh <= 0f || dw <= 0f || dh <= 0f) return arcs;
        PointF[][] out = new PointF[arcs.length][];
        for (int i = 0; i < arcs.length; i++) {
            PointF[] stroke = arcs[i];
            if (stroke == null) continue;
            PointF[] next = new PointF[stroke.length];
            for (int j = 0; j < stroke.length; j++) {
                PointF p = stroke[j];
                if (p == null) continue;
                float rx = (p.x - start.left) / sw;
                float ry = (p.y - start.top) / sh;
                next[j] = new PointF(dest.left + (rx * dw), dest.top + (ry * dh));
            }
            out[i] = next;
        }
        return out;
    }

    @Nullable
    private static PointF[][] cloneArcs(@Nullable PointF[][] arcs) {
        if (arcs == null) return null;
        PointF[][] out = new PointF[arcs.length][];
        for (int i = 0; i < arcs.length; i++) {
            PointF[] stroke = arcs[i];
            if (stroke == null) continue;
            PointF[] strokeCopy = new PointF[stroke.length];
            for (int j = 0; j < stroke.length; j++) {
                PointF p = stroke[j];
                strokeCopy[j] = p == null ? null : new PointF(p.x, p.y);
            }
            out[i] = strokeCopy;
        }
        return out;
    }
}
