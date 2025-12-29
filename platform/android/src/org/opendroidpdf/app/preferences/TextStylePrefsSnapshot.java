package org.opendroidpdf.app.preferences;

/**
 * Immutable view of current FreeText style preferences (font size + color).
 * Keeps the service API free of Android storage types so callers can consume plain data.
 */
public final class TextStylePrefsSnapshot {
    public final float fontSize;
    public final int colorIndex;
    public final float minFontSize;
    public final float maxFontSize;
    public final float stepFontSize;
    public final float defaultFontSize;

    public TextStylePrefsSnapshot(float fontSize,
                                  int colorIndex,
                                  float minFontSize,
                                  float maxFontSize,
                                  float stepFontSize,
                                  float defaultFontSize) {
        this.fontSize = fontSize;
        this.colorIndex = colorIndex;
        this.minFontSize = minFontSize;
        this.maxFontSize = maxFontSize;
        this.stepFontSize = stepFontSize;
        this.defaultFontSize = defaultFontSize;
    }

    public TextStylePrefsSnapshot withFontSize(float value) {
        return new TextStylePrefsSnapshot(value, colorIndex, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withColorIndex(int index) {
        return new TextStylePrefsSnapshot(fontSize, index, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }
}

