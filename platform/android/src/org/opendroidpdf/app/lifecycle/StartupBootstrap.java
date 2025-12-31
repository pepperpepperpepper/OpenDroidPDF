package org.opendroidpdf.app.lifecycle;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.app.preferences.PreferencesNames;
import org.opendroidpdf.app.preferences.PreferencesNamespaceMigrator;

/**
 * Bootstraps preferences, alert builder, and debug hooks to declutter the activity.
 */
public final class StartupBootstrap {
    private StartupBootstrap() {}

    public interface Host {
        @NonNull Context context();
        @Nullable OpenDroidPDFCore getCore();
        @Nullable Object getLastCustomNonConfigurationInstance();
        void restoreFromLastNonConfig(@NonNull Object last);
        void setAlertBuilder(@NonNull AlertDialog.Builder builder);
    }

    public static Bundle recoverBundleIfNecessary(Bundle saved, ClassLoader loader) {
        // Call through activity in onCreate before super; keep helper here for parity if needed later.
        return saved; // no-op here; recovery remains in the activity for access-level reasons
    }

    public static org.opendroidpdf.app.preferences.PreferencesSubscription bootstrap(
            @NonNull Host host,
            @NonNull org.opendroidpdf.app.preferences.PreferencesCoordinator preferencesCoordinator,
            @Nullable org.opendroidpdf.app.debug.DebugActionsController.Host debugHost) {
        final Context ctx = host.context();
        // Set default preferences and ensure namespace
        PreferenceManager.setDefaultValues(ctx, PreferencesNames.CURRENT,
                Context.MODE_MULTI_PROCESS, R.xml.preferences, false);
        PreferencesNamespaceMigrator.ensureMigrated(ctx);

        // Register preference listener (lifecycle-owned) and trigger initial apply
        org.opendroidpdf.app.preferences.PreferencesSubscription subscription =
                org.opendroidpdf.app.preferences.PreferencesSubscription.start(
                        ctx,
                        preferencesCoordinator);
        preferencesCoordinator.refreshAndApply();

        // Alert builder used across dialogs
        host.setAlertBuilder(new AlertDialog.Builder(ctx));

        // Recover core from last configuration if none yet
        if (host.getCore() == null) {
            try {
                Object last = host.getLastCustomNonConfigurationInstance();
                if (last != null) host.restoreFromLastNonConfig(last);
            } catch (Throwable ignore) {}
        }

        // Debug hooks
        if (BuildConfig.DEBUG && debugHost != null) {
            org.opendroidpdf.app.debug.DebugActionsController.registerDebugBroadcasts(debugHost);
        }

        return subscription;
    }
}
