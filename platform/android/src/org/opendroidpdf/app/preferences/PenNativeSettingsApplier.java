package org.opendroidpdf.app.preferences;

import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.MuPDFCore;

/**
 * Applies the current pen preferences snapshot to the native MuPDF core ink settings.
 *
 * <p>This is intentionally the only place that translates {@link PenPrefsSnapshot} into
 * {@link MuPDFCore#setInkThickness(float)} / {@link MuPDFCore#setInkColor(float, float, float)}
 * so we don't have multiple drift-prone "apply pen prefs" pipelines.</p>
 */
public final class PenNativeSettingsApplier {
    private PenNativeSettingsApplier() {}

    public static void apply(MuPDFCore core, PenPrefsSnapshot snap) {
        if (core == null || snap == null) return;
        // Match legacy behavior: MuPDF expects a slightly smaller value than the UI-facing thickness.
        core.setInkThickness(snap.thickness * 0.5f);
        int idx = snap.colorIndex;
        core.setInkColor(ColorPalette.getR(idx), ColorPalette.getG(idx), ColorPalette.getB(idx));
    }
}
