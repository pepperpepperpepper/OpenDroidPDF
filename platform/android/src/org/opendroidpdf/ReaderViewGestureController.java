package org.opendroidpdf;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import org.opendroidpdf.app.reader.ReaderGeometry;
import org.opendroidpdf.app.reader.ScrollMode;

final class ReaderViewGestureController {
    private final ReaderView view;

    ReaderViewGestureController(ReaderView view) {
        this.view = view;
    }

    void run() {
        if (!view.mScroller.isFinished()) {
            view.mScroller.computeScrollOffset();
            int x = view.mScroller.getCurrX();
            int y = view.mScroller.getCurrY();
            int dx = x - view.scrollState.getScrollerLastX();
            int dy = y - view.scrollState.getScrollerLastY();
            view.scrollState.setScrollerLast(x, y);

            // In continuous scroll, the Scroller is intentionally given a massive Y range so flings
            // can traverse many pages. That makes it possible to scroll past the first/last page
            // into empty background unless we actively clamp at document ends.
            if (dx != 0 || dy != 0) {
                view.scrollState.addScroll(dx, dy);
                if (view.getScrollMode() == ScrollMode.CONTINUOUS) {
                    clampInertialScrollAtDocumentEndsIfNeeded();
                }
                view.requestLayout();
            }
            if (!view.isScrollDisabledForHost()) view.postOnAnimation(view);
        } else if (!view.mUserInteracting) {
            // End of an inertial scroll and the user is not interacting.
            // The layout is stable.
            View v = view.getSelectedView();
            if (v != null) {
                // Ensure we don't end an inertial scroll out of bounds (e.g. beyond the first/last page
                // in continuous scrolling, which would leave empty background visible).
                view.slideViewOntoScreenBridge(v);
                if (view.mScroller.isFinished()) {
                    view.postSettle(v);
                }
            }
        }
    }

    boolean onTouchEvent(MotionEvent event) {
        view.gestureRouter.onTouchEvent(event);

        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            view.mUserInteracting = true;
        }
        int action = (event.getAction() & MotionEvent.ACTION_MASK);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            view.setScrollDisabledForHost(false);
            view.mUserInteracting = false;

            View v = view.getSelectedView();
            if (v != null) {
                if (view.mScroller.isFinished()) {
                    // If, at the end of user interaction, there is no current inertial scroll in
                    // operation then animate the view onto screen if necessary.
                    view.slideViewOntoScreenBridge(v);
                }

                if (view.mScroller.isFinished()) {
                    // If still there is no inertial scroll in operation then the layout is stable.
                    view.postSettle(v);
                }
            }
        }

        return true;
    }

    private void clampInertialScrollAtDocumentEndsIfNeeded() {
        android.widget.Adapter adapter = view.getAdapter();
        if (adapter == null) return;
        int count = adapter.getCount();
        if (count <= 0) return;
        int current = view.getSelectedItemPosition();
        if (current < 0) return;

        boolean atFirstPage = current == 0;
        boolean atLastPage = current == count - 1;
        if (!atFirstPage && !atLastPage) return;

        View selected = view.getSelectedView();
        if (selected == null) return;

        Rect bounds = view.getScrollBoundsForView(selected);
        Point corr = ReaderGeometry.correction(bounds);
        if (corr.y == 0) return;

        // Overscrolling past the start requires a negative correction (move content up).
        if (atFirstPage && corr.y < 0) {
            view.scrollState.setScroll(view.scrollState.getX() + corr.x, view.scrollState.getY() + corr.y);
            view.mScroller.forceFinished(true);
            view.scrollState.setScrollerLast(0, 0);
            return;
        }
        // Overscrolling past the end requires a positive correction (move content down).
        if (atLastPage && corr.y > 0) {
            view.scrollState.setScroll(view.scrollState.getX() + corr.x, view.scrollState.getY() + corr.y);
            view.mScroller.forceFinished(true);
            view.scrollState.setScrollerLast(0, 0);
        }
    }
}
