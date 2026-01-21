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
        boolean writeBackThickness = false;
        try {
            String raw = prefs.getString(PREF_INK_THICKNESS, null);
            if (raw != null) {
                thickness = Float.parseFloat(raw.replaceAll("[^0-9.]", ""));
            }
        } catch (ClassCastException cce) {
            // Older builds stored thickness as a float; migrate in-place.
            try {
                thickness = prefs.getFloat(PREF_INK_THICKNESS, def);
            } catch (Throwable ignored) {
                thickness = def;
            }
            writeBackThickness = true;
        } catch (Throwable t) {
            thickness = def;
        }
        thickness = clamp(thickness, min, max);

        int colorIdx = 0;
        boolean writeBackColor = false;
        try {
            String raw = prefs.getString(PREF_INK_COLOR, null);
            if (raw != null) {
                colorIdx = Integer.parseInt(raw.replaceAll("[^0-9-]", ""));
            }
        } catch (ClassCastException cce) {
            try {
                colorIdx = prefs.getInt(PREF_INK_COLOR, 0);
            } catch (Throwable ignored) {
                colorIdx = 0;
            }
            writeBackColor = true;
        } catch (Throwable t) {
            colorIdx = 0;
        }

        if (writeBackThickness || writeBackColor) {
            try {
                SharedPreferences.Editor e = prefs.edit();
                if (writeBackThickness) {
                    e.putString(PREF_INK_THICKNESS, Float.toString(thickness));
                }
                if (writeBackColor) {
                    e.putString(PREF_INK_COLOR, Integer.toString(colorIdx));
                }
                e.apply();
            } catch (Throwable ignore) {}
        }

        return new PenPrefsSnapshot(thickness, colorIdx, min, max, step, def);
    }

    @Override
    public void save(PenPrefsSnapshot snapshot) {
        prefs.edit()
                .putString(PREF_INK_THICKNESS, Float.toString(snapshot.thickness))
                .putString(PREF_INK_COLOR, Integer.toString(snapshot.colorIndex))
                .apply();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
