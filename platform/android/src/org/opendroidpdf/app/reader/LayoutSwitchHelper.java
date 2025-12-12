package org.opendroidpdf.app.reader;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.widget.Adapter;
import android.widget.Scroller;

/**
 * Handles view switching/layout positioning to shrink ReaderView.
 */
public final class LayoutSwitchHelper {
    private LayoutSwitchHelper() {}

    public interface Host {
        Adapter adapter();
        AdapterState adapterState();
        int currentIndex();
        void setCurrentIndex(int idx);
        View currentView();
        View getOrCreateChild(int index);
        void onMoveOffChild(int index);
        void onMoveToChild(int index);
        void onUnsettle(View v);
        void postSelf();
        void postSettle(View v);
        void postUnsettle(View v);
        Point subScreenSizeOffset(View v);
        ScrollState scrollState();
        Scroller scroller();
        int width();
        int height();
        int paddingLeft();
        int paddingRight();
        int paddingTop();
        int paddingBottom();
        boolean isUserInteracting();
        int gap();
    }

    /**
     * Handles pending/potential view switches and returns the active current view for layout.
     */
    public static View handleSwitches(Host h) {
        View cv = h.currentView();

        if (h.adapterState().hasPending()) {
            if (cv != null) h.onUnsettle(cv);
            h.scrollState().resetScroll();
            h.onMoveOffChild(h.currentIndex());
            h.setCurrentIndex(h.adapterState().getPendingIndex());
            h.onMoveToChild(h.currentIndex());
            h.adapterState().clearPending();
            h.postSelf();
        } else if (cv != null && maySwitchView(h)) {
            Point cvOffset = h.subScreenSizeOffset(cv);
            if (shouldMoveNext(h, cv, cvOffset)) {
                h.postUnsettle(cv);
                h.onMoveOffChild(h.currentIndex());
                h.setCurrentIndex(h.currentIndex() + 1);
                h.onMoveToChild(h.currentIndex());
                h.postSelf();
            }
            if (shouldMovePrev(h, cv, cvOffset)) {
                h.postUnsettle(cv);
                h.onMoveOffChild(h.currentIndex());
                h.setCurrentIndex(h.currentIndex() - 1);
                h.onMoveToChild(h.currentIndex());
                h.postSelf();
            }
        }
        return h.currentView();
    }

    private static boolean maySwitchView(Host h) { return h.adapter() != null && h.adapter().getCount() > 0; }

    private static boolean shouldMoveNext(Host h, View cv, Point cvOffset) {
        int halfWidth = h.width() / 2;
        int gapHalf = h.gap() / 2;
        return cv.getLeft() + cv.getMeasuredWidth() + cvOffset.x + gapHalf + h.scrollState().getX() < halfWidth
                && h.currentIndex() + 1 < h.adapter().getCount();
    }

    private static boolean shouldMovePrev(Host h, View cv, Point cvOffset) {
        int halfWidth = h.width() / 2;
        int gapHalf = h.gap() / 2;
        return cv.getLeft() - cvOffset.x - gapHalf + h.scrollState().getX() >= halfWidth && h.currentIndex() > 0;
    }

    public interface LayoutHost {
        int paddingLeft();
        int paddingRight();
        int paddingTop();
        int paddingBottom();
        int width();
        int height();
        ScrollState scrollState();
        boolean isUserInteracting();
        Scroller scroller();
        void postSettle(View v);
        Point subScreenSizeOffset(View v);
        View getOrCreateChild(int index);
        Adapter adapter();
        int gap();
        void measureChild(View v);
    }

    /**
     * Computes and applies layout for current/neighbor views. Returns a LayoutResult with bounds.
     */
    public static LayoutResult layoutCurrentAndNeighbors(LayoutHost h, View cv, int currentIndex) {
        int cvLeft, cvRight, cvTop, cvBottom;

        // Apply pending normalized scroll/scale handled in ReaderView before call.
        cvLeft = cv.getLeft() + h.scrollState().getX();
        cvTop  = cv.getTop()  + h.scrollState().getY();
        h.scrollState().resetScroll();

        // Recompute bounds after scale applied by caller.
        cvRight  = cvLeft + cv.getMeasuredWidth();
        cvBottom = cvTop  + cv.getMeasuredHeight();

        if (!h.isUserInteracting() && h.scroller().isFinished()) {
            Point corr = org.opendroidpdf.app.reader.ReaderGeometry.correction(
                    org.opendroidpdf.app.reader.ReaderGeometry.scrollBounds(
                            h.width(), h.height(),
                            h.paddingLeft(), h.paddingRight(), h.paddingTop(), h.paddingBottom(),
                            cvLeft, cvTop, cvRight, cvBottom));
            cvRight  += corr.x;
            cvLeft   += corr.x;
            cvTop    += corr.y;
            cvBottom += corr.y;
        }

        cv.layout(cvLeft, cvTop, cvRight, cvBottom);
        if (!h.isUserInteracting() && h.scroller().isFinished()) h.postSettle(cv);

        Point cvOffset = h.subScreenSizeOffset(cv);
        View lv = null, rv = null;
        if (currentIndex > 0) {
            lv = h.getOrCreateChild(currentIndex - 1);
            h.measureChild(lv);
            Point leftOffset = h.subScreenSizeOffset(lv);
            int gap = leftOffset.x + h.gap() + cvOffset.x;
            lv.layout(cvLeft - lv.getMeasuredWidth() - gap,
                    (cvBottom + cvTop - lv.getMeasuredHeight())/2,
                    cvLeft - gap,
                    (cvBottom + cvTop + lv.getMeasuredHeight())/2);
        }
        if (h.adapter() != null && currentIndex + 1 < h.adapter().getCount()) {
            rv = h.getOrCreateChild(currentIndex + 1);
            h.measureChild(rv);
            Point rightOffset = h.subScreenSizeOffset(rv);
            int gap = cvOffset.x + h.gap() + rightOffset.x;
            rv.layout(cvRight + gap,
                    (cvBottom + cvTop - rv.getMeasuredHeight())/2,
                    cvRight + rv.getMeasuredWidth() + gap,
                    (cvBottom + cvTop + rv.getMeasuredHeight())/2);
        }
        return new LayoutResult(cvLeft, cvTop, cvRight, cvBottom, lv, rv);
    }

    public static final class LayoutResult {
        public final int left, top, right, bottom;
        public final View leftView;
        public final View rightView;
        public LayoutResult(int l, int t, int r, int b, View lv, View rv) {
            this.left = l; this.top = t; this.right = r; this.bottom = b;
            this.leftView = lv; this.rightView = rv;
        }
    }
}
