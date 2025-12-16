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
        float thickness = prefs.getFloat(PREF_INK_THICKNESS, def);
        int colorIdx = prefs.getInt(PREF_INK_COLOR, 0);
        return new PenPrefsSnapshot(thickness, colorIdx, min, max, step, def);
    }

    @Override
    public void save(PenPrefsSnapshot snapshot) {
        prefs.edit()
                .putFloat(PREF_INK_THICKNESS, snapshot.thickness)
                .putInt(PREF_INK_COLOR, snapshot.colorIndex)
                .apply();
    }
}
