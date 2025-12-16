package org.opendroidpdf;

import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.activity.OnBackPressedCallback;

public class SettingsActivity extends androidx.appcompat.app.AppCompatActivity {
    final static String PREF_USE_STYLUS = "pref_use_stylus";
    final static String PREF_SCROLL_VERTICAL = "pref_scroll_vertical";
    final static String PREF_SCROLL_CONTINUOUS = "pref_scroll_continuous";
    final static String PREF_FIT_WIDTH = "pref_fit_width";
    public final static String PREF_INK_THICKNESS = "pref_ink_thickness";
    final static String PREF_ERASER_THICKNESS = "pref_eraser_thickness";
    public final static String PREF_INK_COLOR = "pref_ink_color";
    final static String PREF_HIGHLIGHT_COLOR = "pref_highlight_color";
    final static String PREF_UNDERLINE_COLOR = "pref_underline_color";
    final static String PREF_STRIKEOUT_COLOR = "pref_strikeout_color";
    final static String PREF_TEXTANNOTICON_COLOR = "pref_textannoticon_color";
    final static String PREF_ABOUT_VERSION = "pref_about_version";
    final static String PREF_ABOUT_LICENSE = "pref_about_license";
    final static String PREF_ABOUT_SOURCE = "pref_about_source";
    final static String PREF_ABOUT_ISSUES = "pref_about_issues";
    
    final static String PREF_NUMBER_RECENT_FILES = "pref_number_recent_files";
    
    final static String PREF_SAVE_ON_DESTROY = "pref_save_on_destroy";
    final static String PREF_SAVE_ON_STOP = "pref_save_on_stop";
    final static String PREF_SMART_TEXT_SELECTION = "pref_smart_text_selection";
    final static String PREF_KEEP_SCREEN_ON = "keep_screen_on";

	final static String PREF_EXPERIMENTAL_MODE = "experimental_mode";
	
    public final static String SHARED_PREFERENCES_STRING = "OpenDroidPDF";
    private final static String LEGACY_SHARED_PREFERENCES_STRING = "PenAndPDF";
    private final static String PREF_NAMESPACE_MIGRATED_FLAG = "__opendroidpdf_namespace_migrated__";
    private final static Object PREF_MIGRATION_LOCK = new Object();
    private final static String TAG = "SettingsActivity";
    private OnBackPressedCallback backPressedCallback;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ensurePreferencesNamespace(this);

        setContentView(R.layout.settings);
        Toolbar myToolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        String title = getString(R.string.app_name);
        androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
		if(actionBar != null){
			actionBar.setTitle(title);
		}
        
        // Add the fragment to the layout
        // if the savedInstanceState != null this is apprantly not necessary...
        if(savedInstanceState == null)
        {
            getFragmentManager().beginTransaction()
                .add(R.id.sub_layout, new SettingsFragment())
                .commit();
        }

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(false);
                finish();
                overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    static void ensurePreferencesNamespace(Context context) {
        if (context == null) {
            return;
        }

        synchronized (PREF_MIGRATION_LOCK) {
            final SharedPreferences targetPrefs = context.getSharedPreferences(SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            if (targetPrefs.getBoolean(PREF_NAMESPACE_MIGRATED_FLAG, false)) {
                return;
            }

            final SharedPreferences legacyPrefs = context.getSharedPreferences(LEGACY_SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            final Map<String, ?> legacyEntries = legacyPrefs.getAll();
            if (legacyEntries == null || legacyEntries.isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "ensurePreferencesNamespace: no legacy entries found");
                }
                targetPrefs.edit().putBoolean(PREF_NAMESPACE_MIGRATED_FLAG, true).apply();
                return;
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "ensurePreferencesNamespace: migrating " + legacyEntries.size() + " entries from " + LEGACY_SHARED_PREFERENCES_STRING);
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
                    Log.d(TAG, "ensurePreferencesNamespace: clearing legacy preferences after migration");
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
        File legacyFile = new File(baseDir, LEGACY_SHARED_PREFERENCES_STRING + ".xml");
        boolean deleted = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            deleted = context.deleteSharedPreferences(LEGACY_SHARED_PREFERENCES_STRING);
        }
        if (!deleted && legacyFile.exists()) {
            // Best effort: ignore failure.
            //noinspection ResultOfMethodCallIgnored
            legacyFile.delete();
        }
    }
}
