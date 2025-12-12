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
    // Ink/eraser prefs (px + colors) for convenience
    private int inkColor;
    private int eraserColor;
    private float inkThicknessPx;
    private float eraserThicknessPx;

    public int getPageNumber() { return pageNumber; }
    public Point getMinZoomSize() { return minZoomSize; }
    public float getSourceScale() { return sourceScale; }
    public float getDocRelXmin() { return docRelXmin; }
    public float getDocRelXmax() { return docRelXmax; }
    public int getInkColor() { return inkColor; }
    public int getEraserColor() { return eraserColor; }
    public float getInkThickness() { return inkThicknessPx; }
    public float getEraserThickness() { return eraserThicknessPx; }

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

    public void setInkColor(int color) { this.inkColor = color; }
    public void setEraserColor(int color) { this.eraserColor = color; }
    public void setInkThickness(float px) { this.inkThicknessPx = px; }
    public void setEraserThickness(float px) { this.eraserThicknessPx = px; }
}
