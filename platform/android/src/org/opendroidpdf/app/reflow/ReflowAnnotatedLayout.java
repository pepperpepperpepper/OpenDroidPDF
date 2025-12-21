package org.opendroidpdf.app.reflow;

import androidx.annotation.NonNull;

/**
 * Captures the exact reflow "layout profile" under which annotations were created.
 *
 * <p>This is intentionally more specific than {@link ReflowPrefsSnapshot} alone: for reflowable
 * documents the pagination depends on both user prefs (font/margins/line spacing) and the target
 * layout size (page width/height passed to MuPDF's layout). Without remembering the page size,
 * "switch back to annotated layout" may not reproduce the original layoutProfileId.</p>
 */
public final class ReflowAnnotatedLayout {
    @NonNull public final ReflowPrefsSnapshot prefs;
    /** Page width in MuPDF layout units (points). */
    public final float pageWidthPt;
    /** Page height in MuPDF layout units (points). */
    public final float pageHeightPt;

    public ReflowAnnotatedLayout(@NonNull ReflowPrefsSnapshot prefs,
                                 float pageWidthPt,
                                 float pageHeightPt) {
        this.prefs = prefs;
        this.pageWidthPt = pageWidthPt;
        this.pageHeightPt = pageHeightPt;
    }
}

