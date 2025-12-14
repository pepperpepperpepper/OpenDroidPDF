package org.opendroidpdf;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.WindowManager;

/**
 * Computes and applies preference changes so the activity can stay lean.
 */
public final class PreferenceApplier {
    private PreferenceApplier() {}

    public static final class State {
        public boolean keepScreenOn;
        public boolean saveOnStop;
        public boolean saveOnDestroy;
        public int numberRecentFiles;
    }

    public static State compute(Context ctx, SharedPreferences prefs) {
        State s = new State();
        s.keepScreenOn = prefs.getBoolean(SettingsActivity.PREF_KEEP_SCREEN_ON, false);
        s.saveOnStop = prefs.getBoolean(SettingsActivity.PREF_SAVE_ON_STOP, true);
        s.saveOnDestroy = prefs.getBoolean(SettingsActivity.PREF_SAVE_ON_DESTROY, true);
        try {
            String def = ctx.getResources().getString(R.string.number_recent_files_default);
            s.numberRecentFiles = Integer.parseInt(prefs.getString(SettingsActivity.PREF_NUMBER_RECENT_FILES, def));
        } catch (Exception e) {
            s.numberRecentFiles = 20;
        }
        return s;
    }

    public static void applyKeepScreenOn(Activity activity, boolean keepScreenOn) {
        if (keepScreenOn) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public static void applyToViews(SharedPreferences prefs, String key, MuPDFReaderView docView, OpenDroidPDFCore core, Context ctx) {
        ReaderView.onSharedPreferenceChanged(prefs, key);
        PageView.onSharedPreferenceChanged(prefs, key, ctx);
        if (docView != null) docView.onSharedPreferenceChanged(prefs, key);
        if (core != null) core.onSharedPreferenceChanged(prefs, key);
    }

    /**
     * Handles a preference change end-to-end for the activity: recompute the
     * cached state, apply window flags, and fan out to the active views.
     */
    public static State handlePreferenceChanged(OpenDroidPDFActivity activity, SharedPreferences prefs, String key) {
        State st = compute(activity, prefs);
        applyKeepScreenOn(activity, st.keepScreenOn);
        activity.setSaveFlags(st.saveOnStop, st.saveOnDestroy, st.numberRecentFiles);
        applyToViews(prefs, key, activity.getDocView(), activity.getCore(), activity);
        return st;
    }
}
