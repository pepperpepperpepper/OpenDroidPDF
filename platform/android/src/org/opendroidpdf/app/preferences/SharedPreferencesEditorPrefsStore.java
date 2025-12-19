package org.opendroidpdf.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;

import org.opendroidpdf.SettingsActivity;

/** SharedPreferences-backed editor/annotation prefs store (excluding pen thickness/color). */
public final class SharedPreferencesEditorPrefsStore implements EditorPrefsStore {
    private static final String KEY_ERASER_THICKNESS = SettingsActivity.PREF_ERASER_THICKNESS;
    private static final String KEY_SMART_TEXT_SELECTION = SettingsActivity.PREF_SMART_TEXT_SELECTION;
    private static final String KEY_HIGHLIGHT_COLOR = SettingsActivity.PREF_HIGHLIGHT_COLOR;
    private static final String KEY_UNDERLINE_COLOR = SettingsActivity.PREF_UNDERLINE_COLOR;
    private static final String KEY_STRIKEOUT_COLOR = SettingsActivity.PREF_STRIKEOUT_COLOR;
    private static final String KEY_TEXTANNOTICON_COLOR = SettingsActivity.PREF_TEXTANNOTICON_COLOR;

    // Defaults mirror res/xml/preferences.xml ListPreference defaults.
    private static final int DEFAULT_HIGHLIGHT_COLOR_INDEX = 21;
    private static final int DEFAULT_UNDERLINE_COLOR_INDEX = 3;
    private static final int DEFAULT_STRIKEOUT_COLOR_INDEX = 15;
    private static final int DEFAULT_TEXTANNOTICON_COLOR_INDEX = 10;

    private final Context context;
    private final SharedPreferences prefs;

    public SharedPreferencesEditorPrefsStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
    }

    @Override
    public EditorPrefsSnapshot load() {
        float eraserThickness = readPrefFloatString(KEY_ERASER_THICKNESS, defaultEraserThickness());
        boolean smartSel = prefs.getBoolean(KEY_SMART_TEXT_SELECTION, true);

        int highlightIdx = readPrefIntString(KEY_HIGHLIGHT_COLOR, DEFAULT_HIGHLIGHT_COLOR_INDEX);
        int underlineIdx = readPrefIntString(KEY_UNDERLINE_COLOR, DEFAULT_UNDERLINE_COLOR_INDEX);
        int strikeoutIdx = readPrefIntString(KEY_STRIKEOUT_COLOR, DEFAULT_STRIKEOUT_COLOR_INDEX);
        int textIconIdx = readPrefIntString(KEY_TEXTANNOTICON_COLOR, DEFAULT_TEXTANNOTICON_COLOR_INDEX);

        return new EditorPrefsSnapshot(eraserThickness, smartSel, highlightIdx, underlineIdx, strikeoutIdx, textIconIdx);
    }

    private float defaultEraserThickness() {
        try {
            TypedValue tv = new TypedValue();
            context.getResources().getValue(org.opendroidpdf.R.dimen.eraser_thickness_default, tv, true);
            return tv.getFloat();
        } catch (Throwable t) {
            return 10.0f;
        }
    }

    private float readPrefFloatString(String key, float def) {
        try {
            String raw = prefs.getString(key, Float.toString(def));
            if (raw == null) return def;
            return Float.parseFloat(raw.replaceAll("[^0-9.]", ""));
        } catch (ClassCastException cce) {
            try {
                return prefs.getFloat(key, def);
            } catch (Throwable ignore) {
                return def;
            }
        } catch (Throwable t) {
            return def;
        }
    }

    private int readPrefIntString(String key, int def) {
        try {
            String raw = prefs.getString(key, Integer.toString(def));
            if (raw == null) return def;
            return Integer.parseInt(raw.replaceAll("[^0-9-]", ""));
        } catch (ClassCastException cce) {
            try {
                return prefs.getInt(key, def);
            } catch (Throwable ignore) {
                return def;
            }
        } catch (Throwable t) {
            return def;
        }
    }
}
