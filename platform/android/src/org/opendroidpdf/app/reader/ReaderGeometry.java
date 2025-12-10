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

    public static float scaleCorrection(int containerWidth, int padLeft, int padRight,
                                        int viewMeasuredWidth, float fillScreenScale) {
        float availW = (float)(containerWidth - padLeft - padRight);
        return availW / (viewMeasuredWidth * fillScreenScale);
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
}

