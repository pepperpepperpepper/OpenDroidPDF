package org.opendroidpdf.app.preferences;

import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.MuPDFCore;

/**
 * Applies the current FreeText style preferences snapshot to the native MuPDF core FreeText defaults.
 *
 * <p>This is intentionally the single place that translates {@link TextStylePrefsSnapshot} into the
 * native {@link MuPDFCore#setFreeTextDefaults(float, int, int, float, float, float, float, float, float, float, float, float, float, float, float, boolean, float)}
 * call so new text comments inherit the user's last-used settings.</p>
 */
public final class TextStyleNativeSettingsApplier {
    private TextStyleNativeSettingsApplier() {}

    public static void apply(MuPDFCore core, TextStylePrefsSnapshot snap) {
        if (core == null || snap == null) return;

        int textIdx = snap.colorIndex;
        int bgIdx = snap.backgroundColorIndex;
        int borderIdx = snap.borderColorIndex;
        core.setFreeTextDefaults(
                snap.fontSize,
                snap.fontFamily,
                snap.fontStyleFlags,
                snap.lineHeight,
                snap.textIndentPt,
                ColorPalette.getR(textIdx), ColorPalette.getG(textIdx), ColorPalette.getB(textIdx),
                ColorPalette.getR(bgIdx), ColorPalette.getG(bgIdx), ColorPalette.getB(bgIdx), snap.backgroundOpacity,
                ColorPalette.getR(borderIdx), ColorPalette.getG(borderIdx), ColorPalette.getB(borderIdx),
                snap.borderWidthPt,
                snap.borderStyle != 0,
                snap.borderRadiusPt);
    }
}
