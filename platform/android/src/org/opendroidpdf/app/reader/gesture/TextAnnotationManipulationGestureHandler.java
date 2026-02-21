package org.opendroidpdf.app.reader.gesture;

import android.content.res.Resources;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.annotation.TextAnnotationClipboard;
import org.opendroidpdf.app.annotation.TextFontFamily;
import org.opendroidpdf.app.reader.ScrollMode;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;
import org.opendroidpdf.app.overlay.TextDragPreviewOverlay;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController;
import org.opendroidpdf.core.MuPdfController;

/**
 * Enables direct manipulation (move/resize) of embedded PDF FreeText annotations:
 * - Drag inside the selected box to move
 * - Drag the MOVE handle (top-center) to move (optional affordance)
 * - Drag corner handles (when visible) to resize (resize handles are explicitly enabled)
 *
 * <p>Consumes scroll gestures only when the gesture begins on the selected text annotation
 * (or on one of its handles) so normal panning remains intact elsewhere.</p>
 */
public final class TextAnnotationManipulationGestureHandler {
    private static final String TAG = "TextAnnotGesture";

    public interface Host {
        @Nullable MuPDFPageView currentPageView();
        @Nullable MuPDFReaderView readerView();
    }

    private enum Mode { NONE, MOVE, RESIZE, BLOCKED }

    private final Resources res;
    private final Host host;
    @Nullable private TextAnnotationMultiSelectController multiSelect;

    // Allow a small hit slop around the selected box so "grab to move" is reliable and doesn't
    // accidentally trigger page swipe navigation when the user misses by a few pixels.
    private static final float MOVE_GRAB_SLOP_DP = 24f;
    private static final float AUTO_SCROLL_EDGE_DP = 72f;
    private static final float AUTO_SCROLL_MAX_SPEED_DP_PER_FRAME = 6f;

    // PDF annotation flags (/F) bits for lock controls (mirrors MuPDF/EmbeddedFreeTextRepositoryOps).
    private static final int PDF_ANNOT_FLAG_LOCKED = 1 << (8 - 1);
    private static final int PDF_ANNOT_FLAG_LOCKED_CONTENTS = 1 << (10 - 1);

    private Mode mode = Mode.NONE;
    private ItemSelectionHandles.Handle resizeHandle = ItemSelectionHandles.Handle.NONE;
    private long activeObjectId = 0L;
    @Nullable private String activeSidecarNoteId;
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
    @Nullable private TextDragPreviewOverlay activePreviewOverlay;
    private boolean autoScrollActive = false;

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
    // If we start a MOVE/RESIZE during a gesture, always suppress the fling generated at the end
    // of that same gesture. This prevents accidental page switches when the selection box is moved
    // (the selection bounds may update before onFling runs, causing coordinate-based checks to miss).
    private long suppressFlingDownTime = -1L;

    // Best-effort caches so we can keep a text preview visible during drag even when the selected
    // annotation payload is missing in the live selection object (e.g., sidecar selection).
    @Nullable private String lastKnownEmbeddedText;
    private long lastKnownEmbeddedObjectId = -1L;
    @Nullable private String lastKnownSidecarText;
    @Nullable private String lastKnownSidecarId;

    // When manipulating embedded (PDF) text annotations, temporarily disable native annotation
    // rendering so the original appearance doesn't "ghost" under the overlay preview.
    private boolean embeddedAnnotationRenderingSuppressed = false;
    @Nullable private MuPDFPageView embeddedAnnotationSuppressionPageView;

    public TextAnnotationManipulationGestureHandler(@NonNull Resources res, @NonNull Host host) {
        this.res = res;
        this.host = host;
    }

    public void setMultiSelectController(@Nullable TextAnnotationMultiSelectController controller) {
        this.multiSelect = controller;
    }

    public boolean isActive() { return mode != Mode.NONE; }

