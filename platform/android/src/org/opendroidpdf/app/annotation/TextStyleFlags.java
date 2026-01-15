package org.opendroidpdf.app.annotation;

import android.graphics.Typeface;

/** Bitmask style flags for comment-style text annotations (FreeText + sidecar notes). */
public final class TextStyleFlags {
    public static final int BOLD = 1 << 0;
    public static final int ITALIC = 1 << 1;
    public static final int UNDERLINE = 1 << 2;
    public static final int STRIKETHROUGH = 1 << 3;

    public static final int MASK = BOLD | ITALIC | UNDERLINE | STRIKETHROUGH;

    private TextStyleFlags() {}

    public static int normalize(int flags) {
        return flags & MASK;
    }

    public static boolean isBold(int flags) {
        return (normalize(flags) & BOLD) != 0;
    }

    public static boolean isItalic(int flags) {
        return (normalize(flags) & ITALIC) != 0;
    }

    public static boolean isUnderline(int flags) {
        return (normalize(flags) & UNDERLINE) != 0;
    }

    public static boolean isStrikethrough(int flags) {
        return (normalize(flags) & STRIKETHROUGH) != 0;
    }

    public static int typefaceStyle(int flags) {
        flags = normalize(flags);
        boolean bold = (flags & BOLD) != 0;
        boolean italic = (flags & ITALIC) != 0;
        if (bold && italic) return Typeface.BOLD_ITALIC;
        if (bold) return Typeface.BOLD;
        if (italic) return Typeface.ITALIC;
        return Typeface.NORMAL;
    }
}

