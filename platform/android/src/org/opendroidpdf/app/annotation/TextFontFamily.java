package org.opendroidpdf.app.annotation;

import android.graphics.Typeface;

/** Supported font families for comment-style text annotations (Base-14 subset). */
public final class TextFontFamily {
    public static final int SANS = 0;  // Helvetica
    public static final int SERIF = 1; // Times-Roman
    public static final int MONO = 2;  // Courier

    private TextFontFamily() {}

    public static int normalize(int family) {
        if (family == SERIF) return SERIF;
        if (family == MONO) return MONO;
        return SANS;
    }

    public static Typeface typeface(int family) {
        family = normalize(family);
        if (family == SERIF) return Typeface.SERIF;
        if (family == MONO) return Typeface.MONOSPACE;
        return Typeface.SANS_SERIF;
    }
}

