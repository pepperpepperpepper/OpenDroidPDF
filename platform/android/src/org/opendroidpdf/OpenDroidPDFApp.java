package org.opendroidpdf;

import android.app.Application;
import android.content.res.Resources;

import androidx.annotation.Nullable;

import org.opendroidpdf.app.diagnostics.AppLog;
import org.opendroidpdf.app.diagnostics.CrashReporter;
import org.opendroidpdf.app.diagnostics.SessionDiagnostics;

public class OpenDroidPDFApp extends Application {
    private static OpenDroidPDFApp instance;
    @Nullable private static SessionDiagnostics.PreviousSession previousSession;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        previousSession = SessionDiagnostics.beginNewSession(this);
        AppLog.init(this);
        CrashReporter.install(this);
    }

    public static Resources getAppResources() {
        return instance != null ? instance.getResources() : null;
    }

    @Nullable
    public static SessionDiagnostics.PreviousSession previousSession() {
        return previousSession;
    }
}
