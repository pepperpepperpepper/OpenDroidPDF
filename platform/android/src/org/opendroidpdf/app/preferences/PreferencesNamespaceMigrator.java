package org.opendroidpdf.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.opendroidpdf.BuildConfig;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Best-effort migration of legacy SharedPreferences file naming.
 * <p>
 * Historically, preferences lived under {@link PreferencesNames#LEGACY}. We now use
 * {@link PreferencesNames#CURRENT}, but we must migrate values so users retain settings across upgrades.
 */
public final class PreferencesNamespaceMigrator {
    private static final String PREF_NAMESPACE_MIGRATED_FLAG = "__opendroidpdf_namespace_migrated__";
    private static final Object PREF_MIGRATION_LOCK = new Object();
    private static final String TAG = "PrefsNamespaceMigrator";

    private PreferencesNamespaceMigrator() {}

    public static void ensureMigrated(Context context) {
        if (context == null) {
            return;
        }

        synchronized (PREF_MIGRATION_LOCK) {
            final SharedPreferences targetPrefs =
                    context.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
            if (targetPrefs.getBoolean(PREF_NAMESPACE_MIGRATED_FLAG, false)) {
                return;
            }

            final SharedPreferences legacyPrefs =
                    context.getSharedPreferences(PreferencesNames.LEGACY, Context.MODE_MULTI_PROCESS);
            final Map<String, ?> legacyEntries = legacyPrefs.getAll();
            if (legacyEntries == null || legacyEntries.isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "ensureMigrated: no legacy entries found");
                }
                targetPrefs.edit().putBoolean(PREF_NAMESPACE_MIGRATED_FLAG, true).apply();
                return;
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "ensureMigrated: migrating " + legacyEntries.size() + " entries from " + PreferencesNames.LEGACY);
            }

            boolean migratedAny = false;
            final SharedPreferences.Editor editor = targetPrefs.edit();
            for (Map.Entry<String, ?> entry : legacyEntries.entrySet()) {
                final String key = entry.getKey();
                final Object value = entry.getValue();

                if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                    migratedAny = true;
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                    migratedAny = true;
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                    migratedAny = true;
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                    migratedAny = true;
                } else if (value instanceof String) {
                    editor.putString(key, (String) value);
                    migratedAny = true;
                } else if (value instanceof Set) {
                    try {
                        @SuppressWarnings("unchecked")
                        final Set<String> stringSet = new HashSet<>((Set<String>) value);
                        editor.putStringSet(key, stringSet);
                        migratedAny = true;
                    } catch (ClassCastException ignore) {
                        // Ignore malformed legacy entries; we simply won't migrate them.
                    }
                }
            }

            editor.putBoolean(PREF_NAMESPACE_MIGRATED_FLAG, true);
            editor.apply();

            if (migratedAny) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "ensureMigrated: clearing legacy preferences after migration");
                }
                legacyPrefs.edit().clear().apply();
                deleteLegacySharedPreferencesFile(context);
            }
        }
    }

    private static void deleteLegacySharedPreferencesFile(Context context) {
        if (context == null) {
            return;
        }

        File baseDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        File legacyFile = new File(baseDir, PreferencesNames.LEGACY + ".xml");
        boolean deleted = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            deleted = context.deleteSharedPreferences(PreferencesNames.LEGACY);
        }
        if (!deleted && legacyFile.exists()) {
            // Best effort: ignore failure.
            //noinspection ResultOfMethodCallIgnored
            legacyFile.delete();
        }
    }
}

