package org.opendroidpdf.app.reader.gesture;

import android.graphics.Rect;
import android.view.View;

import org.opendroidpdf.app.reader.GestureRouter;
import org.opendroidpdf.app.reader.PagingAxis;

/**
 * Extracted Host implementation for GestureRouter to slim down ReaderView.
 * Bridges back to ReaderView via a narrow interface so ReaderView can keep
 * its internal helpers package-private.
 */
public final class ReaderViewGestureHost implements GestureRouter.Host {
    public interface ViewBridge {
        boolean isScaling();
        void setScaling(boolean scaling);

        PagingAxis pagingAxis();

        boolean isScrollDisabled();
        void setScrollDisabled(boolean disabled);
        View getSelectedView();
        int getSelectedItemPosition();
        View getViewAt(int index);
        Rect getScrollBoundsForView(View v);
        int getFlingMargin();
        void slideViewOntoScreen(View v);
        void flingWithinBounds(int velocityX, int velocityY, Rect bounds);

        void addScroll(float dx, float dy);
        void requestLayout();
        void setScroll(int x, int y);

        float getScale();
        void setScale(float scale);
        boolean isReflow();
        boolean isFitWidth();
        float getMinScale();
        float getMaxScale();
        int getPrevFocusX();
        int getPrevFocusY();
        void setPrevFocus(int x, int y);
        void applyScaleToAllChildren();
        void stopScroller();
        int getContainerWidth();
        int getContainerHeight();
        int getPadLeft();
        int getPadRight();
        int getPadTop();
        int getPadBottom();
    }

    private final ViewBridge bridge;

    public ReaderViewGestureHost(ViewBridge bridge) {
        this.bridge = bridge;
    }

    @Override public boolean isScaling() { return bridge.isScaling(); }
    @Override public void setScaling(boolean scaling) { bridge.setScaling(scaling); }

    @Override public PagingAxis pagingAxis() { return bridge.pagingAxis(); }

    @Override public boolean isScrollDisabled() { return bridge.isScrollDisabled(); }
    @Override public void setScrollDisabled(boolean disabled) { bridge.setScrollDisabled(disabled); }

    @Override public View getSelectedView() { return bridge.getSelectedView(); }
    @Override public View getNextViewCandidate() {
        int i = bridge.getSelectedItemPosition() + 1;
        return bridge.getViewAt(i);
    }
    @Override public View getPreviousViewCandidate() {
        int i = bridge.getSelectedItemPosition() - 1;
        return bridge.getViewAt(i);
    }

    @Override public Rect getScrollBounds(View v) { return bridge.getScrollBoundsForView(v); }
    @Override public int getFlingMargin() { return bridge.getFlingMargin(); }
    @Override public void slideViewOntoScreen(View v) { bridge.slideViewOntoScreen(v); }
    @Override public void flingWithinBounds(int velocityX, int velocityY, Rect bounds) { bridge.flingWithinBounds(velocityX, velocityY, bounds); }

    @Override public void addScroll(float dx, float dy) { bridge.addScroll(dx, dy); }
    @Override public void requestLayoutHost() { bridge.requestLayout(); }
    @Override public void setScroll(int x, int y) { bridge.setScroll(x, y); }

    @Override public float getScale() { return bridge.getScale(); }
    @Override public void setScale(float scale) { bridge.setScale(scale); }
    @Override public boolean isReflow() { return bridge.isReflow(); }
    @Override public boolean isFitWidth() { return bridge.isFitWidth(); }
    @Override public float getMinScale() { return bridge.getMinScale(); }
    @Override public float getMaxScale() { return bridge.getMaxScale(); }
    @Override public int getPrevFocusX() { return bridge.getPrevFocusX(); }
    @Override public int getPrevFocusY() { return bridge.getPrevFocusY(); }
    @Override public void setPrevFocus(int x, int y) { bridge.setPrevFocus(x, y); }
    @Override public void applyScaleToAllChildren() { bridge.applyScaleToAllChildren(); }
    @Override public void stopScroller() { bridge.stopScroller(); }
    @Override public int getContainerWidth() { return bridge.getContainerWidth(); }
    @Override public int getContainerHeight() { return bridge.getContainerHeight(); }
    @Override public int getPadLeft() { return bridge.getPadLeft(); }
    @Override public int getPadRight() { return bridge.getPadRight(); }
    @Override public int getPadTop() { return bridge.getPadTop(); }
    @Override public int getPadBottom() { return bridge.getPadBottom(); }
}
