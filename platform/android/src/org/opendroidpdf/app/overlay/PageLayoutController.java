package org.opendroidpdf.app.overlay;

import android.graphics.Matrix;
import android.graphics.Point;
import android.view.View;

import org.opendroidpdf.app.helpers.BusyIndicatorHelper;

/**
 * Centralizes PageView child layout: entire bitmap view, HQ patch, overlay,
 * and busy indicator placement. Keeps PageView thinner and easier to test.
 */
public final class PageLayoutController {

    private PageLayoutController() {}

    public static void layoutAll(
            PagePatchView entireView,
            PagePatchView hqView,
            View overlayView,
            Matrix entireMatrix,
            int left,
            int top,
            int right,
            int bottom,
            Point pageMinZoomSize,
            BusyIndicatorHelper.Handle busyHandle,
            boolean changed)
    {
        final int w = right - left;
        final int h = bottom - top;

        // Entire view is hidden when fully covered by the HQ patch.
        if (entireView != null) {
            if (hqView != null && hqView.getDrawable() != null &&
                    hqView.getLeft() == left && hqView.getTop() == top &&
                    hqView.getRight() == right && hqView.getBottom() == bottom) {
                entireView.setVisibility(View.GONE);
            } else if (pageMinZoomSize != null) {
                entireMatrix.setScale(w / (float) pageMinZoomSize.x, h / (float) pageMinZoomSize.y);
                entireView.setImageMatrix(entireMatrix);
                entireView.layout(0, 0, w, h);
                entireView.setVisibility(View.VISIBLE);
            }
        }

        if (overlayView != null) {
            overlayView.layout(-left, -top,
                    -left + overlayView.getMeasuredWidth(),
                    -top + overlayView.getMeasuredHeight());
            if (changed) overlayView.invalidate();
        }

        BusyIndicatorHelper.layoutCenter(busyHandle, w, h);
    }
}

