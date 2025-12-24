package org.opendroidpdf.app.lifecycle;

import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.app.preferences.PreferencesNames;
import org.opendroidpdf.app.preferences.PreferencesNamespaceMigrator;

/**
 * Bootstraps preferences, alert builder, and debug hooks to declutter the activity.
 */
public final class StartupBootstrap {
    private StartupBootstrap() {}

    public static Bundle recoverBundleIfNecessary(Bundle saved, ClassLoader loader) {
        // Call through activity in onCreate before super; keep helper here for parity if needed later.
        return saved; // no-op here; recovery remains in the activity for access-level reasons
    }

    public static org.opendroidpdf.app.preferences.PreferencesSubscription bootstrap(
            OpenDroidPDFActivity activity,
            org.opendroidpdf.app.preferences.PreferencesCoordinator preferencesCoordinator) {
        // Set default preferences and ensure namespace
        PreferenceManager.setDefaultValues(activity, PreferencesNames.CURRENT,
                OpenDroidPDFActivity.MODE_MULTI_PROCESS, R.xml.preferences, false);
        PreferencesNamespaceMigrator.ensureMigrated(activity);

        // Register preference listener (lifecycle-owned) and trigger initial apply
        org.opendroidpdf.app.preferences.PreferencesSubscription subscription =
                org.opendroidpdf.app.preferences.PreferencesSubscription.start(
                        activity,
                        preferencesCoordinator);
        preferencesCoordinator.refreshAndApply();

        // Alert builder used across dialogs
        activity.setAlertBuilder(new AlertDialog.Builder(activity));

        // Recover core from last configuration if none yet
        if (activity.getCore() == null) {
            try {
                OpenDroidPDFCore last = (OpenDroidPDFCore) activity.getLastCustomNonConfigurationInstance();
                if (last != null) activity.setCoreFromLastNonConfig(last);
            } catch (Throwable ignore) {}
        }

        // Debug hooks
        if (BuildConfig.DEBUG) {
            org.opendroidpdf.app.debug.DebugActionsController.registerDebugBroadcasts(
                    new org.opendroidpdf.app.hosts.DebugActionsHostAdapter(activity));
        }

        return subscription;
    }
}
