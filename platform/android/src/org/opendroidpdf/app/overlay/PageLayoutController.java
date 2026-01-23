package org.opendroidpdf.app.overlay;

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
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
            // Only hide the entire view when the HQ patch is actually visible. The HQ view can be
            // temporarily set to GONE (e.g., after a reset) while still holding an old drawable;
            // in that case hiding the entire view would leave the page blank/white.
            if (hqView != null && hqView.getVisibility() == View.VISIBLE && hqView.getDrawable() != null &&
                    hqView.getLeft() == left && hqView.getTop() == top &&
                    hqView.getRight() == right && hqView.getBottom() == bottom) {
                entireView.setVisibility(View.GONE);
            } else if (pageMinZoomSize != null) {
                int basisW = pageMinZoomSize.x;
                int basisH = pageMinZoomSize.y;
                try {
                    Rect area = entireView.getArea();
                    if (area != null && area.width() > 0 && area.height() > 0) {
                        basisW = area.width();
                        basisH = area.height();
                    }
                } catch (Throwable ignore) {
                }
                entireMatrix.setScale(w / (float) basisW, h / (float) basisH);
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
