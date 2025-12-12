package org.opendroidpdf;

import android.graphics.Rect;
import android.view.View;

import org.opendroidpdf.app.reader.GestureRouter;

/**
 * Extracted Host implementation for GestureRouter to slim down ReaderView.
 * Bridges back to ReaderView via minimal helper methods.
 */
final class ReaderGestureHost implements GestureRouter.Host {
    private final ReaderView rv;

    ReaderGestureHost(ReaderView rv) {
        this.rv = rv;
    }

    @Override public boolean isScaling() { return rv.isScalingForHost(); }
    @Override public void setScaling(boolean scaling) { rv.setScalingForHost(scaling); }

    @Override public boolean isScrollDisabled() { return rv.isScrollDisabledForHost(); }
    @Override public void setScrollDisabled(boolean disabled) { rv.setScrollDisabledForHost(disabled); }

    @Override public View getSelectedView() { return rv.getSelectedView(); }
    @Override public View getNextViewCandidate() {
        int i = rv.getSelectedItemPosition() + 1;
        return rv.getView(i);
    }
    @Override public View getPreviousViewCandidate() {
        int i = rv.getSelectedItemPosition() - 1;
        return rv.getView(i);
    }

    @Override public Rect getScrollBounds(View v) { return rv.getScrollBoundsForView(v); }
    @Override public int getFlingMargin() { return rv.getFlingMarginConst(); }
    @Override public void slideViewOntoScreen(View v) { rv.slideViewOntoScreenBridge(v); }
    @Override public void flingWithinBounds(int velocityX, int velocityY, Rect bounds) { rv.flingWithinBoundsBridge(velocityX, velocityY, bounds); }

    @Override public void addScroll(float dx, float dy) { rv.addScrollFromHost(dx, dy); }
    @Override public void requestLayoutHost() { rv.requestLayout(); }
    @Override public void setScroll(int x, int y) { rv.setScrollFromHost(x, y); }

    @Override public float getScale() { return rv.getScaleForHost(); }
    @Override public void setScale(float scale) { rv.setScale(scale); }
    @Override public boolean isReflow() { return rv.isReflowForHost(); }
    @Override public boolean isFitWidth() { return rv.isFitWidthForHost(); }
    @Override public float getMinScale() { return rv.getMinScaleForHost(); }
    @Override public float getMaxScale() { return rv.getMaxScaleForHost(); }
    @Override public int getPrevFocusX() { return rv.getPrevFocusXForHost(); }
    @Override public int getPrevFocusY() { return rv.getPrevFocusYForHost(); }
    @Override public void setPrevFocus(int x, int y) { rv.setPrevFocusForHost(x, y); }
    @Override public void applyScaleToAllChildren() { rv.applyScaleToAllChildrenFromHost(); }
    @Override public void stopScroller() { rv.stopScrollerFromHost(); }
    @Override public int getContainerWidth() { return rv.getWidth(); }
    @Override public int getContainerHeight() { return rv.getHeight(); }
    @Override public int getPadLeft() { return rv.getPaddingLeft(); }
    @Override public int getPadRight() { return rv.getPaddingRight(); }
    @Override public int getPadTop() { return rv.getPaddingTop(); }
    @Override public int getPadBottom() { return rv.getPaddingBottom(); }
}

