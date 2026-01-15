package org.opendroidpdf.app.preferences;

/**
 * Immutable view of current FreeText style preferences (font size + color).
 * Keeps the service API free of Android storage types so callers can consume plain data.
 */
public final class TextStylePrefsSnapshot {
    /** 0=sans (Helvetica), 1=serif (Times), 2=mono (Courier). */
    public final int fontFamily;
    /** Bitmask: bold/italic/underline/strikethrough (see {@code TextStyleFlags}). */
    public final int fontStyleFlags;
    public final float fontSize;
    /** CSS-like {@code line-height} multiplier (e.g. 1.2). */
    public final float lineHeight;
    /** CSS-like {@code text-indent} in PDF points. */
    public final float textIndentPt;
    public final int colorIndex;
    public final int backgroundColorIndex;
    public final float backgroundOpacity;
    public final int borderColorIndex;
    /** Border width in PDF points (0 disables). */
    public final float borderWidthPt;
    /** 0=solid, 1=dashed. */
    public final int borderStyle;
    /** Corner radius in PDF points (0=sharp). */
    public final float borderRadiusPt;
    public final float minFontSize;
    public final float maxFontSize;
    public final float stepFontSize;
    public final float defaultFontSize;

    public TextStylePrefsSnapshot(int fontFamily,
                                  int fontStyleFlags,
                                  float fontSize,
                                  float lineHeight,
                                  float textIndentPt,
                                  int colorIndex,
                                  int backgroundColorIndex,
                                  float backgroundOpacity,
                                  int borderColorIndex,
                                  float borderWidthPt,
                                  int borderStyle,
                                  float borderRadiusPt,
                                  float minFontSize,
                                  float maxFontSize,
                                  float stepFontSize,
                                  float defaultFontSize) {
        this.fontFamily = fontFamily;
        this.fontStyleFlags = fontStyleFlags;
        this.fontSize = fontSize;
        this.lineHeight = lineHeight;
        this.textIndentPt = textIndentPt;
        this.colorIndex = colorIndex;
        this.backgroundColorIndex = backgroundColorIndex;
        this.backgroundOpacity = backgroundOpacity;
        this.borderColorIndex = borderColorIndex;
        this.borderWidthPt = borderWidthPt;
        this.borderStyle = borderStyle;
        this.borderRadiusPt = borderRadiusPt;
        this.minFontSize = minFontSize;
        this.maxFontSize = maxFontSize;
        this.stepFontSize = stepFontSize;
        this.defaultFontSize = defaultFontSize;
    }

    public TextStylePrefsSnapshot withFontFamily(int family) {
        return new TextStylePrefsSnapshot(family, fontStyleFlags, fontSize, lineHeight, textIndentPt, colorIndex, backgroundColorIndex, backgroundOpacity, borderColorIndex, borderWidthPt, borderStyle, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withFontStyleFlags(int flags) {
        return new TextStylePrefsSnapshot(fontFamily, flags, fontSize, lineHeight, textIndentPt, colorIndex, backgroundColorIndex, backgroundOpacity, borderColorIndex, borderWidthPt, borderStyle, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withFontSize(float value) {
        return new TextStylePrefsSnapshot(fontFamily, fontStyleFlags, value, lineHeight, textIndentPt, colorIndex, backgroundColorIndex, backgroundOpacity, borderColorIndex, borderWidthPt, borderStyle, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withLineHeight(float value) {
        return new TextStylePrefsSnapshot(fontFamily, fontStyleFlags, fontSize, value, textIndentPt, colorIndex, backgroundColorIndex, backgroundOpacity, borderColorIndex, borderWidthPt, borderStyle, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withTextIndentPt(float value) {
        return new TextStylePrefsSnapshot(fontFamily, fontStyleFlags, fontSize, lineHeight, value, colorIndex, backgroundColorIndex, backgroundOpacity, borderColorIndex, borderWidthPt, borderStyle, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withColorIndex(int index) {
        return new TextStylePrefsSnapshot(fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, index, backgroundColorIndex, backgroundOpacity, borderColorIndex, borderWidthPt, borderStyle, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withBackgroundColorIndex(int index) {
        return new TextStylePrefsSnapshot(fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, colorIndex, index, backgroundOpacity, borderColorIndex, borderWidthPt, borderStyle, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withBackgroundOpacity(float value) {
        return new TextStylePrefsSnapshot(fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, colorIndex, backgroundColorIndex, value, borderColorIndex, borderWidthPt, borderStyle, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withBorderColorIndex(int index) {
        return new TextStylePrefsSnapshot(fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, colorIndex, backgroundColorIndex, backgroundOpacity, index, borderWidthPt, borderStyle, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withBorderWidthPt(float value) {
        return new TextStylePrefsSnapshot(fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, colorIndex, backgroundColorIndex, backgroundOpacity, borderColorIndex, value, borderStyle, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withBorderStyle(int style) {
        return new TextStylePrefsSnapshot(fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, colorIndex, backgroundColorIndex, backgroundOpacity, borderColorIndex, borderWidthPt, style, borderRadiusPt, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }

    public TextStylePrefsSnapshot withBorderRadiusPt(float value) {
        return new TextStylePrefsSnapshot(fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, colorIndex, backgroundColorIndex, backgroundOpacity, borderColorIndex, borderWidthPt, borderStyle, value, minFontSize, maxFontSize, stepFontSize, defaultFontSize);
    }
}
