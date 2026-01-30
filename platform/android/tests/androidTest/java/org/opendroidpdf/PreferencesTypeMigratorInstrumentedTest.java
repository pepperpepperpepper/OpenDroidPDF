package org.opendroidpdf;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class PreferencesTypeMigratorInstrumentedTest {

    @Test
    public void invalidPagingAxisPrefType_doesNotCrashOnStartup() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs =
                context.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        prefs.edit().clear().commit();

        // Simulate a legacy build mistakenly persisting this string preference as an integer.
        prefs.edit().putInt(SettingsActivity.PREF_PAGE_PAGING_AXIS, 1).commit();

        Intent intent = new Intent(context, OpenDroidPDFActivity.class);
        intent.setAction(Intent.ACTION_MAIN);

        try (ActivityScenario<OpenDroidPDFActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                // No-op; the assertion is that launch did not crash.
            });
        }

        Map<String, ?> all = prefs.getAll();
        Object migrated = all != null ? all.get(SettingsActivity.PREF_PAGE_PAGING_AXIS) : null;
        assertTrue(migrated == null || migrated instanceof String);
    }

    @Test
    public void pagingAxisDefault_horizontalMigratesToVerticalOnce() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs =
                context.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        prefs.edit().clear().commit();

        // Simulate the historical default being written into prefs.
        prefs.edit().putString(SettingsActivity.PREF_PAGE_PAGING_AXIS, "horizontal").commit();

        Intent intent = new Intent(context, OpenDroidPDFActivity.class);
        intent.setAction(Intent.ACTION_MAIN);

        try (ActivityScenario<OpenDroidPDFActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                // No-op; we only need bootstrap/migrator to run.
            });
        }

        assertTrue("Expected paging axis to migrate to vertical",
                "vertical".equals(prefs.getString(SettingsActivity.PREF_PAGE_PAGING_AXIS, "")));

        // User explicitly selects horizontal again; should not be flipped back on subsequent launches.
        prefs.edit().putString(SettingsActivity.PREF_PAGE_PAGING_AXIS, "horizontal").commit();

        try (ActivityScenario<OpenDroidPDFActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                // No-op
            });
        }

        assertTrue("Expected paging axis to remain horizontal after user change",
                "horizontal".equals(prefs.getString(SettingsActivity.PREF_PAGE_PAGING_AXIS, "")));
    }
}
