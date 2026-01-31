package org.opendroidpdf;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;

final class ReaderViewLayoutEngine {
    private final ReaderView view;

    ReaderViewLayoutEngine(ReaderView view) {
        this.view = view;
    }

    Rect computeScrollBounds(View v) {
        if (view.mScrollMode == org.opendroidpdf.app.reader.ScrollMode.CONTINUOUS) {
            return computeContinuousScrollBounds();
        }
        return org.opendroidpdf.app.reader.ReaderGeometry.scrollBounds(
                view.getWidth(), view.getHeight(),
                view.getPaddingLeft(), view.getPaddingRight(), view.getPaddingTop(), view.getPaddingBottom(),
                v.getLeft() + view.scrollState.getX() - view.getPaddingLeft(),
                v.getTop() + view.scrollState.getY() - view.getPaddingTop(),
                v.getLeft() + v.getMeasuredWidth() + view.scrollState.getX() + view.getPaddingRight(),
                v.getTop() + v.getMeasuredHeight() + view.scrollState.getY() + view.getPaddingBottom());
    }

    private Rect computeContinuousScrollBounds() {
        // In continuous mode, allow the user to stop between pages without snapping by treating
        // the visible page stack (current + neighbors) as a single scrolling content rect.
        int n = view.getChildCount();
        if (n <= 0) {
            View cv = view.getSelectedView();
            if (cv == null) return new Rect(0, 0, 0, 0);
            return org.opendroidpdf.app.reader.ReaderGeometry.scrollBounds(
                    view.getWidth(), view.getHeight(),
                    view.getPaddingLeft(), view.getPaddingRight(), view.getPaddingTop(), view.getPaddingBottom(),
                    cv.getLeft() + view.scrollState.getX() - view.getPaddingLeft(),
                    cv.getTop() + view.scrollState.getY() - view.getPaddingTop(),
                    cv.getLeft() + cv.getMeasuredWidth() + view.scrollState.getX() + view.getPaddingRight(),
                    cv.getTop() + cv.getMeasuredHeight() + view.scrollState.getY() + view.getPaddingBottom());
        }

        int scrollX = view.scrollState.getX();
        int scrollY = view.scrollState.getY();

        int minLeft = Integer.MAX_VALUE;
        int minTop = Integer.MAX_VALUE;
        int maxRight = Integer.MIN_VALUE;
        int maxBottom = Integer.MIN_VALUE;

        for (int i = 0; i < n; i++) {
            View child = view.getChildAt(i);
            if (child == null) continue;

            int l = child.getLeft() + scrollX;
            int t = child.getTop() + scrollY;
            int r = child.getRight() + scrollX;
            int b = child.getBottom() + scrollY;

            if (l < minLeft) minLeft = l;
            if (t < minTop) minTop = t;
            if (r > maxRight) maxRight = r;
            if (b > maxBottom) maxBottom = b;
        }

        if (minLeft == Integer.MAX_VALUE) {
            return new Rect(0, 0, 0, 0);
        }

        return org.opendroidpdf.app.reader.ReaderGeometry.scrollBounds(
                view.getWidth(), view.getHeight(),
                view.getPaddingLeft(), view.getPaddingRight(), view.getPaddingTop(), view.getPaddingBottom(),
                minLeft - view.getPaddingLeft(),
                minTop - view.getPaddingTop(),
                maxRight + view.getPaddingRight(),
                maxBottom + view.getPaddingBottom());
    }

    Point subScreenSizeOffset(View v) {
        Point offset = org.opendroidpdf.app.reader.ReaderGeometry.subScreenSizeOffset(
                view.getWidth(), view.getHeight(), v.getMeasuredWidth(), v.getMeasuredHeight());
        if (view.mScrollMode == org.opendroidpdf.app.reader.ScrollMode.CONTINUOUS) {
            // In continuous mode we stack pages vertically with a small fixed gap; vertical centering
            // offsets would create large "dead" gaps between pages on tall screens.
            return new Point(offset.x, 0);
        }
        return offset;
    }

    float getNormalizedScale() {
        View cv = view.getSelectedView();
        if (cv != null) {
            return org.opendroidpdf.app.reader.ReaderGeometry.normalizedScale(
                    view.mScale,
                    view.getWidth(), view.getHeight(),
                    view.getPaddingLeft(), view.getPaddingRight(), view.getPaddingTop(), view.getPaddingBottom(),
                    cv.getMeasuredWidth(), cv.getMeasuredHeight());
        }
        return 1f;
    }

