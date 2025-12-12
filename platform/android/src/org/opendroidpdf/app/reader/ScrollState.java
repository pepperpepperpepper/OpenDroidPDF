package org.opendroidpdf.app.reader;

/**
 * Minimal scroll/focus state holder extracted from ReaderView.
 * Starts with pinch focus positions; can expand to hold more
 * scroll-related fields in subsequent slices.
 */
public final class ScrollState {
    private int previousFocusX;
    private int previousFocusY;
    private int scrollerLastX;
    private int scrollerLastY;

    // Pending normalized/doc-relative scroll & scale requests, applied in onLayout.
    private float newNormalizedScale;
    private boolean hasNewNormalizedScale;
    private float newNormalizedXScroll;
    private float newNormalizedYScroll;
    private boolean hasNewNormalizedXScroll;
    private boolean hasNewNormalizedYScroll;
    private float newDocRelXScroll;
    private float newDocRelYScroll;
    private boolean hasNewDocRelXScroll;
    private boolean hasNewDocRelYScroll;
    private boolean nextScrollWithCenter;

    public int getPrevFocusX() { return previousFocusX; }
    public int getPrevFocusY() { return previousFocusY; }
    public void setPrevFocus(int x, int y) { previousFocusX = x; previousFocusY = y; }

    public int getScrollerLastX() { return scrollerLastX; }
    public int getScrollerLastY() { return scrollerLastY; }
    public void setScrollerLast(int x, int y) { scrollerLastX = x; scrollerLastY = y; }

    // Transient scroll deltas applied during layout/gestures
    private int xScroll;
    private int yScroll;
    public int getX() { return xScroll; }
    public int getY() { return yScroll; }
    public void setScroll(int x, int y) { xScroll = x; yScroll = y; }
    public void addScroll(float dx, float dy) { xScroll += (int)dx; yScroll += (int)dy; }
    public void resetScroll() { xScroll = 0; yScroll = 0; }

    public void requestNormalizedScale(float normalizedScale) {
        this.newNormalizedScale = normalizedScale;
        this.hasNewNormalizedScale = true;
    }
    public boolean consumeHasNewNormalizedScale() {
        boolean b = hasNewNormalizedScale; hasNewNormalizedScale = false; return b;
    }
    public float getNewNormalizedScale() { return newNormalizedScale; }

    public void requestNormalizedX(float x) { this.newNormalizedXScroll = x; this.hasNewNormalizedXScroll = true; }
    public void requestNormalizedY(float y) { this.newNormalizedYScroll = y; this.hasNewNormalizedYScroll = true; }
    public boolean hasNewNormalizedX() { return hasNewNormalizedXScroll; }
    public boolean hasNewNormalizedY() { return hasNewNormalizedYScroll; }
    public float getNewNormalizedX() { return newNormalizedXScroll; }
    public float getNewNormalizedY() { return newNormalizedYScroll; }
    public void clearNewNormalizedX() { hasNewNormalizedXScroll = false; }
    public void clearNewNormalizedY() { hasNewNormalizedYScroll = false; }

    public void requestDocRelX(float dx) { this.newDocRelXScroll = dx; this.hasNewDocRelXScroll = true; }
    public void requestDocRelY(float dy) { this.newDocRelYScroll = dy; this.hasNewDocRelYScroll = true; }
    public boolean consumeHasNewDocRelX() { boolean b = hasNewDocRelXScroll; hasNewDocRelXScroll = false; return b; }
    public boolean consumeHasNewDocRelY() { boolean b = hasNewDocRelYScroll; hasNewDocRelYScroll = false; return b; }
    public float getNewDocRelX() { return newDocRelXScroll; }
    public float getNewDocRelY() { return newDocRelYScroll; }

    public void requestNextScrollWithCenter() { nextScrollWithCenter = true; }
    public boolean consumeNextScrollWithCenter() { boolean b = nextScrollWithCenter; nextScrollWithCenter = false; return b; }
}