    /** Returns {@code true} if there is a selected text annotation that should suppress page flings. */
    public boolean hasSelectedTextAnnotation() {
        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return false;
        try {
            Annotation selected = pageView.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
            if (selected != null
                    && (selected.type == Annotation.Type.FREETEXT || selected.type == Annotation.Type.TEXT)
                    && selected.objectNumber > 0L) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        try {
            SidecarSelectionController.Selection sel = pageView.selectedSidecarSelectionOrNull();
            return sel != null && sel.kind == SidecarSelectionController.Kind.NOTE;
        } catch (Throwable ignore) {
        }
        return false;
    }

    /**
     * Returns {@code true} if the gesture started on the currently selected text annotation
     * (embedded FreeText/Text or a sidecar note). Used to suppress view-switching flings while the
     * user is manipulating the annotation.
     */
    public boolean shouldConsumeFling(@Nullable MotionEvent e1) {
        if (e1 == null) return false;
        if (e1.getPointerCount() != 1) return false;
        // If this gesture was used to manipulate a text annotation, never allow the terminal fling
        // to reach the reader (it would switch pages).
        if (suppressFlingDownTime > 0L && e1.getDownTime() == suppressFlingDownTime) return true;

        MuPDFPageView pageView = null;
        MuPDFReaderView reader = host.readerView();
        if (reader != null) {
            try { pageView = pageViewUnderPoint(reader, e1.getX(), e1.getY()); } catch (Throwable ignore) {}
        }
        if (pageView == null) pageView = host.currentPageView();
        if (pageView == null) return false;

        final RectF selectedBounds;

        Annotation selected = pageView.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
        if (selected != null
                && (selected.type == Annotation.Type.FREETEXT || selected.type == Annotation.Type.TEXT)
                && selected.objectNumber > 0L) {
            selectedBounds = new RectF(selected);
        } else {
            SidecarSelectionController.Selection sel = pageView.selectedSidecarSelectionOrNull();
            if (sel != null && sel.kind == SidecarSelectionController.Kind.NOTE && sel.bounds != null) {
                selectedBounds = new RectF(sel.bounds);
            } else {
                // Fallback to the last-known selection box (kept stable across annotation reloads).
                RectF box = pageView.getItemSelectBox();
                if (box == null) return false;
                selectedBounds = box;
            }
        }

        float scale = pageView.getScale();
        if (scale <= 0f) return false;

        float docX1 = (e1.getX() - pageView.getLeft()) / scale;
        float docY1 = (e1.getY() - pageView.getTop()) / scale;

        // Block flings that begin on (or very near) the selection box so the page doesn't
        // change while the user is trying to move/resize the annotation.
        ItemSelectionHandles.Handle handle = ItemSelectionHandles.hitTestHandle(
                res, scale, selectedBounds, docX1, docY1, pageView.textResizeHandlesEnabled());
        if (handle != ItemSelectionHandles.Handle.NONE || selectedBounds.contains(docX1, docY1)) return true;
        float slopDoc = (MOVE_GRAB_SLOP_DP * res.getDisplayMetrics().density) / scale;
        return docX1 >= (selectedBounds.left - slopDoc)
                && docX1 <= (selectedBounds.right + slopDoc)
                && docY1 >= (selectedBounds.top - slopDoc)
                && docY1 <= (selectedBounds.bottom + slopDoc);
    }

    /**
     * Handles a scroll gesture. Returns {@code true} if the gesture is consumed by an active
     * move/resize operation (or if the scroll begins on a selected text annotation and we
     * start manipulation).
     */
    public boolean onScroll(@Nullable MotionEvent e1, @Nullable MotionEvent e2) {
        if (e1 == null || e2 == null) return false;
        if (e2.getPointerCount() != 1) return false;
        if (mode == Mode.BLOCKED) return true;

        MuPDFPageView pageView = null;
        MuPDFReaderView reader = host.readerView();
        if (reader != null) {
            try { pageView = pageViewUnderPoint(reader, e1.getX(), e1.getY()); } catch (Throwable ignore) {}
        }
        if (pageView == null) pageView = host.currentPageView();
        if (pageView == null) return false;

        final RectF selectedBounds;
        @Nullable final String selectedText;
        final long selectedObjectId;
        final String selectedSidecarId;

        Annotation selected = pageView.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
        if (selected != null
                && (selected.type == Annotation.Type.FREETEXT || selected.type == Annotation.Type.TEXT)
                && selected.objectNumber > 0L) {
            selectedBounds = new RectF(selected);
            selectedText = selected.text;
            selectedObjectId = selected.objectNumber;
            selectedSidecarId = null;
        } else {
            SidecarSelectionController.Selection sel = pageView.selectedSidecarSelectionOrNull();
            if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
            if (sel.id == null || sel.id.trim().isEmpty() || sel.bounds == null) return false;
            selectedBounds = new RectF(sel.bounds);
            selectedText = pageView.textAnnotationDelegate().sidecarNoteTextById(sel.id);
            selectedObjectId = 0L;
            selectedSidecarId = sel.id;
        }

        float scale = pageView.getScale();
        if (scale <= 0f) return false;

        float docX1 = (e1.getX() - pageView.getLeft()) / scale;
        float docY1 = (e1.getY() - pageView.getTop()) / scale;
        float docX2 = (e2.getX() - pageView.getLeft()) / scale;
        float docY2 = (e2.getY() - pageView.getTop()) / scale;

        if (mode == Mode.NONE) {
            boolean canResize = pageView.textResizeHandlesEnabled();
            ItemSelectionHandles.Handle handle = ItemSelectionHandles.hitTestHandle(res, scale, selectedBounds, docX1, docY1, canResize);

            // Not on a handle: when a text annotation is selected, dragging its body should
            // move it (Acrobat-style). Allow normal panning only when the drag begins outside
            // the selected bounds.
            if (handle == ItemSelectionHandles.Handle.NONE) {
                boolean inside = selectedBounds.contains(docX1, docY1);
                if (!inside) {
                    float slopDoc = (MOVE_GRAB_SLOP_DP * res.getDisplayMetrics().density) / scale;
                    boolean near = docX1 >= (selectedBounds.left - slopDoc)
                            && docX1 <= (selectedBounds.right + slopDoc)
                            && docY1 >= (selectedBounds.top - slopDoc)
                            && docY1 <= (selectedBounds.bottom + slopDoc);
                    if (!near) return false;
                }
            }

            // If the selected annotation is locked, consume the gesture so the reader doesn't
            // accidentally pan/switch pages while the user tries to move/resize it.
            boolean lockPos = false;
            try { lockPos = pageView.textAnnotationDelegate().selectedTextAnnotationLockPositionSizeOrDefault(); } catch (Throwable ignore) { lockPos = false; }
            if (lockPos) {
                try {
                    android.widget.Toast.makeText(pageView.getContext(), org.opendroidpdf.R.string.text_locked_position_size, android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {
                }
                suppressFlingDownTime = e1.getDownTime();
                mode = Mode.BLOCKED;
                return true;
            }
            if (handle != ItemSelectionHandles.Handle.NONE) {
                if (handle == ItemSelectionHandles.Handle.MOVE) {
                    mode = Mode.MOVE;
                    resizeHandle = ItemSelectionHandles.Handle.NONE;
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        android.util.Log.d(TAG, "start MOVE (handle) obj=" + selectedObjectId
                                + " sidecarId=" + selectedSidecarId
                                + " start=(" + docX1 + "," + docY1 + ")"
                                + " rect=(" + selectedBounds.left + "," + selectedBounds.top
                                + " " + selectedBounds.right + "," + selectedBounds.bottom + ")");
                    }
                } else {
                    mode = Mode.RESIZE;
                    resizeHandle = handle;
                }
            } else {
                mode = Mode.MOVE;
                resizeHandle = ItemSelectionHandles.Handle.NONE;
                if (org.opendroidpdf.BuildConfig.DEBUG) {
                    android.util.Log.d(TAG, "start MOVE (body) obj=" + selectedObjectId
                            + " sidecarId=" + selectedSidecarId
                            + " start=(" + docX1 + "," + docY1 + ")"
                            + " rect=(" + selectedBounds.left + "," + selectedBounds.top
                            + " " + selectedBounds.right + "," + selectedBounds.bottom + ")");
                }
            }

            suppressFlingDownTime = e1.getDownTime();
            activeObjectId = selectedObjectId;
            activeSidecarNoteId = selectedSidecarId;
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

            // While dragging/resizing, draw a lightweight preview of the text content in the overlay
            // so it "sticks" to the moving box even though the underlying PDF render updates on UP.
            String previewText = selectedText;
            if (selectedObjectId > 0L) {
                if (previewText != null && !previewText.trim().isEmpty()) {
                    lastKnownEmbeddedObjectId = selectedObjectId;
                    lastKnownEmbeddedText = previewText;
                } else if (selectedObjectId == lastKnownEmbeddedObjectId) {
                    previewText = lastKnownEmbeddedText;
                }
            } else if (selectedSidecarId != null && !selectedSidecarId.trim().isEmpty()) {
                if (previewText != null && !previewText.trim().isEmpty()) {
                    lastKnownSidecarId = selectedSidecarId;
                    lastKnownSidecarText = previewText;
                } else if (selectedSidecarId.equals(lastKnownSidecarId)) {
                    previewText = lastKnownSidecarText;
                }
            }
            TextDragPreviewOverlay previewOverlay = null;
            try {
                if (previewText != null) {
                    String t = previewText.trim();
                    if (!t.isEmpty()) {
                        if (selectedObjectId > 0L) {
                            previewOverlay = pageView.embeddedFreeTextDragPreviewOverlayOrNull(selectedObjectId, t);
                        } else {
                            previewOverlay = new TextDragPreviewOverlay(
                                    t,
                                    0xFF111111,
                                    0f,
                                    TextFontFamily.SANS,
                                    0,
                                    0);
                        }
                    }
                }
            } catch (Throwable ignore) {
                previewOverlay = null;
            }
            try { pageView.setTextDragPreviewOverlay(previewOverlay); } catch (Throwable ignore) {}
            activePreviewOverlay = previewOverlay;
            if (selectedObjectId > 0L) {
                // Suppress native annotation rendering so the original appearance doesn't remain
                // visible beneath the overlay preview while the user moves/resizes.
                suppressEmbeddedAnnotationRenderingIfNeeded(pageView);
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
            // Reset per-gesture state. We intentionally keep selection, but drop any in-progress
            // manipulation from a prior gesture.
            MuPDFPageView pv = host.currentPageView();
            if (pv != null) {
                try { pv.setTextDragPreviewOverlay(null); } catch (Throwable ignore) {}
            }
            MuPDFPageView activePv = activeDragPageView;
            if (activePv != null && activePv != pv) {
                try { activePv.setTextDragPreviewOverlay(null); } catch (Throwable ignore) {}
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
                    try { pageView.setTextDragPreviewOverlay(null); } catch (Throwable ignore) {}
                }
                MuPDFPageView activePv = activeDragPageView;
                if (activePv != null && activePv != pageView) {
                    try { activePv.setTextDragPreviewOverlay(null); } catch (Throwable ignore) {}
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
        final boolean wasMove = (mode == Mode.MOVE);
        final boolean markUserResized = (mode == Mode.RESIZE);
        final boolean restoreEmbeddedAnnotations = embeddedAnnotationRenderingSuppressed;
        if (mode == Mode.BLOCKED) {
            if (commitPageView != null) {
                try { commitPageView.setTextDragPreviewOverlay(null); } catch (Throwable ignore) {}
            }
            if (sourcePage != null && sourcePage != commitPageView) {
                try { sourcePage.setTextDragPreviewOverlay(null); } catch (Throwable ignore) {}
            }
            if (restoreEmbeddedAnnotations) restoreEmbeddedAnnotationRenderingIfNeeded(sourcePage);
            resetState();
            return;
        }
        RectF start = startBoundsDoc;
        RectF cur = currentBoundsDoc;
        long objectId = activeObjectId;
        String sidecarId = activeSidecarNoteId;
        int fromPage = sourcePageNumber;
        if (fromPage < 0 && sourcePage != null) {
            try { fromPage = sourcePage.pageNumber(); } catch (Throwable ignore) { fromPage = -1; }
        }
        int toPage = activeDragPageNumber;
        if (toPage < 0 && commitPageView != null) {
            try { toPage = commitPageView.pageNumber(); } catch (Throwable ignore) { toPage = -1; }
        }

        if (commitPageView != null) {
            try { commitPageView.setTextDragPreviewOverlay(null); } catch (Throwable ignore) {}
        }
        if (sourcePage != null && sourcePage != commitPageView) {
            try { sourcePage.setTextDragPreviewOverlay(null); } catch (Throwable ignore) {}
        }
        resetState();

        if (commitPageView == null || sourcePage == null || start == null) return;

        if (action == MotionEvent.ACTION_CANCEL || cur == null || (objectId <= 0L && (sidecarId == null || sidecarId.trim().isEmpty()))) {
            // Restore selection box to the original bounds if we were only previewing.
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

        // Commit the new rect into the backend (embedded PDF or sidecar store).
        try {
            if (objectId > 0L) {
                if (fromPage >= 0 && toPage >= 0 && fromPage != toPage) {
                    TextAnnotationClipboard.Payload payload = snapshotEmbeddedFreeTextForMoveOrNull(sourcePage, fromPage, objectId, cur);
                    if (payload != null) {
                        // Cross-page move: paste a clone on the destination page, then delete the source with undo.
                        TextAnnotationClipboard.setForCut(payload);
                        boolean pasted = false;
                        try { pasted = commitPageView.textAnnotationDelegate().pasteTextAnnotationFromClipboard(); } catch (Throwable ignore) { pasted = false; }
                        if (pasted) {
                            try { sourcePage.textAnnotationDelegate().deleteEmbeddedFreeTextByObjectNumberWithUndo(objectId); } catch (Throwable ignore) {}
                        } else {
                            // Paste couldn't start; keep the original in place.
                            try { sourcePage.setSelectionBox(start); } catch (Throwable ignore) {}
                            try { sourcePage.invalidateOverlay(); } catch (Throwable ignore) {}
                        }
                    } else {
                        // Snapshot failed; don't delete the original.
                        try { sourcePage.setSelectionBox(start); } catch (Throwable ignore) {}
                        try { sourcePage.invalidateOverlay(); } catch (Throwable ignore) {}
                    }
                } else {
                    sourcePage.textAnnotationDelegate().commitTextAnnotationRectByObjectNumber(objectId, cur, markUserResized);
                }
            } else if (sidecarId != null && !sidecarId.trim().isEmpty()) {
                if (fromPage >= 0 && toPage >= 0 && fromPage != toPage) {
                    org.opendroidpdf.app.sidecar.SidecarAnnotationSession session = sourcePage.sidecarSessionOrNull();
                    if (session != null) {
                        try { session.moveNoteToPage(fromPage, toPage, sidecarId, cur, markUserResized); } catch (Throwable ignore) {}
                    }
                    try { sourcePage.deselectAnnotation(); } catch (Throwable ignore) {}
                    try { commitPageView.textAnnotationDelegate().selectSidecarNoteById(sidecarId); } catch (Throwable ignore) {}
                    try { commitPageView.invalidateOverlay(); } catch (Throwable ignore) {}
                    try { sourcePage.invalidateOverlay(); } catch (Throwable ignore) {}
                } else {
                    sourcePage.textAnnotationDelegate().commitSidecarNoteBounds(sidecarId, cur, markUserResized);
                }
            }
            if (restoreEmbeddedAnnotations) restoreEmbeddedAnnotationRenderingIfNeeded(sourcePage);
        } catch (Throwable t) {
            // Best-effort: restore selection box and keep the doc stable.
            try { sourcePage.setSelectionBox(start); } catch (Throwable ignore) {}
            try { sourcePage.invalidateOverlay(); } catch (Throwable ignore) {}
            if (restoreEmbeddedAnnotations) restoreEmbeddedAnnotationRenderingIfNeeded(sourcePage);
            android.util.Log.e(TAG, "Failed to commit text annotation move/resize", t);
            return;
        }

        if (fromPage < 0 || toPage < 0 || fromPage != toPage) return;

        float dx = cur.left - start.left;
        float dy = cur.top - start.top;
        TextAnnotationMultiSelectController ms = multiSelect;
        if (ms != null) {
            try { ms.updateBoundsForItem(objectId, sidecarId, sourcePage.pageNumber(), cur, dx, dy); } catch (Throwable ignore) {}
            if (wasMove) {
                try { ms.applyGroupTranslation(sourcePage, objectId, sidecarId, dx, dy, markUserResized); } catch (Throwable ignore) {}
            }
        }
    }

    private void resetState() {
        mode = Mode.NONE;
        resizeHandle = ItemSelectionHandles.Handle.NONE;
        activeObjectId = 0L;
        activeSidecarNoteId = null;
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
        activePreviewOverlay = null;
        autoScrollActive = false;
    }

    private void suppressEmbeddedAnnotationRenderingIfNeeded(@NonNull MuPDFPageView pageView) {
        if (embeddedAnnotationRenderingSuppressed) return;
        embeddedAnnotationRenderingSuppressed = true;
        embeddedAnnotationSuppressionPageView = pageView;
        try { pageView.setEmbeddedAnnotationRenderingEnabled(false); } catch (Throwable ignore) {}
        // Force a full redraw so the existing appearance disappears quickly.
        try { pageView.discardRenderedPage(); } catch (Throwable ignore) {}
        try { pageView.redraw(true); } catch (Throwable ignore) {}
    }

    private void restoreEmbeddedAnnotationRenderingIfNeeded(@Nullable MuPDFPageView pageView) {
        if (!embeddedAnnotationRenderingSuppressed) return;
        embeddedAnnotationRenderingSuppressed = false;
        MuPDFPageView pv = pageView != null ? pageView : embeddedAnnotationSuppressionPageView;
        embeddedAnnotationSuppressionPageView = null;
        if (pv == null) return;
        // Respect the reader-level "comments visible" toggle.
        try {
            if (!pv.areCommentsVisible()) return;
        } catch (Throwable ignore) {
        }
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

        if (activeDragPageView != target) {
            clearPreviewFromPage(activeDragPageView);
            activeDragPageView = target;
        }

        try { target.setTextDragPreviewOverlay(activePreviewOverlay); } catch (Throwable ignore) {}
        try { target.setSelectionBox(docRect); } catch (Throwable ignore) {}
        try { target.invalidateOverlay(); } catch (Throwable ignore) {}
    }

    private void clearPreviewFromPage(@Nullable MuPDFPageView pageView) {
        if (pageView == null) return;
        try { pageView.setTextDragPreviewOverlay(null); } catch (Throwable ignore) {}
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

    @Nullable
    private static TextAnnotationClipboard.Payload snapshotEmbeddedFreeTextForMoveOrNull(@NonNull MuPDFPageView pageView,
                                                                                         int pageIndex,
                                                                                         long objectId,
                                                                                         @NonNull RectF destBoundsDoc) {
        if (pageView == null) return null;
        if (pageIndex < 0) return null;
        if (objectId <= 0L) return null;

        MuPdfController controller = null;
        try { controller = pageView.muPdfControllerOrNull(); } catch (Throwable ignore) { controller = null; }
        if (controller == null) return null;

        Annotation annot = null;
        try {
            Annotation[] annots = controller.annotations(pageIndex);
            if (annots != null) {
                for (Annotation a : annots) {
                    if (a != null && a.objectNumber == objectId) { annot = a; break; }
                }
            }
        } catch (Throwable ignore) {
            annot = null;
        }
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return null;

        String text = annot.text != null ? annot.text : "";

        float fontSizePt = 12.0f;
        float lineHeight = 1.2f;
        float textIndentPt = 0.0f;
        int fontFamily = TextFontFamily.SANS;
        int fontStyleFlags = 0;
        int alignment = 0;
        int rotationDeg = 0;
        boolean lockPos = false;
        boolean lockContents = false;
        boolean userResized = true;

        try { fontSizePt = controller.rawRepository().getFreeTextFontSizeByObjectNumber(pageIndex, objectId); } catch (Throwable ignore) {}
        try { fontFamily = controller.rawRepository().getFreeTextFontFamilyByObjectNumber(pageIndex, objectId); } catch (Throwable ignore) {}
        try { fontStyleFlags = controller.rawRepository().getFreeTextStyleFlagsByObjectNumber(pageIndex, objectId); } catch (Throwable ignore) {}
        try {
            float[] p = controller.rawRepository().getFreeTextParagraphByObjectNumber(pageIndex, objectId);
            if (p != null && p.length >= 1) lineHeight = p[0];
            if (p != null && p.length >= 2) textIndentPt = p[1];
        } catch (Throwable ignore) {}
        try { alignment = controller.rawRepository().getFreeTextAlignmentByObjectNumber(pageIndex, objectId); } catch (Throwable ignore) {}
        try { rotationDeg = controller.rawRepository().getFreeTextRotationByObjectNumber(pageIndex, objectId); } catch (Throwable ignore) {}
        try {
            int flags = controller.rawRepository().getFreeTextFlagsByObjectNumber(pageIndex, objectId);
            lockPos = (flags & PDF_ANNOT_FLAG_LOCKED) != 0;
            lockContents = (flags & PDF_ANNOT_FLAG_LOCKED_CONTENTS) != 0;
        } catch (Throwable ignore) {}
        try { userResized = controller.rawRepository().getFreeTextUserResizedByObjectNumber(pageIndex, objectId); } catch (Throwable ignore) {}

        int textColorArgb = 0xFF111111;
        try {
            float[] rgb = controller.rawRepository().getFreeTextTextColorByObjectNumber(pageIndex, objectId);
            if (rgb != null && rgb.length >= 3) {
                textColorArgb = 0xFF000000
                        | (Math.round(clamp01(rgb[0]) * 255f) << 16)
                        | (Math.round(clamp01(rgb[1]) * 255f) << 8)
                        | (Math.round(clamp01(rgb[2]) * 255f));
            }
        } catch (Throwable ignore) {}

        int bgColorArgb = 0x00000000;
        float bgOpacity = 0.0f;
        try {
            float[] bg = controller.rawRepository().getFreeTextBackgroundByObjectNumber(pageIndex, objectId);
            if (bg != null && bg.length >= 4) {
                bgColorArgb = 0xFF000000
                        | (Math.round(clamp01(bg[0]) * 255f) << 16)
                        | (Math.round(clamp01(bg[1]) * 255f) << 8)
                        | (Math.round(clamp01(bg[2]) * 255f));
                bgOpacity = clamp01(bg[3]);
            }
        } catch (Throwable ignore) {}

        int borderColorArgb = 0x00000000;
        float borderWidthPt = 0.0f;
        boolean borderDashed = false;
        float borderRadiusPt = 0.0f;
        try {
            float[] b = controller.rawRepository().getFreeTextBorderByObjectNumber(pageIndex, objectId);
            if (b != null && b.length >= 6) {
                borderColorArgb = 0xFF000000
                        | (Math.round(clamp01(b[0]) * 255f) << 16)
                        | (Math.round(clamp01(b[1]) * 255f) << 8)
                        | (Math.round(clamp01(b[2]) * 255f));
                borderWidthPt = b[3];
                borderDashed = Math.round(b[4]) != 0;
                borderRadiusPt = b[5];
            }
        } catch (Throwable ignore) {}

        return new TextAnnotationClipboard.Payload(
                TextAnnotationClipboard.Kind.EMBEDDED_FREETEXT,
                new RectF(destBoundsDoc),
                text,
                fontSizePt,
                lineHeight,
                textIndentPt,
                fontFamily,
                fontStyleFlags,
                alignment,
                rotationDeg,
                textColorArgb,
                bgColorArgb,
                bgOpacity,
                borderColorArgb,
                borderWidthPt,
                borderDashed,
                borderRadiusPt,
                lockPos,
                lockContents,
                userResized);
    }

    private static float clamp01(float v) {
        if (!Float.isFinite(v)) return 0f;
        return Math.max(0f, Math.min(1f, v));
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
}
