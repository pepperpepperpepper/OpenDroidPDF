package org.opendroidpdf.app.reader;

import android.view.View;

/**
 * Math utilities for pinch-zoom handling extracted from ReaderView.
 */
public final class ZoomController {
    private ZoomController() {}

    public static float clampScale(float currentScale,
                                   float detectorScaleFactor,
                                   boolean reflow,
                                   float minScale,
                                   float maxScale) {
        float scale = currentScale * detectorScaleFactor;
        if (scale < minScale) scale = minScale;
        if (scale > maxScale) scale = maxScale;
        return scale;
    }

    /**
     * Compute scroll adjustments to keep the zoom focus under the same screen point.
     * Returns an int[4] of {newXScroll, newYScroll, newPrevFocusX, newPrevFocusY}.
     */
    public static int[] computeScrollForScale(View v,
                                              float previousScale,
                                              float newScale,
                                              int xScroll,
                                              int yScroll,
                                              int previousFocusX,
                                              int previousFocusY,
                                              int detectorFocusX,
                                              int detectorFocusY) {
        float factor = newScale / previousScale;
        int viewFocusX = detectorFocusX - (v.getLeft() + xScroll);
        int viewFocusY = detectorFocusY - (v.getTop() + yScroll);
        xScroll += viewFocusX - (int)(viewFocusX * factor) - previousFocusX + detectorFocusX;
        yScroll += viewFocusY - (int)(viewFocusY * factor) - previousFocusY + detectorFocusY;
        previousFocusX = detectorFocusX;
        previousFocusY = detectorFocusY;
        return new int[]{xScroll, yScroll, previousFocusX, previousFocusY};
    }

    /**
     * Computes a snap-to-fit-width target scale if conditions match. Returns null when no snap should occur.
     */
    public static Float computeSnapFitWidthScale(boolean fitWidthEnabled,
                                                 boolean reflow,
                                                 float currentScale,
                                                 int containerWidth,
                                                 int containerHeight,
                                                 int padLeft, int padRight, int padTop, int padBottom,
                                                 int viewMeasuredWidth, int viewMeasuredHeight,
                                                 float minScale, float maxScale) {
        if (!fitWidthEnabled) return null;
        float fill = ReaderGeometry.fillScreenScale(
                containerWidth, containerHeight,
                padLeft, padRight, padTop, padBottom,
                viewMeasuredWidth, viewMeasuredHeight);
        float fitWidthScale = (float) containerWidth / (viewMeasuredWidth * fill);
        if (Math.abs(currentScale - fitWidthScale) <= 0.15f && fitWidthScale >= 1.15f) {
            float clamped = Math.min(Math.max(fitWidthScale, minScale), maxScale);
            return clamped;
        }
        return null;
    }
}
