package org.opendroidpdf.app.reader.gesture;

import android.content.res.Resources;
import android.graphics.RectF;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController;

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
    }

    private enum Mode { NONE, MOVE, RESIZE, BLOCKED }

    private final Resources res;
    private final Host host;
    @Nullable private TextAnnotationMultiSelectController multiSelect;

    // Allow a small hit slop around the selected box so "grab to move" is reliable and doesn't
    // accidentally trigger page swipe navigation when the user misses by a few pixels.
    private static final float MOVE_GRAB_SLOP_DP = 24f;

    private Mode mode = Mode.NONE;
    private ItemSelectionHandles.Handle resizeHandle = ItemSelectionHandles.Handle.NONE;
    private long activeObjectId = 0L;
    @Nullable private String activeSidecarNoteId;
    @Nullable private RectF startBoundsDoc;
    @Nullable private RectF currentBoundsDoc;
    private float startDocX;
    private float startDocY;
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

        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return false;
        if (!pageView.areCommentsVisible()) return false;

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

        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return false;
        if (!pageView.areCommentsVisible()) return false;

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
            try { pageView.setItemDragPreviewText(previewText); } catch (Throwable ignore) {}
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
            // Reset per-gesture state. We intentionally keep selection, but drop any in-progress
            // manipulation from a prior gesture.
            MuPDFPageView pv = host.currentPageView();
            if (pv != null) {
                try { pv.setItemDragPreviewText(null); } catch (Throwable ignore) {}
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
                    try { pageView.setItemDragPreviewText(null); } catch (Throwable ignore) {}
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
        final boolean markUserResized = (mode == Mode.RESIZE);
        if (mode == Mode.BLOCKED) {
            if (pageView != null) {
                try { pageView.setItemDragPreviewText(null); } catch (Throwable ignore) {}
            }
            resetState();
            return;
        }
        RectF start = startBoundsDoc;
        RectF cur = currentBoundsDoc;
        long objectId = activeObjectId;
        String sidecarId = activeSidecarNoteId;

        if (pageView != null) {
            try { pageView.setItemDragPreviewText(null); } catch (Throwable ignore) {}
        }
        resetState();
        if (pageView != null && markUserResized) {
            try { pageView.setTextResizeHandlesEnabled(false); } catch (Throwable ignore) {}
        }

        if (pageView == null || start == null) return;

        if (action == MotionEvent.ACTION_CANCEL || cur == null || (objectId <= 0L && (sidecarId == null || sidecarId.trim().isEmpty()))) {
            // Restore selection box to the original bounds if we were only previewing.
            pageView.setSelectionBox(start);
            try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
            return;
        }

        // Commit the new rect into the backend (embedded PDF or sidecar store).
        try {
            if (objectId > 0L) {
                pageView.textAnnotationDelegate().commitTextAnnotationRectByObjectNumber(objectId, cur, markUserResized);
            } else if (sidecarId != null && !sidecarId.trim().isEmpty()) {
                pageView.textAnnotationDelegate().commitSidecarNoteBounds(sidecarId, cur, markUserResized);
            }
        } catch (Throwable t) {
            // Best-effort: restore selection box and keep the doc stable.
            try { pageView.setSelectionBox(start); } catch (Throwable ignore) {}
            try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
            android.util.Log.e(TAG, "Failed to commit text annotation move/resize", t);
            return;
        }

        float dx = cur.left - start.left;
        float dy = cur.top - start.top;
        TextAnnotationMultiSelectController ms = multiSelect;
        if (ms != null) {
            try { ms.updateBoundsForItem(objectId, sidecarId, pageView.pageNumber(), cur, dx, dy); } catch (Throwable ignore) {}
            if (mode == Mode.MOVE) {
                try { ms.applyGroupTranslation(pageView, objectId, sidecarId, dx, dy, markUserResized); } catch (Throwable ignore) {}
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
