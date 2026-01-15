package org.opendroidpdf.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.reader.PagingAxis;

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
        String axisPref = prefs.getString(SettingsActivity.PREF_PAGE_PAGING_AXIS, PagingAxis.HORIZONTAL.prefValue);
        PagingAxis pagingAxis = PagingAxis.fromPrefValue(axisPref);
        return new ViewerPrefsSnapshot(useStylus, fitWidth, pagingAxis);
    }
}
