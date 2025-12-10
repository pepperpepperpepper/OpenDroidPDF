package org.opendroidpdf.app.reader;

import android.graphics.Point;
import android.view.View;

/**
 * Computes smart forward/backward scroll offsets based on the current
 * viewport and neighbouring pages. Extracted from ReaderView.
 */
public final class ColumnPager {
    private ColumnPager() {}

    /**
     * @return int[]{xOffset, yOffset}
     */
    public static int[] computeForwardScroll(int screenWidth, int screenHeight,
                                             int remainingX, int remainingY,
                                             int left, int top, int right, int bottom,
                                             int docWidth, int docHeight,
                                             View nextView) {
        int xOffset, yOffset;
        if (bottom >= docHeight || screenHeight >= (int)(0.8f * docHeight)) {
            if (right + (int)(0.4f * screenWidth) > docWidth || screenWidth >= (int)(0.7f * docWidth)) {
                if (nextView == null) {
                    return new int[]{remainingX, remainingY};
                }
                int nextTop  = -(nextView.getTop() + remainingY);
                int nextLeft = -(nextView.getLeft() + remainingX);
                int nextDocWidth = nextView.getMeasuredWidth();
                int nextDocHeight = nextView.getMeasuredHeight();

                if (nextDocHeight < screenHeight) {
                    yOffset = ((nextDocHeight - screenHeight) >> 1);
                } else if (screenHeight >= (int)(0.8f * docHeight)) {
                    yOffset = top;
                } else {
                    yOffset = 0;
                }

                if (nextDocWidth < screenWidth) {
                    xOffset = (nextDocWidth - screenWidth) >> 1;
                } else {
                    if (screenWidth >= (int)(0.7f * docWidth))
                        xOffset = left;
                    else
                        xOffset = 0;
                    if (xOffset + screenWidth > nextDocWidth)
                        xOffset = nextDocWidth - screenWidth;
                }
                xOffset -= nextLeft;
                yOffset -= nextTop;
            } else {
                xOffset = Math.min(screenWidth, docWidth - right);
                yOffset = screenHeight - bottom;
            }
        } else {
            xOffset = 0;
            yOffset = smartAdvanceAmount(screenHeight, docHeight - bottom);
        }
        return new int[]{xOffset, yOffset};
    }

    /**
     * @return int[]{xOffset, yOffset}
     */
    public static int[] computeBackwardScroll(int screenWidth, int screenHeight,
                                              int remainingX, int remainingY,
                                              int left, int top,
                                              int docWidth, int docHeight,
                                              View prevView) {
        int xOffset, yOffset;
        if (top <= 0 || screenHeight >= (int)(0.8f * docHeight)) {
            if (left < (int)(0.4f * screenWidth) || screenWidth >= (int)(0.7f * docWidth)) {
                if (prevView == null) {
                    return new int[]{remainingX, remainingY};
                }
                int prevTop  = -(prevView.getTop() + remainingY);
                int prevLeft = -(prevView.getLeft() + remainingX);
                int prevDocWidth = prevView.getMeasuredWidth();
                int prevDocHeight = prevView.getMeasuredHeight();

                if (prevDocHeight < screenHeight) {
                    yOffset = ((prevDocHeight - screenHeight) >> 1);
                } else if (screenHeight >= (int)(0.8f * docHeight)) {
                    yOffset = top;
                } else {
                    yOffset = Math.max(0, prevDocHeight - screenHeight);
                }

                if (prevDocWidth < screenWidth) {
                    xOffset = (prevDocWidth - screenWidth) >> 1;
                } else {
                    if (screenWidth >= (int)(0.7f * docWidth))
                        xOffset = left;
                    else
                        xOffset = prevDocWidth - screenWidth;
                    if (xOffset < 0) xOffset = 0;
                }
                xOffset -= prevLeft;
                yOffset -= prevTop;
            } else {
                xOffset = -Math.min(screenWidth, left);
                yOffset = -Math.min(screenHeight, top);
            }
        } else {
            xOffset = 0;
            yOffset = -smartAdvanceAmount(screenHeight, top);
        }
        return new int[]{xOffset, yOffset};
    }

    private static int smartAdvanceAmount(int screenHeight, int max) {
        // Mirror ReaderView.smartAdvanceAmount behavior (90% screen advance
        // with small adjustments so a whole number of steps hits the boundary).
        int advance = (int)(screenHeight * 0.9 + 0.5);
        int leftOver = max % advance;
        int steps = max / advance;
        if (leftOver == 0) {
            // exact, no adjustment
        } else if (steps > 0 && (float)leftOver / steps <= screenHeight * 0.05) {
            advance += (int)((float)leftOver/steps + 0.5);
        } else {
            int overshoot = advance - leftOver;
            if (steps > 0 && (float)overshoot / steps <= screenHeight * 0.1) {
                advance -= (int)((float)overshoot/steps + 0.5);
            }
        }
        if (advance > max)
            advance = max;
        return advance;
    }
}
