package org.opendroidpdf.app.reader.gesture;

import android.content.res.Resources;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;
import org.opendroidpdf.app.selection.SidecarSelectionController;

/**
 * Enables direct manipulation (move/resize) of embedded PDF FreeText annotations:
 * - Long-press + release inside the selected box to arm move, then drag to move
 * - Drag corner handles to resize
 *
 * <p>Consumes scroll gestures only when the gesture begins on the selected text annotation
 * (or on one of its handles) so normal panning remains intact elsewhere.</p>
 */
public final class TextAnnotationManipulationGestureHandler {
    private static final String TAG = "TextAnnotGesture";

    public interface Host {
        @Nullable MuPDFPageView currentPageView();
    }

    private enum Mode { NONE, MOVE, RESIZE }

    private final Resources res;
    private final Host host;

    // "Move" requires an intentional long-press so one-finger pan after zoom remains intuitive.
    private static final long MOVE_LONG_PRESS_MS = ViewConfiguration.getLongPressTimeout();
    private static final long MOVE_ARM_WINDOW_MS = 3000L;
    private static final float MOVE_ARM_SLOP_PX = 12f;
    // Allow a small amount of drift between the arm long-press and the follow-up drag.
    // (ReaderView may apply a settle correction between gestures.)
    private static final float MOVE_START_SLOP_PX = 96f;

    private Mode mode = Mode.NONE;
    private ItemSelectionHandles.Handle resizeHandle = ItemSelectionHandles.Handle.NONE;
    private long moveArmedUntilUptimeMs = 0L;
    private long activeObjectId = 0L;
    @Nullable private String activeSidecarNoteId;
    @Nullable private RectF startBoundsDoc;
    @Nullable private RectF currentBoundsDoc;
    private float startDocX;
    private float startDocY;

    // Track down/up for "arm move" gesture (long-press then release).
    private boolean downInsideSelected = false;
    private float downXpx = 0f;
    private float downYpx = 0f;
    private boolean movedSinceDown = false;

    public TextAnnotationManipulationGestureHandler(@NonNull Resources res, @NonNull Host host) {
        this.res = res;
        this.host = host;
    }

    public boolean isActive() { return mode != Mode.NONE; }

