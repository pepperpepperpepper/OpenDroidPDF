package org.opendroidpdf.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.TypedValue;

import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.services.PenPreferencesService;

/**
 * Central access point for pen-related preferences (thickness, color).
 * Moves persistence out of the activity to make future UI components
 * share the same state without duplicating key/format handling.
 */
public class PenPreferences implements PenPreferencesService {

    private final Context context;
    private final SharedPreferences prefs;

    public PenPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        PreferenceManager.setDefaultValues(this.context, SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS, R.xml.preferences, false);
    }

    @Override
    public SharedPreferences prefs() {
        return prefs;
    }

    @Override
    public float getThickness() {
        float fallback = getDefaultThickness();
        try {
            return Float.parseFloat(prefs.getString(SettingsActivity.PREF_INK_THICKNESS, Float.toString(fallback)));
        } catch (NumberFormatException ignore) {
            return fallback;
        }
    }

    @Override
    public void setThickness(float value) {
        final String valueString = String.format(java.util.Locale.US, "%.2f", value);
        prefs.edit().putString(SettingsActivity.PREF_INK_THICKNESS, valueString).commit();
    }

    @Override
    public int getColorIndex() {
        try {
            return Integer.parseInt(prefs.getString(SettingsActivity.PREF_INK_COLOR, "0"));
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    @Override
    public void setColorIndex(int index) {
        prefs.edit().putString(SettingsActivity.PREF_INK_COLOR, Integer.toString(Math.max(0, index))).commit();
    }

    @Override
    public float getMinThickness() {
        return getFloatDimen(R.dimen.pen_size_min);
    }

    @Override
    public float getMaxThickness() {
        return getFloatDimen(R.dimen.pen_size_max);
    }

    @Override
    public float getStepThickness() {
        return getFloatDimen(R.dimen.pen_size_step);
    }

    @Override
    public float getDefaultThickness() {
        return getFloatDimen(R.dimen.ink_thickness_default);
    }

    private float getFloatDimen(int resId) {
        TypedValue typedValue = new TypedValue();
        context.getResources().getValue(resId, typedValue, true);
        return typedValue.getFloat();
    }
}
