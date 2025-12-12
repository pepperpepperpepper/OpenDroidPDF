package org.opendroidpdf.app.overlay;

import android.graphics.Point;
import android.view.View;
import android.view.ViewGroup;

import org.opendroidpdf.app.helpers.BusyIndicatorAdapter;
import org.opendroidpdf.app.reader.PageState;

/**
 * Handles PageView measurement so the view class can stay lean.
 */
public final class PageMeasureHelper {
    private PageMeasureHelper() {}

    public static Point measure(PageState pageState,
                                ViewGroup parent,
                                org.opendroidpdf.app.overlay.PageOverlayView overlayView,
                                BusyIndicatorAdapter busyIndicator,
                                View view,
                                int widthMeasureSpec,
                                int heightMeasureSpec) {
        Point minZoom = pageState.getMinZoomSize();

        int fallbackW = view.getResources().getDisplayMetrics().widthPixels;
        int fallbackH = view.getResources().getDisplayMetrics().heightPixels;
        int parentW = (parent != null && parent.getWidth() > 0) ? parent.getWidth() : fallbackW;
        int parentH = (parent != null && parent.getHeight() > 0) ? parent.getHeight() : fallbackH;

        int x = resolve(widthMeasureSpec, minZoom != null ? minZoom.x : parentW);
        int y = resolve(heightMeasureSpec, minZoom != null ? minZoom.y : parentH);

        busyIndicator.measureCenter(parentW, parentH);

        if (overlayView != null) {
            overlayView.measure(View.MeasureSpec.makeMeasureSpec(parentW, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(parentH, View.MeasureSpec.AT_MOST));
        }

        return new Point(x, y);
    }

    private static int resolve(int measureSpec, int fallbackSize) {
        switch (View.MeasureSpec.getMode(measureSpec)) {
            case View.MeasureSpec.UNSPECIFIED:
                return fallbackSize;
            default:
                return View.MeasureSpec.getSize(measureSpec);
        }
    }
}
