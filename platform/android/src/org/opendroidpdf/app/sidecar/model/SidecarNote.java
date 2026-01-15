package org.opendroidpdf.app.sidecar.model;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Immutable note/text annotation persisted in the sidecar store. */
public final class SidecarNote {
    /** Default text color used when migrating older rows without a stored color. */
    public static final int DEFAULT_COLOR = 0xFF111111;
    /** Default font family (0=sans, 1=serif, 2=mono). */
    public static final int DEFAULT_FONT_FAMILY = 0;
    /** Default font style flags bitmask (bold/italic/underline/strikethrough). */
    public static final int DEFAULT_FONT_STYLE_FLAGS = 0;
    /** Default font size (doc units) used when migrating older rows without a stored size. */
    public static final float DEFAULT_FONT_SIZE = 12.0f;
    /** Default background color (ARGB) when older rows do not store fill settings. */
    public static final int DEFAULT_BACKGROUND_COLOR = 0x00000000; // transparent
    /** Default background fill opacity [0..1]. */
    public static final float DEFAULT_BACKGROUND_OPACITY = 0.0f;
    /** Default border color (ARGB). */
    public static final int DEFAULT_BORDER_COLOR = DEFAULT_COLOR;
    /** Default border width (doc units / pt). */
    public static final float DEFAULT_BORDER_WIDTH_PT = 0.0f;
    /** Default border style (0=solid, 1=dashed). */
    public static final int DEFAULT_BORDER_STYLE = 0;
    /** Default corner rounding radius (doc units / pt). */
    public static final float DEFAULT_BORDER_RADIUS_PT = 0.0f;
    /** Default lock for position/size. */
    public static final boolean DEFAULT_LOCK_POSITION_SIZE = false;
    /** Default lock for contents/style edits. */
    public static final boolean DEFAULT_LOCK_CONTENTS = false;
    /** Default rotation in degrees (0/90/180/270). */
    public static final int DEFAULT_ROTATION_DEG = 0;
    /** Default line height multiplier for note text (CSS-like {@code line-height}). */
    public static final float DEFAULT_LINE_HEIGHT = 1.2f;
    /** Default first-line indent for note text (doc units / pt). */
    public static final float DEFAULT_TEXT_INDENT_PT = 0.0f;

    @NonNull public final String id;
    public final int pageIndex;
    @Nullable public final String layoutProfileId;
    @NonNull public final RectF bounds;
    @Nullable public final String text;
    public final long createdAtEpochMs;
    public final int color;
    public final int fontFamily;
    public final int fontStyleFlags;
    public final float fontSize;
    public final float lineHeight;
    public final float textIndentPt;
    public final boolean userResized;
    public final int backgroundColor;
    public final float backgroundOpacity;
    public final int borderColor;
    public final float borderWidthPt;
    public final int borderStyle;
    public final float borderRadiusPt;
    public final boolean lockPositionSize;
    public final boolean lockContents;
    public final int rotationDeg;

    private static int normalizeFontFamily(int family) {
        if (family == 1) return 1;
        if (family == 2) return 2;
        return 0;
    }

    private static int normalizeFontStyleFlags(int flags) {
        return flags & 0x0F;
    }

    private static float normalizeLineHeight(float lineHeight) {
        if (Float.isNaN(lineHeight) || Float.isInfinite(lineHeight)) return DEFAULT_LINE_HEIGHT;
        if (lineHeight < 0.5f) return DEFAULT_LINE_HEIGHT;
        if (lineHeight > 5.0f) return 5.0f;
        return lineHeight;
    }

    private static float normalizeTextIndentPt(float textIndentPt) {
        if (Float.isNaN(textIndentPt) || Float.isInfinite(textIndentPt)) return DEFAULT_TEXT_INDENT_PT;
        if (textIndentPt < -144.0f) return -144.0f;
        if (textIndentPt > 144.0f) return 144.0f;
        return textIndentPt;
    }

    public SidecarNote(@NonNull String id,
                       int pageIndex,
                       @Nullable String layoutProfileId,
                       @NonNull RectF bounds,
                       @Nullable String text,
                       long createdAtEpochMs,
                       int color,
                       float fontSize) {
        this(id, pageIndex, layoutProfileId, bounds, text, createdAtEpochMs, color,
                DEFAULT_FONT_FAMILY, DEFAULT_FONT_STYLE_FLAGS, fontSize,
                DEFAULT_LINE_HEIGHT, DEFAULT_TEXT_INDENT_PT,
                false, DEFAULT_BACKGROUND_COLOR, DEFAULT_BACKGROUND_OPACITY);
    }

    public SidecarNote(@NonNull String id,
                       int pageIndex,
                       @Nullable String layoutProfileId,
                       @NonNull RectF bounds,
                       @Nullable String text,
                       long createdAtEpochMs,
                       int color,
                       int fontFamily,
                       float fontSize) {
        this(id, pageIndex, layoutProfileId, bounds, text, createdAtEpochMs, color, fontFamily, DEFAULT_FONT_STYLE_FLAGS, fontSize, DEFAULT_LINE_HEIGHT, DEFAULT_TEXT_INDENT_PT, false, DEFAULT_BACKGROUND_COLOR, DEFAULT_BACKGROUND_OPACITY);
    }

