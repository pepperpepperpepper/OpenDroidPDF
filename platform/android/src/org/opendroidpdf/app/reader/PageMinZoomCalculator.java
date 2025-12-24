package org.opendroidpdf.app.reader;

import android.graphics.Point;
import android.graphics.PointF;

/**
 * Computes the minimum-zoom layout for a page given its unscaled document size and
 * the available parent dimensions.
 *
 * <p>This is kept outside the view so PageView can delegate layout math to a
 * dedicated reader-zone component.</p>
 */
public final class PageMinZoomCalculator {

    private PageMinZoomCalculator() {}

    public static Result compute(PointF pageSize, float parentWidthPx, float parentHeightPx) {
        float w = (pageSize != null && pageSize.x > 0) ? pageSize.x : parentWidthPx;
        float h = (pageSize != null && pageSize.y > 0) ? pageSize.y : parentHeightPx;
        if (w <= 0) w = 1f;
        if (h <= 0) h = 1f;

        float sourceScale = Math.min(parentWidthPx / w, parentHeightPx / h);
        Point minZoomSize = new Point((int) (w * sourceScale), (int) (h * sourceScale));
        return new Result(sourceScale, minZoomSize);
    }

    public static final class Result {
        public final float sourceScale;
        public final Point minZoomSize;

        public Result(float sourceScale, Point minZoomSize) {
            this.sourceScale = sourceScale;
            this.minZoomSize = minZoomSize;
        }
    }
}

