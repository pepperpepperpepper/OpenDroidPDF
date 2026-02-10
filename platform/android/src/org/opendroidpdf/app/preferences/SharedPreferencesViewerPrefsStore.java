package org.opendroidpdf.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.reader.FlingMomentum;
import org.opendroidpdf.app.reader.PagingAxis;
import org.opendroidpdf.app.reader.ScrollMode;

/** SharedPreferences-backed viewer/navigation prefs store. */
public final class SharedPreferencesViewerPrefsStore implements ViewerPrefsStore {
    private final SharedPreferences prefs;

    public SharedPreferencesViewerPrefsStore(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
    }

    @Override
    public ViewerPrefsSnapshot load() {
        boolean useStylus = prefs.getBoolean(SettingsActivity.PREF_USE_STYLUS, false);
        boolean fitWidth = prefs.getBoolean(SettingsActivity.PREF_FIT_WIDTH, true);
        String scrollModePref = prefs.getString(SettingsActivity.PREF_READER_SCROLL_MODE, ScrollMode.CONTINUOUS.prefValue);
        ScrollMode scrollMode = ScrollMode.fromPrefValue(scrollModePref);
        String axisPref = prefs.getString(SettingsActivity.PREF_PAGE_PAGING_AXIS, PagingAxis.VERTICAL.prefValue);
        PagingAxis pagingAxis = PagingAxis.fromPrefValue(axisPref);
        String flingPref = prefs.getString(SettingsActivity.PREF_READER_FLING_MOMENTUM, FlingMomentum.NORMAL.prefValue);
        FlingMomentum flingMomentum = FlingMomentum.fromPrefValue(flingPref);
        boolean nightMode = prefs.getBoolean(SettingsActivity.PREF_NIGHT_MODE, false);
        return new ViewerPrefsSnapshot(useStylus, fitWidth, scrollMode, pagingAxis, flingMomentum, nightMode);
    }
}
