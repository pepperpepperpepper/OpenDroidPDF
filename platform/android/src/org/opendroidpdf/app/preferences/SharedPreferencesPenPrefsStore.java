package org.opendroidpdf.app.preferences;

import android.content.SharedPreferences;

/** Android-backed pen prefs store; wraps SharedPreferences but keeps it out of the service API. */
public class SharedPreferencesPenPrefsStore implements PenPrefsStore {
    private static final String PREF_INK_THICKNESS = "pref_ink_thickness";
    private static final String PREF_INK_COLOR = "pref_ink_color";

    private final SharedPreferences prefs;
    private final float min;
    private final float max;
    private final float step;
    private final float def;

    public SharedPreferencesPenPrefsStore(SharedPreferences prefs,
                                          float minThickness,
                                          float maxThickness,
                                          float stepThickness,
                                          float defaultThickness) {
        this.prefs = prefs;
        this.min = minThickness;
        this.max = maxThickness;
        this.step = stepThickness;
        this.def = defaultThickness;
    }

    @Override
    public PenPrefsSnapshot load() {
        float thickness = def;
        try {
            thickness = prefs.getFloat(PREF_INK_THICKNESS, def);
        } catch (ClassCastException cce) {
            // Older builds stored thickness as a String; migrate in-place.
            try {
                String raw = prefs.getString(PREF_INK_THICKNESS, null);
                if (raw != null) {
                    thickness = Float.parseFloat(raw);
                }
            } catch (Exception ignored) {
                thickness = def;
            }
            prefs.edit().remove(PREF_INK_THICKNESS).apply();
        }
        thickness = clamp(thickness, min, max);

        int colorIdx = 0;
        try {
            colorIdx = prefs.getInt(PREF_INK_COLOR, 0);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_INK_COLOR, null);
                if (raw != null) {
                    colorIdx = Integer.parseInt(raw);
                }
            } catch (Exception ignored) {
                colorIdx = 0;
            }
            prefs.edit().remove(PREF_INK_COLOR).apply();
        }

        return new PenPrefsSnapshot(thickness, colorIdx, min, max, step, def);
    }

    @Override
    public void save(PenPrefsSnapshot snapshot) {
        prefs.edit()
                .putFloat(PREF_INK_THICKNESS, snapshot.thickness)
                .putInt(PREF_INK_COLOR, snapshot.colorIndex)
                .apply();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
