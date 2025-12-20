package org.opendroidpdf.app.reflow;

import androidx.annotation.NonNull;

/** Immutable per-document preferences for reflowable documents (EPUB/HTML). */
public final class ReflowPrefsSnapshot {
    public static final float DEFAULT_FONT_DP = 16f;
    public static final float DEFAULT_MARGIN_SCALE = 1.0f;
    public static final float DEFAULT_LINE_SPACING = 1.0f;

    public final float fontDp;
    public final float marginScale;
    public final float lineSpacing;
    @NonNull public final ReflowTheme theme;

    public ReflowPrefsSnapshot(float fontDp,
                               float marginScale,
                               float lineSpacing,
                               @NonNull ReflowTheme theme) {
        this.fontDp = fontDp;
        this.marginScale = marginScale;
        this.lineSpacing = lineSpacing;
        this.theme = theme;
    }

    @NonNull
    public static ReflowPrefsSnapshot defaults() {
        return new ReflowPrefsSnapshot(DEFAULT_FONT_DP, DEFAULT_MARGIN_SCALE, DEFAULT_LINE_SPACING, ReflowTheme.LIGHT);
    }
}

