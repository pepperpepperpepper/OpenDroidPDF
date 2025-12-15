package org.opendroidpdf.app;

import android.app.Application;

import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.preferences.PenPreferences;
import org.opendroidpdf.app.services.PenPreferencesService;
import org.opendroidpdf.core.MuPdfRepository;

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
            penPreferences = new PenPreferences(app);
        }
        return penPreferences;
    }

    public MuPdfRepository newRepository(OpenDroidPDFCore core) {
        return core == null ? null : new MuPdfRepository(core);
    }
}