    /**
     * Handles a scroll gesture. Returns {@code true} if the gesture is consumed by an active
     * move/resize operation (or if the scroll begins on a selected text annotation and we
     * start manipulation).
     */
    public boolean onScroll(@Nullable MotionEvent e1, @Nullable MotionEvent e2) {
        if (e1 == null || e2 == null) return false;
        if (e2.getPointerCount() != 1) return false;

        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return false;

        final RectF selectedBounds;
        final long selectedObjectId;
        final String selectedSidecarId;

        Annotation selected = pageView.selectedEmbeddedAnnotationOrNull();
        if (selected != null
                && (selected.type == Annotation.Type.FREETEXT || selected.type == Annotation.Type.TEXT)
                && selected.objectNumber > 0L) {
            selectedBounds = new RectF(selected);
            selectedObjectId = selected.objectNumber;
            selectedSidecarId = null;
        } else {
            SidecarSelectionController.Selection sel = pageView.selectedSidecarSelectionOrNull();
            if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
            if (sel.id == null || sel.id.trim().isEmpty() || sel.bounds == null) return false;
            selectedBounds = new RectF(sel.bounds);
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
            ItemSelectionHandles.Handle handle = ItemSelectionHandles.hitTestHandle(res, scale, selectedBounds, docX1, docY1);
            if (handle != ItemSelectionHandles.Handle.NONE) {
                mode = Mode.RESIZE;
                resizeHandle = handle;
            } else {
                // Not on a handle: allow normal panning unless the user intentionally armed move.
                long now = SystemClock.uptimeMillis();
                boolean armed = moveArmedUntilUptimeMs > 0L && now <= moveArmedUntilUptimeMs;
                if (!armed) {
                    // Not armed: treat as normal pan, even if the user pauses before dragging.
                    // (Avoids "can't pan after zoom" reports when a text annotation is selected.)
                    return false;
                }
                // When armed, tolerate some drift from settle/scroll correction between gestures.
                float slopDoc = MOVE_START_SLOP_PX / scale;
                if (docX1 < (selectedBounds.left - slopDoc)
                        || docX1 > (selectedBounds.right + slopDoc)
                        || docY1 < (selectedBounds.top - slopDoc)
                        || docY1 > (selectedBounds.bottom + slopDoc)) {
                    return false;
                }
                // The user long-pressed and released to arm move; allow immediate drag-to-move.
                moveArmedUntilUptimeMs = 0L;
                mode = Mode.MOVE;
                resizeHandle = ItemSelectionHandles.Handle.NONE;
                if (org.opendroidpdf.BuildConfig.DEBUG) {
                    android.util.Log.d(TAG, "start MOVE obj=" + selectedObjectId
                            + " sidecarId=" + selectedSidecarId
                            + " start=(" + docX1 + "," + docY1 + ")"
                            + " rect=(" + selectedBounds.left + "," + selectedBounds.top
                            + " " + selectedBounds.right + "," + selectedBounds.bottom + ")");
                }
            }

            activeObjectId = selectedObjectId;
            activeSidecarNoteId = selectedSidecarId;
            startBoundsDoc = new RectF(selectedBounds);
            currentBoundsDoc = new RectF(selectedBounds);
            startDocX = docX1;
            startDocY = docY1;
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
        pageView.setAnnotationSelectionBox(next);
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
            // Reset per-gesture state. We intentionally keep selection, but drop any in-progress
            // manipulation from a prior gesture.
            resetState();
            downInsideSelected = false;
            movedSinceDown = false;
            downXpx = event.getX();
            downYpx = event.getY();

            // Track whether this down is inside the selected text annotation bounds (excluding handles).
            MuPDFPageView pageView = host.currentPageView();
            if (pageView != null) {
                float scale = pageView.getScale();
                if (scale > 0f) {
                    float docX = (event.getX() - pageView.getLeft()) / scale;
                    float docY = (event.getY() - pageView.getTop()) / scale;

                    RectF bounds = null;
                    Annotation selected = pageView.selectedEmbeddedAnnotationOrNull();
                    if (selected != null
                            && (selected.type == Annotation.Type.FREETEXT || selected.type == Annotation.Type.TEXT)
                            && selected.objectNumber > 0L) {
                        bounds = new RectF(selected);
                    } else {
                        SidecarSelectionController.Selection sel = pageView.selectedSidecarSelectionOrNull();
                        if (sel != null && sel.kind == SidecarSelectionController.Kind.NOTE && sel.bounds != null) {
                            bounds = new RectF(sel.bounds);
                        }
                    }

                    if (bounds != null) {
                        ItemSelectionHandles.Handle handle = ItemSelectionHandles.hitTestHandle(res, scale, bounds, docX, docY);
                        downInsideSelected = handle == ItemSelectionHandles.Handle.NONE && bounds.contains(docX, docY);
                    }
                }
            }
            return;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (!movedSinceDown) {
                float dx = Math.abs(event.getX() - downXpx);
                float dy = Math.abs(event.getY() - downYpx);
                movedSinceDown = dx > MOVE_ARM_SLOP_PX || dy > MOVE_ARM_SLOP_PX;
            }
            return;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            // Multi-touch should always be able to zoom; cancel any manipulation preview.
            if (mode != Mode.NONE) {
                MuPDFPageView pageView = host.currentPageView();
                RectF start = startBoundsDoc;
                resetState();
                if (pageView != null && start != null) {
                    try { pageView.setAnnotationSelectionBox(start); } catch (Throwable ignore) {}
                    try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
                }
            }
            return;
        }
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) return;

        if (mode == Mode.NONE) {
            // Detect a "long-press then release" inside the selection box to arm move, so users
            // can then drag-to-move in a separate gesture (useful for accessibility and automation).
            if (action == MotionEvent.ACTION_UP && downInsideSelected && !movedSinceDown) {
                long dtMs = Math.max(0L, event.getEventTime() - event.getDownTime());
                if (dtMs >= MOVE_LONG_PRESS_MS) {
                    moveArmedUntilUptimeMs = SystemClock.uptimeMillis() + MOVE_ARM_WINDOW_MS;
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        android.util.Log.d(TAG, "armed MOVE until=" + moveArmedUntilUptimeMs
                                + " dtMs=" + dtMs);
                    }
                }
            }
            return;
        }

        MuPDFPageView pageView = host.currentPageView();
        RectF start = startBoundsDoc;
        RectF cur = currentBoundsDoc;
        long objectId = activeObjectId;
        String sidecarId = activeSidecarNoteId;

        resetState();
        moveArmedUntilUptimeMs = 0L;

        if (pageView == null || start == null) return;

        if (action == MotionEvent.ACTION_CANCEL || cur == null || (objectId <= 0L && (sidecarId == null || sidecarId.trim().isEmpty()))) {
            // Restore selection box to the original bounds if we were only previewing.
            pageView.setAnnotationSelectionBox(start);
            try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
            return;
        }

        // Commit the new rect into the backend (embedded PDF or sidecar store).
        try {
            if (objectId > 0L) {
                pageView.commitTextAnnotationRectByObjectNumber(objectId, cur);
            } else if (sidecarId != null && !sidecarId.trim().isEmpty()) {
                pageView.commitSidecarNoteBounds(sidecarId, cur);
            }
        } catch (Throwable t) {
            // Best-effort: restore selection box and keep the doc stable.
            try { pageView.setAnnotationSelectionBox(start); } catch (Throwable ignore) {}
            try { pageView.invalidateOverlay(); } catch (Throwable ignore) {}
            android.util.Log.e(TAG, "Failed to commit text annotation move/resize", t);
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
