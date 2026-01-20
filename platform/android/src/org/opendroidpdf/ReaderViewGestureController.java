package org.opendroidpdf;

import android.view.MotionEvent;
import android.view.View;

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
            int curX = view.scrollState.getX();
            int curY = view.scrollState.getY();
            curX += x - view.scrollState.getScrollerLastX();
            curY += y - view.scrollState.getScrollerLastY();
            view.scrollState.setScroll(curX, curY);
            view.scrollState.setScrollerLast(x, y);
            view.requestLayout();
            if (!view.isScrollDisabledForHost()) view.post(view);
        } else if (!view.mUserInteracting) {
            // End of an inertial scroll and the user is not interacting.
            // The layout is stable.
            View v = view.getSelectedView();
            if (v != null) view.postSettle(v);
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
}
