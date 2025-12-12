package org.opendroidpdf.app.reader;

import android.view.View;
import android.widget.Scroller;

/**
 * Moves forward/backward by ~90% of a page while respecting current scroll/scale.
 * Extracted from ReaderView to reduce its size.
 */
public final class SmartMoveHelper {
    private SmartMoveHelper() {}

    public interface Host {
        View currentView();
        View viewAt(int index);
        int currentIndex();
        int adapterCount();
        int screenWidth();
        int screenHeight();
        int paddingLeft();
        int paddingTop();
        int scrollerRemainingX();
        int scrollerRemainingY();
        ScrollState scrollState();
        Scroller scroller();
        void postSelf();
    }

    public static void moveForwards(Host h) {
        View v = h.currentView();
        if (v == null) return;

        int screenWidth  = h.screenWidth();
        int screenHeight = h.screenHeight();
        int remainingX = h.scrollerRemainingX();
        int remainingY = h.scrollerRemainingY();
        int left = -(v.getLeft() + h.scrollState().getX() + remainingX) + h.paddingLeft();
        int top  = -(v.getTop()  + h.scrollState().getY() + remainingY) + h.paddingTop();
        int right  = screenWidth  + left;
        int bottom = screenHeight + top;
        int docWidth  = v.getMeasuredWidth();
        int docHeight = v.getMeasuredHeight();

        View nv = h.viewAt(h.currentIndex()+1);
        int[] offsetsFwd = ReaderNavigation.forwardOffsets(
                screenWidth, screenHeight,
                remainingX, remainingY,
                left, top, right, bottom,
                docWidth, docHeight,
                nv);
        int xOffset = offsetsFwd[0];
        int yOffset = offsetsFwd[1];
        h.scrollState().setScrollerLast(0, 0);
        h.scroller().startScroll(0, 0, remainingX - xOffset, remainingY - yOffset, 400);
        h.postSelf();
    }

    public static void moveBackwards(Host h) {
        View v = h.currentView();
        if (v == null) return;

        int screenWidth  = h.screenWidth();
        int screenHeight = h.screenHeight();
        int remainingX = h.scrollerRemainingX();
        int remainingY = h.scrollerRemainingY();
        int left  = -(v.getLeft() + h.scrollState().getX() + remainingX) + h.paddingLeft();
        int top   = -(v.getTop()  + h.scrollState().getY() + remainingY) + h.paddingTop();
        int docWidth  = v.getMeasuredWidth();
        int docHeight = v.getMeasuredHeight();

        View pv = h.viewAt(h.currentIndex()-1);
        int[] offsetsBack = ReaderNavigation.backwardOffsets(
                screenWidth, screenHeight,
                remainingX, remainingY,
                left, top,
                docWidth, docHeight,
                pv);
        int xOffset = offsetsBack[0];
        int yOffset = offsetsBack[1];
        h.scrollState().setScrollerLast(0, 0);
        h.scroller().startScroll(0, 0, remainingX - xOffset, remainingY - yOffset, 400);
        h.postSelf();
    }
}