    public SidecarNote(@NonNull String id,
                       int pageIndex,
                       @Nullable String layoutProfileId,
                       @NonNull RectF bounds,
                       @Nullable String text,
                       long createdAtEpochMs,
                       int color,
                       float fontSize,
                       boolean userResized) {
        this(id, pageIndex, layoutProfileId, bounds, text, createdAtEpochMs, color, DEFAULT_FONT_FAMILY, DEFAULT_FONT_STYLE_FLAGS, fontSize, DEFAULT_LINE_HEIGHT, DEFAULT_TEXT_INDENT_PT, userResized, DEFAULT_BACKGROUND_COLOR, DEFAULT_BACKGROUND_OPACITY);
    }

    public SidecarNote(@NonNull String id,
                       int pageIndex,
                       @Nullable String layoutProfileId,
                       @NonNull RectF bounds,
                       @Nullable String text,
                       long createdAtEpochMs,
                       int color,
                       int fontFamily,
                       int fontStyleFlags,
                       float fontSize,
                       float lineHeight,
                       float textIndentPt,
                       boolean userResized,
                       int backgroundColor,
                       float backgroundOpacity) {
        this(id, pageIndex, layoutProfileId, bounds, text, createdAtEpochMs, color, fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, userResized, backgroundColor, backgroundOpacity,
                DEFAULT_BORDER_COLOR, DEFAULT_BORDER_WIDTH_PT, DEFAULT_BORDER_STYLE, DEFAULT_BORDER_RADIUS_PT, DEFAULT_LOCK_POSITION_SIZE, DEFAULT_LOCK_CONTENTS);
    }

    public SidecarNote(@NonNull String id,
                       int pageIndex,
                       @Nullable String layoutProfileId,
                       @NonNull RectF bounds,
                       @Nullable String text,
                       long createdAtEpochMs,
                       int color,
                       int fontFamily,
                       int fontStyleFlags,
                       float fontSize,
                       float lineHeight,
                       float textIndentPt,
                       boolean userResized,
                       int backgroundColor,
                       float backgroundOpacity,
                       int borderColor,
                       float borderWidthPt,
                       int borderStyle,
                       float borderRadiusPt) {
        this(id, pageIndex, layoutProfileId, bounds, text, createdAtEpochMs, color, fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, userResized, backgroundColor, backgroundOpacity,
                borderColor, borderWidthPt, borderStyle, borderRadiusPt, DEFAULT_LOCK_POSITION_SIZE, DEFAULT_LOCK_CONTENTS);
    }

    public SidecarNote(@NonNull String id,
                       int pageIndex,
                       @Nullable String layoutProfileId,
                       @NonNull RectF bounds,
                       @Nullable String text,
                       long createdAtEpochMs,
                       int color,
                       int fontFamily,
                       int fontStyleFlags,
                       float fontSize,
                       float lineHeight,
                       float textIndentPt,
                       boolean userResized,
                       int backgroundColor,
                       float backgroundOpacity,
                       int borderColor,
                       float borderWidthPt,
                       int borderStyle,
                       float borderRadiusPt,
                       boolean lockPositionSize,
                       boolean lockContents) {
        this(id, pageIndex, layoutProfileId, bounds, text, createdAtEpochMs, color, fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, userResized, backgroundColor, backgroundOpacity,
                borderColor, borderWidthPt, borderStyle, borderRadiusPt, lockPositionSize, lockContents, DEFAULT_ROTATION_DEG);
    }

    public SidecarNote(@NonNull String id,
                       int pageIndex,
                       @Nullable String layoutProfileId,
                       @NonNull RectF bounds,
                       @Nullable String text,
                       long createdAtEpochMs,
                       int color,
                       int fontFamily,
                       int fontStyleFlags,
                       float fontSize,
                       float lineHeight,
                       float textIndentPt,
                       boolean userResized,
                       int backgroundColor,
                       float backgroundOpacity,
                       int borderColor,
                       float borderWidthPt,
                       int borderStyle,
                       float borderRadiusPt,
                       boolean lockPositionSize,
                       boolean lockContents,
                       int rotationDeg) {
        this.id = id;
        this.pageIndex = pageIndex;
        this.layoutProfileId = layoutProfileId;
        this.bounds = bounds;
        this.text = text;
        this.createdAtEpochMs = createdAtEpochMs;
        this.color = color;
        this.fontFamily = normalizeFontFamily(fontFamily);
        this.fontStyleFlags = normalizeFontStyleFlags(fontStyleFlags);
        this.fontSize = fontSize;
        this.lineHeight = normalizeLineHeight(lineHeight);
        this.textIndentPt = normalizeTextIndentPt(textIndentPt);
        this.userResized = userResized;
        this.backgroundColor = backgroundColor;
        this.backgroundOpacity = backgroundOpacity;
        this.borderColor = borderColor;
        this.borderWidthPt = borderWidthPt;
        this.borderStyle = borderStyle;
        this.borderRadiusPt = borderRadiusPt;
        this.lockPositionSize = lockPositionSize;
        this.lockContents = lockContents;
        this.rotationDeg = rotationDeg;
    }
}
