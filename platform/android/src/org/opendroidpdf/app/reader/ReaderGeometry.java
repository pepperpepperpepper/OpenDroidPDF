package org.opendroidpdf.app.reader;

import android.graphics.Point;
import android.graphics.Rect;

/**
 * Pure geometry helpers factored out of ReaderView to make that class smaller
 * and keep math testable without Android dependencies.
 */
public final class ReaderGeometry {
    private ReaderGeometry() {}

    public static float fillScreenScale(int containerWidth, int containerHeight,
                                        int padLeft, int padRight, int padTop, int padBottom,
                                        int viewMeasuredWidth, int viewMeasuredHeight) {
        float availW = (float)(containerWidth - padLeft - padRight);
        float availH = (float)(containerHeight - padTop - padBottom);
        return Math.min(availW / (float) viewMeasuredWidth,
                        availH / (float) viewMeasuredHeight);
    }

    public static float fillScreenScaleFromViews(android.view.View container,
                                                 android.view.View child) {
        return fillScreenScale(
                container.getWidth(), container.getHeight(),
                container.getPaddingLeft(), container.getPaddingRight(),
                container.getPaddingTop(), container.getPaddingBottom(),
                child.getMeasuredWidth(), child.getMeasuredHeight());
    }

    public static float scaleCorrection(int containerWidth, int padLeft, int padRight,
                                        int viewMeasuredWidth, float fillScreenScale) {
        float availW = (float)(containerWidth - padLeft - padRight);
        return availW / (viewMeasuredWidth * fillScreenScale);
    }

    public static float scaleCorrectionFromViews(android.view.View container,
                                                 android.view.View child,
                                                 float fillScreenScale) {
        return scaleCorrection(
                container.getWidth(),
                container.getPaddingLeft(), container.getPaddingRight(),
                child.getMeasuredWidth(),
                fillScreenScale);
    }

    /**
     * Computes the normalized scale (relative to fit-to-screen) for the
     * provided currentScale and geometry. Equivalent to ReaderView's
     * mScale/scaleCorrection with the same behavior.
     */
    public static float normalizedScale(float currentScale,
                                        int containerWidth, int containerHeight,
                                        int padLeft, int padRight, int padTop, int padBottom,
                                        int viewMeasuredWidth, int viewMeasuredHeight) {
        float fill = fillScreenScale(containerWidth, containerHeight,
                padLeft, padRight, padTop, padBottom,
                viewMeasuredWidth, viewMeasuredHeight);
        float corr = scaleCorrection(containerWidth, padLeft, padRight,
                viewMeasuredWidth, fill);
        return currentScale / corr;
    }

    public static Rect scrollBounds(int containerWidth, int containerHeight,
                                    int padLeft, int padRight, int padTop, int padBottom,
                                    int left, int top, int right, int bottom) {
        int xmin = containerWidth - right;
        int xmax = -left;
        int ymin = containerHeight - bottom;
        int ymax = -top;
        if (xmin > xmax) xmin = xmax = (xmin + xmax) / 2;
        if (ymin > ymax) ymin = ymax = (ymin + ymax) / 2;
        return new Rect(xmin, ymin, xmax, ymax);
    }

    public static Point correction(Rect bounds) {
        return new Point(Math.min(Math.max(0, bounds.left), bounds.right),
                         Math.min(Math.max(0, bounds.top), bounds.bottom));
    }

    public static Point subScreenSizeOffset(int containerWidth, int containerHeight,
                                            int viewMeasuredWidth, int viewMeasuredHeight) {
        return new Point(Math.max((containerWidth - viewMeasuredWidth) / 2, 0),
                         Math.max((containerHeight - viewMeasuredHeight) / 2, 0));
    }

    /**
     * Convenience wrapper around ZoomController.computeSnapFitWidthScale that pulls
     * the required geometry from the container and child views.
     */
    public static Float computeSnapFitWidthScaleFromViews(boolean fitWidth,
                                                          boolean reflow,
                                                          float currentScale,
                                                          android.view.View container,
                                                          android.view.View child,
                                                          float minScale,
                                                          float maxScale) {
        return org.opendroidpdf.app.reader.ZoomController.computeSnapFitWidthScale(
                fitWidth,
                reflow,
                currentScale,
                container.getWidth(), container.getHeight(),
                container.getPaddingLeft(), container.getPaddingRight(),
                container.getPaddingTop(), container.getPaddingBottom(),
                child.getMeasuredWidth(), child.getMeasuredHeight(),
                minScale, maxScale);
    }
}
