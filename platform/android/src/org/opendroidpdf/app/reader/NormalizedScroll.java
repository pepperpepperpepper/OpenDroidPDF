package org.opendroidpdf.app.reader;

import android.view.View;

/** Utility to compute normalized scroll values and convert between
 * normalized/doc-relative and pixel coordinates. Kept static for easy
 * reuse from ReaderView without holding references. */
public final class NormalizedScroll {
    private NormalizedScroll() {}

    public static float normalizedX(int childLeft, int paddingLeft, int childWidth) {
        if (childWidth == 0) return 0f;
        return (childLeft - paddingLeft) / (float) childWidth;
    }

    public static float normalizedY(int childTop, int paddingTop, int childHeight) {
        if (childHeight == 0) return 0f;
        return (childTop - paddingTop) / (float) childHeight;
    }

    // Preset-to-current form used in ReaderView before applying new targets
    public static int presetPixelsFromNormalized(float normalized, int childExtent, float viewScale, float fillScale) {
        return (int) (normalized * childExtent * viewScale * fillScale);
    }

    // Compute target pixels for a new normalized request; padding is added here
    public static int targetPixelsFromNormalized(float normalized, int childExtent, float viewScale, float fillScale, int padding) {
        return (int) (normalized * childExtent * viewScale * fillScale) + padding;
    }

    // Convert a doc-relative delta to a normalized delta for the current geometry
    public static float normalizedFromDocRelX(float docRelX, float pageScale, int childWidth, float viewScale, float fillScale) {
        if (childWidth == 0) return 0f;
        return -docRelX * pageScale / (childWidth * viewScale * fillScale);
    }

    public static float normalizedFromDocRelY(float docRelY, float pageScale, int childHeight, float viewScale, float fillScale) {
        if (childHeight == 0) return 0f;
        return -docRelY * pageScale / (childHeight * viewScale * fillScale);
    }
}

