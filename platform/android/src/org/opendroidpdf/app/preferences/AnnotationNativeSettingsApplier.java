package org.opendroidpdf.app.preferences;

import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.MuPDFCore;

/** Applies editor/annotation color preferences to the native core. */
public final class AnnotationNativeSettingsApplier {
    private AnnotationNativeSettingsApplier() {}

    public static void apply(MuPDFCore core, EditorPrefsSnapshot snap) {
        if (core == null || snap == null) return;

        int idx = snap.highlightColorIndex;
        core.setHighlightColor(ColorPalette.getR(idx), ColorPalette.getG(idx), ColorPalette.getB(idx));

        idx = snap.underlineColorIndex;
        core.setUnderlineColor(ColorPalette.getR(idx), ColorPalette.getG(idx), ColorPalette.getB(idx));

        idx = snap.strikeoutColorIndex;
        core.setStrikeoutColor(ColorPalette.getR(idx), ColorPalette.getG(idx), ColorPalette.getB(idx));

        idx = snap.textAnnotIconColorIndex;
        core.setTextAnnotIconColor(ColorPalette.getR(idx), ColorPalette.getG(idx), ColorPalette.getB(idx));
    }
}

