package org.opendroidpdf.app.reader;

/**
 * User-facing fling momentum presets for continuous scrolling.
 *
 * <p>This intentionally uses stable string values for persistence via SharedPreferences.</p>
 */
public enum FlingMomentum {
    SHORT("short", 0.75f),
    NORMAL("normal", 1.0f),
    LONG("long", 1.5f);

    public final String prefValue;
    public final float velocityMultiplier;

    FlingMomentum(String prefValue, float velocityMultiplier) {
        this.prefValue = prefValue;
        this.velocityMultiplier = velocityMultiplier;
    }

    public static FlingMomentum fromPrefValue(String value) {
        if (value == null) return NORMAL;
        for (FlingMomentum m : values()) {
            if (m.prefValue.equals(value)) return m;
        }
        return NORMAL;
    }
}

