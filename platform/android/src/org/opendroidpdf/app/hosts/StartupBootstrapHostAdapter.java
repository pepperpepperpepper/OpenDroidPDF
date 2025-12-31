package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.lifecycle.StartupBootstrap;

/** Adapter for {@link StartupBootstrap.Host} that delegates to {@link OpenDroidPDFActivity}. */
public final class StartupBootstrapHostAdapter implements StartupBootstrap.Host {
    private final OpenDroidPDFActivity activity;

    public StartupBootstrapHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @NonNull @Override public Context context() { return activity; }
    @Nullable @Override public OpenDroidPDFCore getCore() { return activity.getCore(); }
    @Nullable @Override public Object getLastCustomNonConfigurationInstance() { return activity.getLastCustomNonConfigurationInstance(); }
    @Override public void restoreFromLastNonConfig(@NonNull Object last) { activity.restoreFromLastNonConfig(last); }
    @Override public void setAlertBuilder(@NonNull AlertDialog.Builder builder) { activity.setAlertBuilder(builder); }
}
