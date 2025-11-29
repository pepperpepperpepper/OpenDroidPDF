package org.opendroidpdf;

import android.app.Application;
import android.content.res.Resources;

public class OpenDroidPDFApp extends Application {
    private static OpenDroidPDFApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static Resources getAppResources() {
        return instance != null ? instance.getResources() : null;
    }
}
