package org.opendroidpdf.app.preferences;

import android.content.SharedPreferences;

/** Android-backed FreeText style prefs store; wraps SharedPreferences but keeps it out of the service API. */
public class SharedPreferencesTextStylePrefsStore implements TextStylePrefsStore {
    private static final String PREF_TEXT_FONT_SIZE = "pref_text_font_size";
    private static final String PREF_TEXT_COLOR_INDEX = "pref_text_color_index";

    private final SharedPreferences prefs;
    private final float min;
    private final float max;
    private final float step;
    private final float def;

    public SharedPreferencesTextStylePrefsStore(SharedPreferences prefs,
                                                float minFontSize,
                                                float maxFontSize,
                                                float stepFontSize,
                                                float defaultFontSize) {
        this.prefs = prefs;
        this.min = minFontSize;
        this.max = maxFontSize;
        this.step = stepFontSize;
        this.def = defaultFontSize;
    }

    @Override
    public TextStylePrefsSnapshot load() {
        float fontSize = def;
        try {
            fontSize = prefs.getFloat(PREF_TEXT_FONT_SIZE, def);
        } catch (ClassCastException cce) {
            // Older builds may have stored as a String; migrate in-place.
            try {
                String raw = prefs.getString(PREF_TEXT_FONT_SIZE, null);
                if (raw != null) {
                    fontSize = Float.parseFloat(raw);
                }
            } catch (Exception ignored) {
                fontSize = def;
            }
            prefs.edit().remove(PREF_TEXT_FONT_SIZE).apply();
        }
        fontSize = clamp(fontSize, min, max);

        int colorIdx = 0;
        try {
            colorIdx = prefs.getInt(PREF_TEXT_COLOR_INDEX, 0);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_COLOR_INDEX, null);
                if (raw != null) {
                    colorIdx = Integer.parseInt(raw);
                }
            } catch (Exception ignored) {
                colorIdx = 0;
            }
            prefs.edit().remove(PREF_TEXT_COLOR_INDEX).apply();
        }

        return new TextStylePrefsSnapshot(fontSize, colorIdx, min, max, step, def);
    }

    @Override
    public void save(TextStylePrefsSnapshot snapshot) {
        prefs.edit()
                .putFloat(PREF_TEXT_FONT_SIZE, snapshot.fontSize)
                .putInt(PREF_TEXT_COLOR_INDEX, snapshot.colorIndex)
                .apply();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

