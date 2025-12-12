package org.opendroidpdf.app.content;

import android.graphics.Point;
import android.graphics.PointF;

import org.opendroidpdf.app.reader.PageState;

/**
 * Small helper to set/reset page state fields, keeping PageView slimmer.
 */
public final class PageStateUpdater {
    private PageStateUpdater() {}

    public static void set(PageState state, int pageNumber, Point minZoomSize, float sourceScale) {
        if (state == null) return;
        state.set(pageNumber, minZoomSize, sourceScale);
    }

    public static void resetSelection(PageState state) {
        if (state != null) state.resetDocRelXBounds();
    }
}
