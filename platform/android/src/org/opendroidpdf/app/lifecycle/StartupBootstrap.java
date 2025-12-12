package org.opendroidpdf.app.lifecycle;

import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;

/**
 * Bootstraps preferences, alert builder, and debug hooks to declutter the activity.
 */
public final class StartupBootstrap {
    private StartupBootstrap() {}

    public static Bundle recoverBundleIfNecessary(Bundle saved, ClassLoader loader) {
        // Call through activity in onCreate before super; keep helper here for parity if needed later.
        return saved; // no-op here; recovery remains in the activity for access-level reasons
    }

    public static void bootstrap(OpenDroidPDFActivity activity) {
        // Set default preferences and ensure namespace
        PreferenceManager.setDefaultValues(activity, SettingsActivity.SHARED_PREFERENCES_STRING,
                OpenDroidPDFActivity.MODE_MULTI_PROCESS, R.xml.preferences, false);
        try { SettingsActivity.class.getMethod("ensurePreferencesNamespace", android.content.Context.class).invoke(null, activity); }
        catch (Throwable ignore) {}

        // Register preference listener and trigger initial apply
        activity.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING,
                OpenDroidPDFActivity.MODE_MULTI_PROCESS)
                .registerOnSharedPreferenceChangeListener(activity);
        activity.onSharedPreferenceChanged(
                activity.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING,
                        OpenDroidPDFActivity.MODE_MULTI_PROCESS), "");

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
            org.opendroidpdf.app.debug.DebugActionsController.registerDebugBroadcasts(activity);
        }
    }
}
