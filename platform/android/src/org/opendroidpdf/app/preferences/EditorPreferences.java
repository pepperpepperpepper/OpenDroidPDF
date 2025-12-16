package org.opendroidpdf.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.TypedValue;

import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.preferences.PenPrefsSnapshot;
import org.opendroidpdf.app.preferences.PenPreferencesServiceImpl;
import org.opendroidpdf.app.preferences.SharedPreferencesPenPrefsStore;

/**
 * Aggregates editor-related preferences for the PageView and renderers.
 * Delegates pen thickness/color to {@link PenPreferences} and exposes
 * eraser thickness and selection toggles with sensible defaults.
 */
public class EditorPreferences {
    private final Context context;
    private final SharedPreferences prefs;
    private final PenPreferencesServiceImpl penPreferences;

    public EditorPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        PreferenceManager.setDefaultValues(this.context, SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS, R.xml.preferences, false);
        this.penPreferences = new PenPreferencesServiceImpl(
                new SharedPreferencesPenPrefsStore(
                        this.context.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS),
                        getFloatDimen(R.dimen.pen_size_min),
                        getFloatDimen(R.dimen.pen_size_max),
                        getFloatDimen(R.dimen.pen_size_step),
                        getFloatDimen(R.dimen.ink_thickness_default)));
    }

    private static final String KEY_ERASER_THICKNESS = "pref_eraser_thickness";
    private static final String KEY_SMART_TEXT_SELECTION = "pref_smart_text_selection";
    private static final String KEY_HIGHLIGHT_COLOR = "pref_highlight_color";
    private static final String KEY_UNDERLINE_COLOR = "pref_underline_color";
    private static final String KEY_STRIKEOUT_COLOR = "pref_strikeout_color";
    public static final int DEFAULT_ERASER_COLOR = 0xFFFFFFFF;

    // Pen settings
    public float getInkThickness() { return penPreferences.get().thickness; }
    public void setInkThickness(float value) { penPreferences.setThickness(value); }
    public int getInkColorHex() { return ColorPalette.getHex(penPreferences.get().colorIndex); }

    // Eraser settings
    public float getEraserThickness() {
        float fallback = getFloatDimen(R.dimen.eraser_thickness_default);
        try {
            return Float.parseFloat(prefs.getString(KEY_ERASER_THICKNESS, Float.toString(fallback)).replaceAll("[^0-9.]",""));
        } catch (NumberFormatException ignore) {
            return fallback;
        }
    }

    // Selection behavior
    public boolean isSmartTextSelectionEnabled() {
        return prefs.getBoolean(KEY_SMART_TEXT_SELECTION, true);
    }

    // Optional annotation colors (not currently used by PageView overlay)
    public int getHighlightColorHex() { return getPaletteHex(KEY_HIGHLIGHT_COLOR); }
    public int getUnderlineColorHex() { return getPaletteHex(KEY_UNDERLINE_COLOR); }
    public int getStrikeoutColorHex() { return getPaletteHex(KEY_STRIKEOUT_COLOR); }

    private int getPaletteHex(String key) {
        try {
            int idx = Integer.parseInt(prefs.getString(key, "0"));
            return ColorPalette.getHex(idx);
        } catch (NumberFormatException ignore) {
            return ColorPalette.getHex(0);
        }
    }

    private float getFloatDimen(int resId) {
        TypedValue typedValue = new TypedValue();
        context.getResources().getValue(resId, typedValue, true);
        return typedValue.getFloat();
    }
}
