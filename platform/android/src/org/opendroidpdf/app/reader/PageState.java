package org.opendroidpdf.app.reader;

import android.graphics.Point;

/**
 * Lightweight container for current page state used by controllers/renderers.
 * Introduced to reduce PageView surface and concentrate shared values.
 */
public final class PageState {
    private int pageNumber = 0;
    private Point minZoomSize = null; // size in view pixels at minimum zoom
    private float sourceScale = 1f;   // scale mapping doc -> min-zoom view
    // Document-relative X bounds observed during selection; used for smart selection.
    private float docRelXmin = Float.POSITIVE_INFINITY;
    private float docRelXmax = Float.NEGATIVE_INFINITY;

    public int getPageNumber() { return pageNumber; }
    public Point getMinZoomSize() { return minZoomSize; }
    public float getSourceScale() { return sourceScale; }
    public float getDocRelXmin() { return docRelXmin; }
    public float getDocRelXmax() { return docRelXmax; }

    /**
     * Computes the current page scale mapping document coordinates into view pixels.
     * This is derived from the minimum-zoom (fit) scale and the current view width.
     */
    public float computeScale(int viewWidthPx) {
        if (minZoomSize == null || minZoomSize.x == 0) return 1f;
        return sourceScale * (float) viewWidthPx / (float) minZoomSize.x;
    }

    public void set(int pageNumber, Point minZoomSize, float sourceScale) {
        this.pageNumber = pageNumber;
        this.minZoomSize = minZoomSize;
        this.sourceScale = sourceScale;
        resetDocRelXBounds();
    }

    public void resetDocRelXBounds() {
        docRelXmin = Float.POSITIVE_INFINITY;
        docRelXmax = Float.NEGATIVE_INFINITY;
    }

    public void updateDocRelXBounds(float docRelX) {
        if (docRelX > docRelXmax) docRelXmax = docRelX;
        if (docRelX < docRelXmin) docRelXmin = docRelX;
    }
}
