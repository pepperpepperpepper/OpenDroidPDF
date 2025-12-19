package org.opendroidpdf.app.preferences;

import android.app.Application;
import android.content.Context;

import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.services.PenPreferencesService;

/**
 * Aggregates editor-related preferences for the PageView and renderers.
 * Delegates pen thickness/color to {@link PenPreferences} and exposes
 * eraser thickness and selection toggles with sensible defaults.
 */
public class EditorPreferences {
    private final EditorPrefsStore editorPrefsStore;
    private final PenPreferencesService penPreferences;

    public EditorPreferences(Context context) {
        Context app = context.getApplicationContext();
        this.editorPrefsStore = new SharedPreferencesEditorPrefsStore(app);
        this.penPreferences = AppServices.init((Application) app).penPreferences();
    }

    public static final int DEFAULT_ERASER_COLOR = 0xFFFFFFFF;

    // Pen settings
    public float getInkThickness() { return penPreferences.get().thickness; }
    public void setInkThickness(float value) { penPreferences.setThickness(value); }
    public int getInkColorHex() { return ColorPalette.getHex(penPreferences.get().colorIndex); }

    // Eraser settings
    public float getEraserThickness() {
        return editorPrefsStore.load().eraserThickness;
    }

    // Selection behavior
    public boolean isSmartTextSelectionEnabled() {
        return editorPrefsStore.load().smartTextSelectionEnabled;
    }

    // Optional annotation colors (not currently used by PageView overlay)
    public int getHighlightColorHex() { return ColorPalette.getHex(editorPrefsStore.load().highlightColorIndex); }
    public int getUnderlineColorHex() { return ColorPalette.getHex(editorPrefsStore.load().underlineColorIndex); }
    public int getStrikeoutColorHex() { return ColorPalette.getHex(editorPrefsStore.load().strikeoutColorIndex); }
}
