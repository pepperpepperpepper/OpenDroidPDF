package org.opendroidpdf.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;

/** SharedPreferences-backed activity-level prefs store. */
public final class SharedPreferencesAppPrefsStore implements AppPrefsStore {
    private final Context context;
    private final SharedPreferences prefs;

    public SharedPreferencesAppPrefsStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
    }

    @Override
    public AppPrefsSnapshot load() {
        boolean keepScreenOn = prefs.getBoolean(SettingsActivity.PREF_KEEP_SCREEN_ON, true);
        boolean saveOnStop = prefs.getBoolean(SettingsActivity.PREF_SAVE_ON_STOP, false);
        boolean saveOnDestroy = prefs.getBoolean(SettingsActivity.PREF_SAVE_ON_DESTROY, true);
        int numberRecentFiles = readPrefIntString(SettingsActivity.PREF_NUMBER_RECENT_FILES, defaultNumberRecentFiles());
        return new AppPrefsSnapshot(keepScreenOn, saveOnStop, saveOnDestroy, numberRecentFiles);
    }

    private int defaultNumberRecentFiles() {
        try {
            return Integer.parseInt(context.getResources().getString(R.string.number_recent_files_default));
        } catch (Throwable t) {
            return 20;
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
