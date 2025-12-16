package org.opendroidpdf.app;

import android.app.Application;
import android.content.Context;

import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.preferences.PenPreferencesServiceImpl;
import org.opendroidpdf.app.preferences.SharedPreferencesPenPrefsStore;
import org.opendroidpdf.app.services.PenPreferencesService;
import org.opendroidpdf.core.MuPdfRepository;
import android.util.TypedValue;

/**
 * Lightweight service locator to keep a single place for app-scoped helpers.
 * This avoids re-instantiating preferences/repositories from activities and
 * prepares the codebase for a future multi-module split.
 */
public final class AppServices {
    private static AppServices instance;

    private final Application app;
    private PenPreferencesService penPreferences;

    private AppServices(Application application) {
        this.app = application;
    }

    public static synchronized AppServices init(Application application) {
        if (instance == null) {
            instance = new AppServices(application);
        }
        return instance;
    }

    public static AppServices get() {
        if (instance == null) {
            throw new IllegalStateException("AppServices not initialized");
        }
        return instance;
    }

    public PenPreferencesService penPreferences() {
        if (penPreferences == null) {
            float min = resFloat(R.dimen.pen_size_min);
            float max = resFloat(R.dimen.pen_size_max);
            float step = resFloat(R.dimen.pen_size_step);
            float def = resFloat(R.dimen.ink_thickness_default);
            penPreferences = new PenPreferencesServiceImpl(
                    new SharedPreferencesPenPrefsStore(
                            app.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS),
                            min,
                            max,
                            step,
                            def));
        }
        return penPreferences;
    }

    private float resFloat(int resId) {
        TypedValue tv = new TypedValue();
        app.getResources().getValue(resId, tv, true);
        return tv.getFloat();
    }

    public MuPdfRepository newRepository(OpenDroidPDFCore core) {
        return core == null ? null : new MuPdfRepository(core);
    }
}
