package org.opendroidpdf.app.reader.gesture;

import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.reader.ScrollMode;
import org.opendroidpdf.app.overlay.InkDragPreviewOverlay;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.core.MuPdfController;

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
        @Nullable MuPDFReaderView readerView();
    }

    private enum Mode { NONE, MOVE, RESIZE }

    // Allow a small hit slop around the selected box so "grab to move" is reliable.
    private static final float MOVE_GRAB_SLOP_DP = 24f;
    private static final float AUTO_SCROLL_EDGE_DP = 72f;
    private static final float AUTO_SCROLL_MAX_SPEED_DP_PER_FRAME = 6f;

    private final Resources res;
    private final Host host;

    private Mode mode = Mode.NONE;
    private ItemSelectionHandles.Handle resizeHandle = ItemSelectionHandles.Handle.NONE;
    private long suppressFlingDownTime = -1L;

    @Nullable private RectF startBoundsDoc;
    @Nullable private RectF currentBoundsDoc;
    private float startDocX;
    private float startDocY;
    private float startTouchX;
    private float startTouchY;
    private float lastTouchX;
    private float lastTouchY;
    @Nullable private RectF startBoundsScreen;
    private int sourcePageNumber = -1;
    @Nullable private MuPDFPageView sourcePageView;
    @Nullable private MuPDFPageView activeDragPageView;
    private int activeDragPageNumber = -1;
    @Nullable private InkDragPreviewOverlay sourceSuppressOverlay;
    private boolean autoScrollActive = false;
    @Nullable private InkDragPreviewOverlay dragPreviewOverlay;

    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!autoScrollActive) return;
            if (mode != Mode.MOVE) { autoScrollActive = false; return; }

            MuPDFReaderView reader = host.readerView();
            if (reader == null) { autoScrollActive = false; return; }

            try {
                if (reader.getScrollMode() != ScrollMode.CONTINUOUS) { autoScrollActive = false; return; }
            } catch (Throwable ignore) {
                autoScrollActive = false;
                return;
            }

            float edgePx = AUTO_SCROLL_EDGE_DP * res.getDisplayMetrics().density;
            float maxSpeedPx = AUTO_SCROLL_MAX_SPEED_DP_PER_FRAME * res.getDisplayMetrics().density;
            float h = reader.getHeight();
            if (h <= 0f || edgePx <= 0f || maxSpeedPx <= 0f) { autoScrollActive = false; return; }

            float y = lastTouchY;
            float dy = 0f;
            if (y < edgePx) {
                float t = (edgePx - y) / edgePx;
                dy = +maxSpeedPx * clamp01(t);
            } else if (y > (h - edgePx)) {
                float t = (y - (h - edgePx)) / edgePx;
                dy = -maxSpeedPx * clamp01(t);
            }

            if (dy == 0f) {
                autoScrollActive = false;
                return;
            }

            try { reader.addScrollForOverlayDrag(0f, dy); } catch (Throwable ignore) {}
            try {
                MuPDFPageView fallback = activeDragPageView != null ? activeDragPageView : (sourcePageView != null ? sourcePageView : host.currentPageView());
                if (fallback != null) updateMovePreview(fallback, lastTouchX, lastTouchY);
            } catch (Throwable ignore) {
            }

            try { reader.postOnAnimation(this); } catch (Throwable ignore) { autoScrollActive = false; }
        }
    };

    // Embedded ink selection (object id + geometry).
    private long activeObjectId = -1L;
    @Nullable private PointF[][] activeOriginalArcsDoc;

    // Sidecar ink selection (grouped by createdAtEpochMs).
    private long activeSidecarCreatedAtEpochMs = -1L;
    @Nullable private List<SidecarInkStroke> activeOriginalSidecarStrokes;

    // When manipulating embedded (PDF) ink, temporarily disable native annotation rendering
    // so the original appearance doesn't "ghost" under the overlay preview.
    private boolean embeddedAnnotationRenderingSuppressed = false;
    @Nullable private MuPDFPageView embeddedAnnotationSuppressionPageView;

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
            startTouchX = e1.getX();
            startTouchY = e1.getY();
            lastTouchX = startTouchX;
            lastTouchY = startTouchY;
            startBoundsScreen = new RectF(
                    pageView.getLeft() + (selectedBounds.left * scale),
                    pageView.getTop() + (selectedBounds.top * scale),
                    pageView.getLeft() + (selectedBounds.right * scale),
                    pageView.getTop() + (selectedBounds.bottom * scale));
            sourcePageNumber = pageView.pageNumber();
            sourcePageView = pageView;
            activeDragPageView = pageView;
            activeDragPageNumber = pageView.pageNumber();
            sourceSuppressOverlay = null;

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
                dragPreviewOverlay = new InkDragPreviewOverlay(
                        new RectF(selectedBounds),
                        new RectF(selectedBounds),
                        arcs,
                        color,
                        thickness,
                        createdAtMs /* suppress this ink group while previewing */);
                sourceSuppressOverlay = new InkDragPreviewOverlay(
                        new RectF(selectedBounds),
                        new RectF(selectedBounds),
                        new PointF[0][],
                        color,
                        thickness,
                        createdAtMs /* suppress this ink group on the source page while previewing */);
                try { pageView.setInkDragPreviewOverlay(dragPreviewOverlay); } catch (Throwable ignore) {}
            } else if (embeddedInk != null) {
                activeObjectId = embeddedInk.objectNumber;
                activeOriginalArcsDoc = cloneArcs(embeddedInk.arcs);
                activeSidecarCreatedAtEpochMs = -1L;
                activeOriginalSidecarStrokes = null;
                sourceSuppressOverlay = null;

                if (activeOriginalArcsDoc != null && activeOriginalArcsDoc.length > 0) {
                    dragPreviewOverlay = new InkDragPreviewOverlay(
                            new RectF(selectedBounds),
                            new RectF(selectedBounds),
                            activeOriginalArcsDoc,
                            0xCC000000,
                            2.5f);
                    try { pageView.setInkDragPreviewOverlay(dragPreviewOverlay); } catch (Throwable ignore) {}
                    // Suppress native annotation rendering so the original ink doesn't remain visible
                    // beneath the overlay preview while the user resizes/moves.
                    suppressEmbeddedAnnotationRenderingIfNeeded(pageView);
                }
            }
        }

        RectF start = startBoundsDoc;
        if (start == null) return false;

        if (mode == Mode.MOVE && startBoundsScreen != null) {
            lastTouchX = e2.getX();
            lastTouchY = e2.getY();
            updateMovePreview(pageView, lastTouchX, lastTouchY);
            maybeStartOrStopAutoScroll();
            return true;
        }

        float dx = docX2 - startDocX;
        float dy = docY2 - startDocY;

        final RectF next;
        if (mode == Mode.MOVE) {
            RectF moved = new RectF(start);
            moved.offset(dx, dy);
            next = clampAndNormalize(pageView, scale, moved);
        } else if (mode == Mode.RESIZE) {
            // Proportional resize already clamps to the document bounds and enforces a minimum edge.
            next = proportionalResizeFromStart(pageView, scale, start, resizeHandle, dx, dy);
        } else {
            next = new RectF(start);
        }
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
        if (action == MotionEvent.ACTION_MOVE) {
            if (mode == Mode.MOVE) {
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                maybeStartOrStopAutoScroll();
            }
        }
        if (action == MotionEvent.ACTION_DOWN) {
            suppressFlingDownTime = -1L;
            autoScrollActive = false;
            // Reset per-gesture state. Keep selection, but drop any in-progress manipulation.
            MuPDFPageView pv = host.currentPageView();
            if (pv != null) {
                try { pv.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
            }
            MuPDFPageView activePv = activeDragPageView;
            if (activePv != null && activePv != pv) {
                try { activePv.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
            }
            MuPDFPageView sourcePv = sourcePageView;
            if (sourcePv != null && sourcePv != pv && sourcePv != activePv) {
                try { sourcePv.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
            }
            restoreEmbeddedAnnotationRenderingIfNeeded(pv);
            resetState();
            return;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // Multi-touch should always be able to zoom; cancel any manipulation preview.
            if (mode != Mode.NONE) {
                MuPDFPageView pageView = host.currentPageView();
                RectF start = startBoundsDoc;
                autoScrollActive = false;
                if (pageView != null) {
                    try { pageView.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
                }
                MuPDFPageView activePv = activeDragPageView;
                if (activePv != null && activePv != pageView) {
                    try { activePv.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
                }
                MuPDFPageView sourcePv = sourcePageView;
                if (sourcePv != null && sourcePv != pageView && sourcePv != activePv) {
                    try { sourcePv.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
                }
                restoreEmbeddedAnnotationRenderingIfNeeded(pageView);
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

        autoScrollActive = false;

        MuPDFPageView commitPageView = activeDragPageView != null ? activeDragPageView : host.currentPageView();
        MuPDFPageView sourcePage = sourcePageView != null ? sourcePageView : host.currentPageView();
        RectF start = startBoundsDoc;
        RectF cur = currentBoundsDoc;
        long objectId = activeObjectId;
        PointF[][] originalArcs = activeOriginalArcsDoc;
        long createdAtMs = activeSidecarCreatedAtEpochMs;
        List<SidecarInkStroke> originalSidecar = activeOriginalSidecarStrokes;
        boolean restoreEmbeddedAnnotations = embeddedAnnotationRenderingSuppressed;

        int fromPage = sourcePageNumber;
        if (fromPage < 0 && sourcePage != null) {
            try { fromPage = sourcePage.pageNumber(); } catch (Throwable ignore) { fromPage = -1; }
        }
        int toPage = activeDragPageNumber;
        if (toPage < 0 && commitPageView != null) {
            try { toPage = commitPageView.pageNumber(); } catch (Throwable ignore) { toPage = -1; }
        }

        if (commitPageView != null) {
            try { commitPageView.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
        }
        if (sourcePage != null && sourcePage != commitPageView) {
            try { sourcePage.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
        }
        resetState();

        if (commitPageView == null || sourcePage == null || start == null) return;

        if (action == MotionEvent.ACTION_CANCEL || cur == null) {
            try {
                sourcePage.setSelectionBox(start);
                sourcePage.invalidateOverlay();
            } catch (Throwable ignore) {
            }
            if (commitPageView != sourcePage) {
                try {
                    commitPageView.setSelectionBox(null);
                    commitPageView.invalidateOverlay();
                } catch (Throwable ignore) {
                }
            }
            if (restoreEmbeddedAnnotations) restoreEmbeddedAnnotationRenderingIfNeeded(sourcePage);
            return;
        }

        if (createdAtMs > 0L && originalSidecar != null && !originalSidecar.isEmpty()) {
            if (fromPage >= 0 && toPage >= 0 && fromPage != toPage) {
                commitSidecarInkMoveToPage(sourcePage, commitPageView, toPage, originalSidecar, start, cur);
            } else {
                commitSidecarInkTransform(sourcePage, createdAtMs, originalSidecar, start, cur);
            }
            if (restoreEmbeddedAnnotations) restoreEmbeddedAnnotationRenderingIfNeeded(sourcePage);
            return;
        }

        if (objectId > 0L && originalArcs != null && originalArcs.length > 0) {
            PointF[][] updatedArcs = transformArcs(originalArcs, start, cur);
            if (fromPage >= 0 && toPage >= 0 && fromPage != toPage) {
                boolean added = false;
                try { added = commitPageView.addInkAnnotationFromUi(updatedArcs); } catch (Throwable ignore) { added = false; }
                if (added) {
                    try {
                        MuPdfController controller = sourcePage.muPdfControllerOrNull();
                        if (controller != null) controller.deleteAnnotationByObjectNumber(fromPage, objectId);
                    } catch (Throwable ignore) {
                    }
                    try { sourcePage.requestFullRedrawAfterNextAnnotationLoad(); } catch (Throwable ignore) {}
                    try { sourcePage.discardRenderedPage(); } catch (Throwable ignore) {}
                    try { sourcePage.loadAnnotations(); } catch (Throwable ignore) {}
                    try { sourcePage.setSelectionBox(null); } catch (Throwable ignore) {}
                    try { sourcePage.invalidateOverlay(); } catch (Throwable ignore) {}
                } else {
                    try { sourcePage.setSelectionBox(start); } catch (Throwable ignore) {}
                    try { sourcePage.invalidateOverlay(); } catch (Throwable ignore) {}
                }
            } else {
                try {
                    boolean ok = sourcePage.replaceEmbeddedInkAnnotationByObjectNumberFromUi(objectId, originalArcs, updatedArcs);
                    if (!ok) {
                        sourcePage.setSelectionBox(start);
                        try { sourcePage.invalidateOverlay(); } catch (Throwable ignore) {}
                    }
                } catch (Throwable t) {
                    try { sourcePage.setSelectionBox(start); } catch (Throwable ignore) {}
                    try { sourcePage.invalidateOverlay(); } catch (Throwable ignore) {}
                    android.util.Log.e(TAG, "Failed to commit ink annotation move/resize", t);
                }
            }
            if (restoreEmbeddedAnnotations) restoreEmbeddedAnnotationRenderingIfNeeded(sourcePage);
        } else {
            sourcePage.setSelectionBox(start);
            try { sourcePage.invalidateOverlay(); } catch (Throwable ignore) {}
            if (restoreEmbeddedAnnotations) restoreEmbeddedAnnotationRenderingIfNeeded(sourcePage);
        }
    }

    private void resetState() {
        mode = Mode.NONE;
        resizeHandle = ItemSelectionHandles.Handle.NONE;
        startBoundsDoc = null;
        currentBoundsDoc = null;
        startDocX = 0f;
        startDocY = 0f;
        startTouchX = 0f;
        startTouchY = 0f;
        lastTouchX = 0f;
        lastTouchY = 0f;
        startBoundsScreen = null;
        sourcePageNumber = -1;
        sourcePageView = null;
        activeDragPageView = null;
        activeDragPageNumber = -1;
        sourceSuppressOverlay = null;
        autoScrollActive = false;
        dragPreviewOverlay = null;
        activeObjectId = -1L;
        activeOriginalArcsDoc = null;
        activeSidecarCreatedAtEpochMs = -1L;
        activeOriginalSidecarStrokes = null;
    }

    private void suppressEmbeddedAnnotationRenderingIfNeeded(@NonNull MuPDFPageView pageView) {
        if (embeddedAnnotationRenderingSuppressed) return;
        embeddedAnnotationRenderingSuppressed = true;
        embeddedAnnotationSuppressionPageView = pageView;
        try { pageView.setEmbeddedAnnotationRenderingEnabled(false); } catch (Throwable ignore) {}
        // Force a full redraw so the existing ink appearance disappears quickly.
        try { pageView.discardRenderedPage(); } catch (Throwable ignore) {}
        try { pageView.redraw(true); } catch (Throwable ignore) {}
    }

    private void restoreEmbeddedAnnotationRenderingIfNeeded(@Nullable MuPDFPageView pageView) {
        if (!embeddedAnnotationRenderingSuppressed) return;
        embeddedAnnotationRenderingSuppressed = false;
        MuPDFPageView pv = pageView != null ? pageView : embeddedAnnotationSuppressionPageView;
        embeddedAnnotationSuppressionPageView = null;
        if (pv == null) return;
        try { pv.setEmbeddedAnnotationRenderingEnabled(true); } catch (Throwable ignore) {}
        try { pv.discardRenderedPage(); } catch (Throwable ignore) {}
        try { pv.redraw(true); } catch (Throwable ignore) {}
    }

    private void updateMovePreview(@NonNull MuPDFPageView defaultPageView, float touchX, float touchY) {
        RectF startScreen = startBoundsScreen;
        if (startScreen == null) return;

        RectF screenRect = new RectF(startScreen);
        screenRect.offset(touchX - startTouchX, touchY - startTouchY);

        MuPDFPageView target = defaultPageView;
        MuPDFReaderView reader = host.readerView();
        if (reader != null) {
            try {
                if (reader.getScrollMode() == ScrollMode.CONTINUOUS) {
                    MuPDFPageView under = pageViewUnderPoint(reader, touchX, touchY);
                    if (under == null) {
                        MuPDFPageView ref = activeDragPageView != null ? activeDragPageView : defaultPageView;
                        under = inferAdjacentPageIfInGap(reader, ref, touchY);
                    }
                    if (under != null) target = under;
                }
            } catch (Throwable ignore) {
            }
        }

        float scale = 0f;
        try { scale = target.getScale(); } catch (Throwable ignore) { scale = 0f; }
        if (scale <= 0f) return;

        RectF docRect = new RectF(
                (screenRect.left - target.getLeft()) / scale,
                (screenRect.top - target.getTop()) / scale,
                (screenRect.right - target.getLeft()) / scale,
                (screenRect.bottom - target.getTop()) / scale);

        docRect = clampAndNormalize(target, scale, docRect);
        currentBoundsDoc = docRect;
        activeDragPageNumber = target.pageNumber();

        InkDragPreviewOverlay preview = dragPreviewOverlay;
        if (preview != null) {
            try { preview.setCurrentBoundsDoc(docRect); } catch (Throwable ignore) {}
        }

        MuPDFPageView sourcePv = sourcePageView;
        boolean sidecar = activeSidecarCreatedAtEpochMs > 0L && sourcePv != null && sourceSuppressOverlay != null;

        // Manage the overlay lifecycle across pages.
        MuPDFPageView prev = activeDragPageView;
        if (prev != null && prev != target) {
            boolean keepPrev = sidecar && prev == sourcePv;
            if (!keepPrev) {
                clearPreviewFromPage(prev);
            }
        }

        activeDragPageView = target;

        if (sidecar && sourcePv != null) {
            // Keep suppressing the source group while previewing on other pages.
            try {
                if (target == sourcePv) {
                    sourcePv.setInkDragPreviewOverlay(dragPreviewOverlay);
                } else {
                    sourcePv.setInkDragPreviewOverlay(sourceSuppressOverlay);
                }
            } catch (Throwable ignore) {
            }
        } else if (sourcePv != null && target != sourcePv) {
            // Embedded ink: clear overlay from the source page when we move away.
            try { sourcePv.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
        }

        if (target != sourcePv) {
            try { target.setInkDragPreviewOverlay(dragPreviewOverlay); } catch (Throwable ignore) {}
        }

        try { target.setSelectionBox(docRect); } catch (Throwable ignore) {}
        try { target.invalidateOverlay(); } catch (Throwable ignore) {}
        if (sourcePv != null && sourcePv != target) {
            try { sourcePv.invalidateOverlay(); } catch (Throwable ignore) {}
        }
    }

    private void clearPreviewFromPage(@Nullable MuPDFPageView pageView) {
        if (pageView == null) return;
        try { pageView.setInkDragPreviewOverlay(null); } catch (Throwable ignore) {}
        try { pageView.setSelectionBox(null); } catch (Throwable ignore) {}
        try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
    }

    private void maybeStartOrStopAutoScroll() {
        if (mode != Mode.MOVE) { autoScrollActive = false; return; }
        MuPDFReaderView reader = host.readerView();
        if (reader == null) { autoScrollActive = false; return; }
        try {
            if (reader.getScrollMode() != ScrollMode.CONTINUOUS) { autoScrollActive = false; return; }
        } catch (Throwable ignore) {
            autoScrollActive = false;
            return;
        }

        float edgePx = AUTO_SCROLL_EDGE_DP * res.getDisplayMetrics().density;
        float h = reader.getHeight();
        if (edgePx <= 0f || h <= 0f) { autoScrollActive = false; return; }

        boolean nearEdge = lastTouchY < edgePx || lastTouchY > (h - edgePx);
        if (!nearEdge) {
            autoScrollActive = false;
            return;
        }

        if (!autoScrollActive) {
            autoScrollActive = true;
            try { reader.postOnAnimation(autoScrollRunnable); } catch (Throwable ignore) { autoScrollActive = false; }
        }
    }

    @Nullable
    private static MuPDFPageView pageViewUnderPoint(@Nullable MuPDFReaderView reader, float x, float y) {
        if (reader == null) return null;
        int center = reader.getSelectedItemPosition();
        for (int delta = -2; delta <= 2; delta++) {
            int idx = center + delta;
            View v = reader.getView(idx);
            if (!(v instanceof MuPDFPageView)) continue;
            MuPDFPageView pv = (MuPDFPageView) v;
            if (x >= pv.getLeft() && x <= pv.getRight()
                    && y >= pv.getTop() && y <= pv.getBottom()) {
                return pv;
            }
        }
        return null;
    }

    @Nullable
    private static MuPDFPageView inferAdjacentPageIfInGap(@Nullable MuPDFReaderView reader,
                                                          @NonNull MuPDFPageView referencePage,
                                                          float y) {
        if (reader == null || referencePage == null) return null;
        int current = referencePage.getPageNumber();
        if (y < referencePage.getTop()) {
            View prev = reader.getView(current - 1);
            return prev instanceof MuPDFPageView ? (MuPDFPageView) prev : null;
        }
        if (y > referencePage.getBottom()) {
            View next = reader.getView(current + 1);
            return next instanceof MuPDFPageView ? (MuPDFPageView) next : null;
        }
        return null;
    }

    private static float clamp01(float v) {
        if (!Float.isFinite(v)) return 0f;
        return Math.max(0f, Math.min(1f, v));
    }

    @NonNull
    private RectF proportionalResizeFromStart(@NonNull MuPDFPageView pageView,
                                             float scale,
                                             @NonNull RectF start,
                                             @NonNull ItemSelectionHandles.Handle handle,
                                             float dx,
                                             float dy) {
        float sw = start.width();
        float sh = start.height();
        if (sw <= 0f || sh <= 0f || !Float.isFinite(sw) || !Float.isFinite(sh)) {
            RectF fallback = new RectF(start);
            applyFreeResizeFromStart(fallback, handle, dx, dy);
            return fallback;
        }

        // Lock proportions by default (Acrobat-style for signatures/ink).
        final float aspect = sw / sh;
        if (aspect <= 0f || !Float.isFinite(aspect)) {
            RectF fallback = new RectF(start);
            applyFreeResizeFromStart(fallback, handle, dx, dy);
            return fallback;
        }

        float anchorX;
        float anchorY;
        float proposedX;
        float proposedY;
        int sx;
        int sy;
        switch (handle) {
            case TOP_LEFT:
                anchorX = start.right;
                anchorY = start.bottom;
                proposedX = start.left + dx;
                proposedY = start.top + dy;
                sx = -1;
                sy = -1;
                break;
            case TOP_RIGHT:
                anchorX = start.left;
                anchorY = start.bottom;
                proposedX = start.right + dx;
                proposedY = start.top + dy;
                sx = +1;
                sy = -1;
                break;
            case BOTTOM_LEFT:
                anchorX = start.right;
                anchorY = start.top;
                proposedX = start.left + dx;
                proposedY = start.bottom + dy;
                sx = -1;
                sy = +1;
                break;
            case BOTTOM_RIGHT:
                anchorX = start.left;
                anchorY = start.top;
                proposedX = start.right + dx;
                proposedY = start.bottom + dy;
                sx = +1;
                sy = +1;
                break;
            default:
                RectF fallback = new RectF(start);
                fallback.offset(dx, dy);
                return fallback;
        }

        // Use directional widths/heights so dragging "past" the anchor clamps cleanly rather than
        // producing mirrored jumps.
        float proposedW = (sx < 0) ? (anchorX - proposedX) : (proposedX - anchorX);
        float proposedH = (sy < 0) ? (anchorY - proposedY) : (proposedY - anchorY);
        proposedW = Math.max(1e-4f, proposedW);
        proposedH = Math.max(1e-4f, proposedH);

        // Choose whether to preserve the user's X or Y motion based on which adjustment is smaller.
        float optionAX = anchorX + (sx * (aspect * proposedH));
        float optionAY = proposedY;
        float optionBX = proposedX;
        float optionBY = anchorY + (sy * (proposedW / aspect));
        float dA2 = (optionAX - proposedX) * (optionAX - proposedX) + (optionAY - proposedY) * (optionAY - proposedY);
        float dB2 = (optionBX - proposedX) * (optionBX - proposedX) + (optionBY - proposedY) * (optionBY - proposedY);
        float cornerX = dA2 <= dB2 ? optionAX : optionBX;
        float cornerY = dA2 <= dB2 ? optionAY : optionBY;

        // Clamp to document bounds while keeping the anchor fixed and preserving aspect ratio.
        float docW = pageView.getWidth() / (scale > 0f ? scale : 1f);
        float docH = pageView.getHeight() / (scale > 0f ? scale : 1f);

        float maxW = sx < 0 ? anchorX : (docW - anchorX);
        float maxH = sy < 0 ? anchorY : (docH - anchorY);
        maxW = Math.max(0f, maxW);
        maxH = Math.max(0f, maxH);

        float minEdgeDoc = ItemSelectionHandles.minEdgePx(res) / (scale > 0f ? scale : 1f);
        float minH = minEdgeDoc;
        if (aspect < 1f) {
            // width = aspect * height; enforce width >= minEdgeDoc.
            minH = Math.max(minH, minEdgeDoc / Math.max(1e-4f, aspect));
        }

        float desiredW = Math.abs(cornerX - anchorX);
        float desiredH = Math.abs(cornerY - anchorY);
        desiredH = Math.max(minH, desiredH);

        float maxAllowedH = maxH;
        if (aspect > 0f) {
            maxAllowedH = Math.min(maxAllowedH, maxW / aspect);
        }
        if (Float.isFinite(maxAllowedH) && maxAllowedH > 0f) {
            desiredH = Math.min(desiredH, maxAllowedH);
        }

        float finalW = aspect * desiredH;
        float finalH = desiredH;

        float finalCornerX = anchorX + (sx * finalW);
        float finalCornerY = anchorY + (sy * finalH);

        RectF out;
        switch (handle) {
            case TOP_LEFT:
                out = new RectF(finalCornerX, finalCornerY, anchorX, anchorY);
                break;
            case TOP_RIGHT:
                out = new RectF(anchorX, finalCornerY, finalCornerX, anchorY);
                break;
            case BOTTOM_LEFT:
                out = new RectF(finalCornerX, anchorY, anchorX, finalCornerY);
                break;
            case BOTTOM_RIGHT:
                out = new RectF(anchorX, anchorY, finalCornerX, finalCornerY);
                break;
            default:
                out = new RectF(start);
                out.offset(dx, dy);
                break;
        }
        return out;
    }

    private static void applyFreeResizeFromStart(@NonNull RectF rect,
                                                @NonNull ItemSelectionHandles.Handle handle,
                                                float dx,
                                                float dy) {
        switch (handle) {
            case TOP_LEFT:
                rect.left += dx;
                rect.top += dy;
                return;
            case TOP_RIGHT:
                rect.right += dx;
                rect.top += dy;
                return;
            case BOTTOM_LEFT:
                rect.left += dx;
                rect.bottom += dy;
                return;
            case BOTTOM_RIGHT:
                rect.right += dx;
                rect.bottom += dy;
                return;
            default:
                rect.offset(dx, dy);
        }
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

    private static void commitSidecarInkMoveToPage(@NonNull MuPDFPageView sourcePageView,
                                                   @NonNull MuPDFPageView destPageView,
                                                   int destPageIndex,
                                                   @NonNull List<SidecarInkStroke> original,
                                                   @NonNull RectF startBoundsDoc,
                                                   @NonNull RectF destBoundsDoc) {
        SidecarAnnotationSession session = sourcePageView.sidecarSessionOrNull();
        if (session == null) return;

        ArrayList<SidecarInkStroke> updated = new ArrayList<>();
        for (SidecarInkStroke s : original) {
            if (s == null || s.points == null || s.points.length < 2) continue;
            PointF[] nextPoints = transformPoints(s.points, startBoundsDoc, destBoundsDoc);
            updated.add(new SidecarInkStroke(
                    s.id,
                    destPageIndex,
                    s.layoutProfileId,
                    s.color,
                    s.thickness,
                    s.createdAtEpochMs,
                    nextPoints));
        }
        if (updated.isEmpty()) return;

        try { session.recordUndoInkMoved(original, updated); } catch (Throwable ignore) {}
        try { session.upsertInkStrokesAnyPage(updated); } catch (Throwable ignore) {}

        try { sourcePageView.deselectAnnotation(); } catch (Throwable ignore) {}
        try { destPageView.setSelectionBox(new RectF(destBoundsDoc)); } catch (Throwable ignore) {}
        try { destPageView.invalidateOverlay(); } catch (Throwable ignore) {}
        try { sourcePageView.invalidateOverlay(); } catch (Throwable ignore) {}
        try { destPageView.refreshUndoState(); } catch (Throwable ignore) {}
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
