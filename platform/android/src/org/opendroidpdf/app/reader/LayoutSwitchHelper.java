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
    public static View handleSwitches(Host h, PagingAxis pagingAxis) {
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
            if (shouldMoveNext(h, cv, cvOffset, pagingAxis)) {
                h.postUnsettle(cv);
                h.onMoveOffChild(h.currentIndex());
                h.setCurrentIndex(h.currentIndex() + 1);
                h.onMoveToChild(h.currentIndex());
                h.postSelf();
            }
            if (shouldMovePrev(h, cv, cvOffset, pagingAxis)) {
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

    private static boolean shouldMoveNext(Host h, View cv, Point cvOffset, PagingAxis pagingAxis) {
        int gapHalf = h.gap() / 2;
        if (pagingAxis == PagingAxis.VERTICAL) {
            int halfHeight = h.height() / 2;
            return cv.getTop() + cv.getMeasuredHeight() + cvOffset.y + gapHalf + h.scrollState().getY() < halfHeight
                    && h.currentIndex() + 1 < h.adapter().getCount();
        }
        int halfWidth = h.width() / 2;
        return cv.getLeft() + cv.getMeasuredWidth() + cvOffset.x + gapHalf + h.scrollState().getX() < halfWidth
                && h.currentIndex() + 1 < h.adapter().getCount();
    }

    private static boolean shouldMovePrev(Host h, View cv, Point cvOffset, PagingAxis pagingAxis) {
        int gapHalf = h.gap() / 2;
        if (pagingAxis == PagingAxis.VERTICAL) {
            int halfHeight = h.height() / 2;
            return cv.getTop() - cvOffset.y - gapHalf + h.scrollState().getY() >= halfHeight && h.currentIndex() > 0;
        }
        int halfWidth = h.width() / 2;
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
    public static LayoutResult layoutCurrentAndNeighbors(LayoutHost h, View cv, int currentIndex, PagingAxis pagingAxis) {
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
            if (org.opendroidpdf.BuildConfig.DEBUG && (corr.x != 0 || corr.y != 0)) {
                android.util.Log.d("LayoutSwitchHelper", "settleCorrection corr=(" + corr.x + "," + corr.y + ")"
                        + " cv=(" + cvLeft + "," + cvTop + " " + (cvRight - cvLeft) + "x" + (cvBottom - cvTop) + ")"
                        + " container=" + h.width() + "x" + h.height());
            }
            cvRight  += corr.x;
            cvLeft   += corr.x;
            cvTop    += corr.y;
            cvBottom += corr.y;
        }

        cv.layout(cvLeft, cvTop, cvRight, cvBottom);
        if (!h.isUserInteracting() && h.scroller().isFinished()) h.postSettle(cv);

        Point cvOffset = h.subScreenSizeOffset(cv);
        View prevView = null;
        View nextView = null;
        if (pagingAxis == PagingAxis.VERTICAL) {
            if (currentIndex > 0) {
                prevView = h.getOrCreateChild(currentIndex - 1);
                h.measureChild(prevView);
                Point prevOffset = h.subScreenSizeOffset(prevView);
                int gap = prevOffset.y + h.gap() + cvOffset.y;
                prevView.layout((cvRight + cvLeft - prevView.getMeasuredWidth()) / 2,
                        cvTop - prevView.getMeasuredHeight() - gap,
                        (cvRight + cvLeft + prevView.getMeasuredWidth()) / 2,
                        cvTop - gap);
            }
            if (h.adapter() != null && currentIndex + 1 < h.adapter().getCount()) {
                nextView = h.getOrCreateChild(currentIndex + 1);
                h.measureChild(nextView);
                Point nextOffset = h.subScreenSizeOffset(nextView);
                int gap = cvOffset.y + h.gap() + nextOffset.y;
                nextView.layout((cvRight + cvLeft - nextView.getMeasuredWidth()) / 2,
                        cvBottom + gap,
                        (cvRight + cvLeft + nextView.getMeasuredWidth()) / 2,
                        cvBottom + nextView.getMeasuredHeight() + gap);
            }
        } else {
            if (currentIndex > 0) {
                prevView = h.getOrCreateChild(currentIndex - 1);
                h.measureChild(prevView);
                Point leftOffset = h.subScreenSizeOffset(prevView);
                int gap = leftOffset.x + h.gap() + cvOffset.x;
                prevView.layout(cvLeft - prevView.getMeasuredWidth() - gap,
                        (cvBottom + cvTop - prevView.getMeasuredHeight())/2,
                        cvLeft - gap,
                        (cvBottom + cvTop + prevView.getMeasuredHeight())/2);
            }
            if (h.adapter() != null && currentIndex + 1 < h.adapter().getCount()) {
                nextView = h.getOrCreateChild(currentIndex + 1);
                h.measureChild(nextView);
                Point rightOffset = h.subScreenSizeOffset(nextView);
                int gap = cvOffset.x + h.gap() + rightOffset.x;
                nextView.layout(cvRight + gap,
                        (cvBottom + cvTop - nextView.getMeasuredHeight())/2,
                        cvRight + nextView.getMeasuredWidth() + gap,
                        (cvBottom + cvTop + nextView.getMeasuredHeight())/2);
            }
        }
        return new LayoutResult(cvLeft, cvTop, cvRight, cvBottom, prevView, nextView);
    }

    public static final class LayoutResult {
        public final int left, top, right, bottom;
        public final View previousView;
        public final View nextView;
        public LayoutResult(int l, int t, int r, int b, View previousView, View nextView) {
            this.left = l; this.top = t; this.right = r; this.bottom = b;
            this.previousView = previousView; this.nextView = nextView;
        }
    }
}
