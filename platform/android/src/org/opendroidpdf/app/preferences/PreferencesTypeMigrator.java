package org.opendroidpdf.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.reader.PagingAxis;

import java.util.Map;

/**
 * One-time migrations for preference value types.
 *
 * <p>The legacy {@code android.preference} UI persists {@code ListPreference} and {@code EditTextPreference}
 * values as Strings. Some refactors started persisting numeric types directly, which makes Settings crash
 * on launch (SharedPreferences throws ClassCastException when reading a numeric as String).</p>
 */
public final class PreferencesTypeMigrator {
    private PreferencesTypeMigrator() {}

    public static void ensureMigrated(Context context) {
        if (context == null) return;

        // Use the provided Context so we hit the same SharedPreferences instance as Settings uses.
        SharedPreferences prefs =
                context.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);

        Map<String, ?> all;
        try {
            all = prefs.getAll();
        } catch (Throwable t) {
            return;
        }
        if (all == null || all.isEmpty()) return;

        SharedPreferences.Editor e = prefs.edit();
        boolean changed = false;

        // String-backed preferences that must never be stored as numeric types.
        changed |= coerceToString(e, all, SettingsActivity.PREF_INK_THICKNESS);
        changed |= coerceToString(e, all, SettingsActivity.PREF_ERASER_THICKNESS);
        changed |= coerceToString(e, all, SettingsActivity.PREF_INK_COLOR);
        changed |= coerceToString(e, all, SettingsActivity.PREF_HIGHLIGHT_COLOR);
        changed |= coerceToString(e, all, SettingsActivity.PREF_UNDERLINE_COLOR);
        changed |= coerceToString(e, all, SettingsActivity.PREF_STRIKEOUT_COLOR);
        changed |= coerceToString(e, all, SettingsActivity.PREF_TEXTANNOTICON_COLOR);
        changed |= coerceToString(e, all, SettingsActivity.PREF_NUMBER_RECENT_FILES);
        changed |= normalizePagingAxis(e, all, SettingsActivity.PREF_PAGE_PAGING_AXIS);

        if (changed) {
            try {
                // Settings reads immediately after calling this, so use commit for safety.
                e.commit();
            } catch (Throwable ignore) {}
        }
    }

    private static boolean normalizePagingAxis(SharedPreferences.Editor e, Map<String, ?> all, String key) {
        if (e == null || all == null || key == null) return false;
        Object value = all.get(key);
        if (value == null) return false;
        if (value instanceof String) {
            String s = (String) value;
            if (PagingAxis.HORIZONTAL.prefValue.equals(s) || PagingAxis.VERTICAL.prefValue.equals(s)) {
                return false;
            }
        }
        // Drop unknown values (or wrong types) so Settings can fall back to the XML default.
        try {
            e.remove(key);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean coerceToString(SharedPreferences.Editor e, Map<String, ?> all, String key) {
        if (e == null || all == null || key == null) return false;
        Object value = all.get(key);
        if (value == null) return false;
        if (value instanceof String) return false;
        try {
            if (value instanceof Number || value instanceof Boolean) {
                e.putString(key, String.valueOf(value));
            } else {
                // Defensive fallback (unexpected types, e.g., Set<String>).
                e.remove(key);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
