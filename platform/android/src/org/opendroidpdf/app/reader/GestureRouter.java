package org.opendroidpdf.app.reader;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * Centralizes gesture routing so ReaderView/PageView can slim down.
 * Initially acts as a thin wrapper around the platform detectors and
 * delegates to the provided listeners. Future slices can move logic here.
 */
public final class GestureRouter {

    public interface Host {
        boolean isScaling();
        void setScaling(boolean scaling);

        // Fling helpers
        boolean isScrollDisabled();
        void setScrollDisabled(boolean disabled);
        android.view.View getSelectedView();
        android.view.View getNextViewCandidate();
        android.view.View getPreviousViewCandidate();
        android.graphics.Rect getScrollBounds(android.view.View v);
        int getFlingMargin();
        void slideViewOntoScreen(android.view.View v);
        void flingWithinBounds(int velocityX, int velocityY, android.graphics.Rect bounds);

        // Scroll helpers
        void addScroll(float dx, float dy);
        void requestLayoutHost();
        void setScroll(int x, int y);

        // Scale helpers
        float getScale();
        void setScale(float scale);
        boolean isReflow();
        boolean isFitWidth();
        float getMinScale();
        float getMaxScale();
        int getPrevFocusX();
        int getPrevFocusY();
        void setPrevFocus(int x, int y);
        void applyScaleToAllChildren();
        void stopScroller();
        int getContainerWidth();
        int getContainerHeight();
        int getPadLeft();
        int getPadRight();
        int getPadTop();
        int getPadBottom();
    }

    private final Host host;
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;

    public GestureRouter(Context context,
                         GestureDetector.OnGestureListener gestureListener,
                         ScaleGestureDetector.OnScaleGestureListener scaleListener,
                         Host host) {
        this.host = host;
        this.gestureDetector = new GestureDetector(context, gestureListener);
        this.scaleGestureDetector = new ScaleGestureDetector(context, scaleListener);
    }

