package org.opendroidpdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class PreferencesMigrationTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearPrefs(SettingsActivity.SHARED_PREFERENCES_STRING);
        clearPrefs("PenAndPDF");
    }

    @Test
    public void migratesLegacyValuesIntoOpenDroidNamespace() {
        SharedPreferences legacy = context.getSharedPreferences("PenAndPDF", Context.MODE_MULTI_PROCESS);
        legacy.edit()
                .putString(SettingsActivity.PREF_INK_THICKNESS, "2.75")
                .putString(SettingsActivity.PREF_INK_COLOR, "7")
                .putBoolean(SettingsActivity.PREF_FIT_WIDTH, true)
                .apply();

        SettingsActivity.ensurePreferencesNamespace(context);

        SharedPreferences current = context.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        assertEquals("2.75", current.getString(SettingsActivity.PREF_INK_THICKNESS, ""));
        assertEquals("7", current.getString(SettingsActivity.PREF_INK_COLOR, ""));
        assertTrue(current.getBoolean(SettingsActivity.PREF_FIT_WIDTH, false));
        assertTrue(current.getBoolean("__opendroidpdf_namespace_migrated__", false));

        Map<String, ?> leftover = legacy.getAll();
        assertTrue(leftover == null || leftover.isEmpty());
    }

    private void clearPrefs(String name) {
        SharedPreferences prefs = context.getSharedPreferences(name, Context.MODE_MULTI_PROCESS);
        prefs.edit().clear().commit();
    }
}