    float getNormalizedXScroll() {
        View cv = view.getSelectedView();
        if (cv != null) {
            return org.opendroidpdf.app.reader.NormalizedScroll.normalizedX(
                    cv.getLeft(), view.getPaddingLeft(), cv.getMeasuredWidth());
        }
        return 0;
    }

    float getNormalizedYScroll() {
        View cv = view.getSelectedView();
        if (cv != null) {
            return org.opendroidpdf.app.reader.NormalizedScroll.normalizedY(
                    cv.getTop(), view.getPaddingTop(), cv.getMeasuredHeight());
        }
        return 0;
    }

    void applyPendingScrollAndScale(View cv) {
        if (view.mReflow) return;

        float scale_factor = view.mReflow ? ReaderView.REFLOW_SCALE_FACTOR : 1.0f;
        float min_scale = ReaderView.MIN_SCALE * scale_factor;
        float max_scale = ReaderView.MAX_SCALE * scale_factor;
        float scale = org.opendroidpdf.app.reader.ReaderGeometry.fillScreenScaleFromViews(view, cv);
        float scaleCorrection = org.opendroidpdf.app.reader.ReaderGeometry.scaleCorrectionFromViews(view, cv, scale);

        if (view.scrollState.consumeHasNewNormalizedScale()) {
            view.mScale = Math.min(
                    Math.max(view.scrollState.getNewNormalizedScale() * scaleCorrection, min_scale),
                    max_scale);
        }
        if (view.scrollState.consumeHasNewDocRelX()) {
            float normX = org.opendroidpdf.app.reader.NormalizedScroll.normalizedFromDocRelX(
                    view.scrollState.getNewDocRelX(),
                    ((PageView) cv).getScale(),
                    cv.getMeasuredWidth(),
                    view.mScale,
                    scale);
            view.scrollState.requestNormalizedX(normX);
        }
        if (view.scrollState.consumeHasNewDocRelY()) {
            float normY = org.opendroidpdf.app.reader.NormalizedScroll.normalizedFromDocRelY(
                    view.scrollState.getNewDocRelY(),
                    ((PageView) cv).getScale(),
                    cv.getMeasuredHeight(),
                    view.mScale,
                    scale);
            view.scrollState.requestNormalizedY(normY);
        }

        if (view.scrollState.hasNewNormalizedX() || view.scrollState.hasNewNormalizedY()) {
            if (org.opendroidpdf.BuildConfig.DEBUG) {
                android.util.Log.d(
                        "ReaderView",
                        "applyNormalizedScroll newX="
                                + (view.scrollState.hasNewNormalizedX() ? view.scrollState.getNewNormalizedX() : "(keep)")
                                + " newY=" + (view.scrollState.hasNewNormalizedY() ? view.scrollState.getNewNormalizedY() : "(keep)")
                                + " scale=" + view.mScale);
            }

            // Preset to the current values
            int XScroll = org.opendroidpdf.app.reader.NormalizedScroll.presetPixelsFromNormalized(
                    getNormalizedXScroll(), cv.getMeasuredWidth(), view.mScale, scale);
            int YScroll = org.opendroidpdf.app.reader.NormalizedScroll.presetPixelsFromNormalized(
                    getNormalizedYScroll(), cv.getMeasuredHeight(), view.mScale, scale);

            if (view.scrollState.hasNewNormalizedX()) {
                XScroll = org.opendroidpdf.app.reader.NormalizedScroll.targetPixelsFromNormalized(
                        view.scrollState.getNewNormalizedX(),
                        cv.getMeasuredWidth(),
                        view.mScale,
                        scale,
                        view.getPaddingLeft());
                view.scrollState.clearNewNormalizedX();
            }
            if (view.scrollState.hasNewNormalizedY()) {
                YScroll = org.opendroidpdf.app.reader.NormalizedScroll.targetPixelsFromNormalized(
                        view.scrollState.getNewNormalizedY(),
                        cv.getMeasuredHeight(),
                        view.mScale,
                        scale,
                        view.getPaddingTop());
                view.scrollState.clearNewNormalizedY();
            }

            if (view.scrollState.consumeNextScrollWithCenter()) {
                XScroll += view.getWidth() / 2;
                YScroll += view.getHeight() / 2;
            }

            view.mScroller.forceFinished(true);
            view.scrollState.setScrollerLast(0, 0);
            view.scrollState.setScroll(XScroll - cv.getLeft(), YScroll - cv.getTop());
        }
    }
}