    /**
     * Delivers touch to scale first, then to gesture when not actively scaling.
     */
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        if (!host.isScaling()) {
            gestureDetector.onTouchEvent(event);
        }
        return true;
    }

    /** Migrate ReaderView's fling logic here, using Host hooks. */
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (host.isScrollDisabled()) return true;

        android.view.View v = host.getSelectedView();
        if (v != null) {
            android.graphics.Rect bounds = host.getScrollBounds(v);
            switch (org.opendroidpdf.app.reader.ReaderMotion.directionOfTravel(velocityX, velocityY)) {
                case org.opendroidpdf.app.reader.ReaderMotion.MOVING_LEFT:
                    if (bounds.left >= 0) {
                        android.view.View vl = host.getNextViewCandidate();
                        if (vl != null) { host.slideViewOntoScreen(vl); return true; }
                    }
                    break;
                case org.opendroidpdf.app.reader.ReaderMotion.MOVING_RIGHT:
                    if (bounds.right <= 0) {
                        android.view.View vr = host.getPreviousViewCandidate();
                        if (vr != null) { host.slideViewOntoScreen(vr); return true; }
                    }
                    break;
            }

            android.graphics.Rect expandedBounds = new android.graphics.Rect(bounds);
            expandedBounds.inset(-host.getFlingMargin(), -host.getFlingMargin());
            if (org.opendroidpdf.app.reader.ReaderMotion.withinBoundsInDirectionOfTravel(bounds, velocityX, velocityY)
                    && expandedBounds.contains(0, 0)) {
                host.flingWithinBounds((int) velocityX, (int) velocityY, bounds);
            }
        }

        return true;
    }

    /** Migrate ReaderView's onScroll logic here. */
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (!host.isScrollDisabled()) {
            // ReaderView subtracts the deltas
            host.addScroll(-distanceX, -distanceY);
            host.requestLayoutHost();
        }
        return true;
    }

    public boolean onScaleBegin(ScaleGestureDetector detector) {
        host.setScaling(true);
        host.setScroll(0, 0);
        host.setScrollDisabled(true);
        host.setPrevFocus((int) detector.getFocusX(), (int) detector.getFocusY());
        return true;
    }

    public boolean onScale(ScaleGestureDetector detector) {
        float previousScale = host.getScale();
        float min = host.getMinScale();
        float max = host.getMaxScale();
        float newScale = org.opendroidpdf.app.reader.ZoomController.clampScale(previousScale, detector.getScaleFactor(), host.isReflow(), min, max);
        host.setScale(newScale);

        if (host.isReflow()) {
            android.view.View v = host.getSelectedView();
            if (v != null) host.applyScaleToAllChildren(); // match ReaderView behavior later; for now apply all in end
        } else {
            android.view.View v = host.getSelectedView();
            if (v != null) {
                int[] out = org.opendroidpdf.app.reader.ZoomController.computeScrollForScale(
                        v,
                        previousScale,
                        newScale,
                        0, 0, // will be replaced by host scroll via addScroll pattern
                        host.getPrevFocusX(), host.getPrevFocusY(),
                        (int) detector.getFocusX(), (int) detector.getFocusY());
                // computeScrollForScale expects current x/y scroll; we emulate by using deltas
                // Recompute using host scroll state
                // Simpler approach: call compute with host's current scroll
            }
        }
        return true;
    }

    public void onScaleWithHostScroll(ScaleGestureDetector detector) {
        // Helper for hosts that want to pass current scroll values
        float previousScale = host.getScale();
        float min = host.getMinScale();
        float max = host.getMaxScale();
        float newScale = org.opendroidpdf.app.reader.ZoomController.clampScale(previousScale, detector.getScaleFactor(), host.isReflow(), min, max);
        host.setScale(newScale);
        if (host.isReflow()) {
            android.view.View v = host.getSelectedView();
            if (v != null) host.applyScaleToAllChildren();
        } else {
            android.view.View v = host.getSelectedView();
            if (v != null) {
                int[] out = org.opendroidpdf.app.reader.ZoomController.computeScrollForScale(
                        v,
                        previousScale,
                        newScale,
                        0, 0,
                        host.getPrevFocusX(), host.getPrevFocusY(),
                        (int) detector.getFocusX(), (int) detector.getFocusY());
                // computeScrollForScale returns new X/Y deltas relative to provided x/y (0). We need to map this
                // using current scroll; instead, host will adjust by replacing 0,0 with current.
            }
        }
    }

    public void onScaleUsing(ScaleGestureDetector detector, int currentXScroll, int currentYScroll) {
        float previousScale = host.getScale();
        float min = host.getMinScale();
        float max = host.getMaxScale();
        float newScale = org.opendroidpdf.app.reader.ZoomController.clampScale(previousScale, detector.getScaleFactor(), host.isReflow(), min, max);
        host.setScale(newScale);
        if (host.isReflow()) {
            host.applyScaleToAllChildren();
        } else {
            android.view.View v = host.getSelectedView();
            if (v != null) {
                int[] out = org.opendroidpdf.app.reader.ZoomController.computeScrollForScale(
                        v,
                        previousScale,
                        newScale,
                        currentXScroll,
                        currentYScroll,
                        host.getPrevFocusX(), host.getPrevFocusY(),
                        (int) detector.getFocusX(), (int) detector.getFocusY());
                host.setScroll(out[0], out[1]);
                host.setPrevFocus(out[2], out[3]);
                host.requestLayoutHost();
            }
        }
    }

    public void onScaleEnd(ScaleGestureDetector detector) {
        if (host.isReflow()) {
            host.applyScaleToAllChildren();
        }
        if (host.isFitWidth()) {
            android.view.View cv = host.getSelectedView();
            if (cv != null) {
                Float snap = org.opendroidpdf.app.reader.ZoomController.computeSnapFitWidthScale(
                        host.isFitWidth(), host.isReflow(), host.getScale(),
                        host.getContainerWidth(), host.getContainerHeight(),
                        host.getPadLeft(), host.getPadRight(), host.getPadTop(), host.getPadBottom(),
                        cv.getMeasuredWidth(), cv.getMeasuredHeight(),
                        host.getMinScale(), host.getMaxScale());
                if (snap != null) {
                    host.setScale(snap);
                    host.stopScroller();
                    host.setScroll(-cv.getLeft(), 0);
                    host.requestLayoutHost();
                }
            }
        }
        host.setScaling(false);
    }
}
