package org.opendroidpdf.app.preferences;

/**
 * Immutable view of current pen preferences. Keeps the service API free of
 * Android storage types so callers can consume plain data.
 */
public final class PenPrefsSnapshot {
    public final float thickness;
    public final int colorIndex;
    public final float minThickness;
    public final float maxThickness;
    public final float stepThickness;
    public final float defaultThickness;

    public PenPrefsSnapshot(float thickness,
                            int colorIndex,
                            float minThickness,
                            float maxThickness,
                            float stepThickness,
                            float defaultThickness) {
        this.thickness = thickness;
        this.colorIndex = colorIndex;
        this.minThickness = minThickness;
        this.maxThickness = maxThickness;
        this.stepThickness = stepThickness;
        this.defaultThickness = defaultThickness;
    }

    public PenPrefsSnapshot withThickness(float v) {
        return new PenPrefsSnapshot(v, colorIndex, minThickness, maxThickness, stepThickness, defaultThickness);
    }

    public PenPrefsSnapshot withColorIndex(int idx) {
        return new PenPrefsSnapshot(thickness, idx, minThickness, maxThickness, stepThickness, defaultThickness);
    }
}
