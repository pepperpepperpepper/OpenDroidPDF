package org.opendroidpdf.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lifecycle-owned SharedPreferences listener registration for applying preference snapshots.
 * <p>
 * This keeps SharedPreferences plumbing out of UI types (Activity/Fragment) and ensures we always
 * unregister on teardown.
 */
public final class PreferencesSubscription {

    private final SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener;
    private boolean active;

    private PreferencesSubscription(SharedPreferences prefs,
                                    SharedPreferences.OnSharedPreferenceChangeListener listener) {
        this.prefs = prefs;
        this.listener = listener;
    }

    public static PreferencesSubscription start(Context context,
                                                PreferencesCoordinator preferencesCoordinator) {
        return start(context.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS),
                preferencesCoordinator);
    }

    public static PreferencesSubscription start(SharedPreferences prefs,
                                                PreferencesCoordinator preferencesCoordinator) {
        SharedPreferences.OnSharedPreferenceChangeListener listener =
                (sharedPrefs, key) -> preferencesCoordinator.refreshAndApply();
        PreferencesSubscription subscription = new PreferencesSubscription(prefs, listener);
        subscription.register();
        return subscription;
    }

    public void stop() {
        unregister();
    }

    private void register() {
        if (active) return;
        prefs.registerOnSharedPreferenceChangeListener(listener);
        active = true;
    }

    private void unregister() {
        if (!active) return;
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
        active = false;
    }
}
