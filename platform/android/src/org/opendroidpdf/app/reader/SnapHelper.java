package org.opendroidpdf.app.reader;

import android.view.View;
import android.widget.Scroller;

/**
 * Handles fit-width snap logic so ReaderView can stay slimmer.
 */
public final class SnapHelper {
    private SnapHelper() {}

    /**
     * Attempts to snap to fit-width scale; returns the new scale if applied, otherwise null.
     * Side effects: may adjust scroll state and force-finish the scroller.
     */
    public static Float snapFitWidthIfEligible(
            boolean fitWidth,
            boolean reflow,
            float currentScale,
            float minScale,
            float maxScale,
            View container,
            View child,
            Scroller scroller,
            ScrollState scrollState) {
        if (!fitWidth || child == null) return null;
        Float snap = ReaderGeometry.computeSnapFitWidthScaleFromViews(
                fitWidth,
                reflow,
                currentScale,
                container,
                child,
                minScale,
                maxScale);
        if (snap == null) return null;

        scroller.forceFinished(true);
        scrollState.setScroll(-child.getLeft(), 0);
        return snap;
    }
}
