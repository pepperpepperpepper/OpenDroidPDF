package org.opendroidpdf.app.reader;

import android.view.View;

/**
 * Navigation helpers extracted from ReaderView: compute forward/backward
 * offsets using ColumnPager with the same inputs ReaderView used.
 */
public final class ReaderNavigation {
    private ReaderNavigation() {}

    public static int[] forwardOffsets(int screenWidth, int screenHeight,
                                       int remainingX, int remainingY,
                                       int left, int top, int right, int bottom,
                                       int docWidth, int docHeight,
                                       View nextViewCandidate) {
        return org.opendroidpdf.app.reader.ColumnPager.computeForwardScroll(
                screenWidth, screenHeight,
                remainingX, remainingY,
                left, top, right, bottom,
                docWidth, docHeight,
                nextViewCandidate);
    }

    public static int[] backwardOffsets(int screenWidth, int screenHeight,
                                        int remainingX, int remainingY,
                                        int left, int top,
                                        int docWidth, int docHeight,
                                        View previousViewCandidate) {
        return org.opendroidpdf.app.reader.ColumnPager.computeBackwardScroll(
                screenWidth, screenHeight,
                remainingX, remainingY,
                left, top,
                docWidth, docHeight,
                previousViewCandidate);
    }
}

