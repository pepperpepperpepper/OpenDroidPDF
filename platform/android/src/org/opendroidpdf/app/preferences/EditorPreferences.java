package org.opendroidpdf.app.preferences;

import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.app.services.Provider;

/**
 * View-friendly projection of editor preferences.
 *
 * <p>Backed by in-memory snapshot providers so views don't read SharedPreferences
 * (or reach into AppServices) directly.
 */
public class EditorPreferences {
    private final Provider<PenPrefsSnapshot> penPrefs;
    private final Provider<EditorPrefsSnapshot> editorPrefs;

    public EditorPreferences(Provider<PenPrefsSnapshot> penPrefs,
                             Provider<EditorPrefsSnapshot> editorPrefs) {
        this.penPrefs = penPrefs;
        this.editorPrefs = editorPrefs;
    }

    public static final int DEFAULT_ERASER_COLOR = 0xFFFFFFFF;

    // Pen settings
    public float getInkThickness() { return penPrefs.get().thickness; }
    public int getInkColorHex() { return ColorPalette.getHex(penPrefs.get().colorIndex); }

    // Eraser settings
    public float getEraserThickness() {
        return editorPrefs.get().eraserThickness;
    }

    // Selection behavior
    public boolean isSmartTextSelectionEnabled() {
        return editorPrefs.get().smartTextSelectionEnabled;
    }

    // Optional annotation colors (not currently used by PageView overlay)
    public int getHighlightColorHex() { return ColorPalette.getHex(editorPrefs.get().highlightColorIndex); }
    public int getUnderlineColorHex() { return ColorPalette.getHex(editorPrefs.get().underlineColorIndex); }
    public int getStrikeoutColorHex() { return ColorPalette.getHex(editorPrefs.get().strikeoutColorIndex); }
}
