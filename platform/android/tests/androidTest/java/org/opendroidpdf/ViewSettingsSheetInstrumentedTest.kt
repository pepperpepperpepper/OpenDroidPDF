package org.opendroidpdf

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ViewSettingsSheetInstrumentedTest {

    @Test
    fun viewSettings_togglesPersistAndApply() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // Keep the test deterministic.
        setPrefBool(context, SettingsActivity.PREF_NIGHT_MODE, false)
        setPrefBool(context, SettingsActivity.PREF_READING_MODE, false)
        setPrefString(context, SettingsActivity.PREF_READER_SCROLL_MODE, "continuous")
        setPrefBool(context, SettingsActivity.PREF_SEEN_PAGE_INDICATOR_NAV_HINT, true)

        val pdf = copyAssetToFiles(context, "two_page_sample.pdf")
        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))

            openActionBarOverflowOrOptionsMenu(context)
            onView(withText(R.string.menu_view_settings)).perform(click())
            onView(withId(R.id.view_settings_sheet_root)).check(matches(isDisplayed()))

            // Switch to Single page.
            onView(withId(R.id.view_settings_scroll_mode_single_page)).perform(click())
            assertTrue(
                "Expected scroll mode pref to become paged",
                waitForPrefString(context, SettingsActivity.PREF_READER_SCROLL_MODE, "paged")
            )

            // Enable Night mode.
            onView(withId(R.id.view_settings_row_night_mode)).perform(click())
            assertTrue(
                "Expected night mode pref enabled",
                waitForPrefBool(context, SettingsActivity.PREF_NIGHT_MODE, true)
            )
            val nightColor = ContextCompat.getColor(context, R.color.window_background_night)
            assertTrue("Expected docView background to be night", waitForDocViewBackgroundColor(scenario, nightColor))

            // Disable Night mode.
            onView(withId(R.id.view_settings_row_night_mode)).perform(click())
            assertTrue(
                "Expected night mode pref disabled",
                waitForPrefBool(context, SettingsActivity.PREF_NIGHT_MODE, false)
            )
            val dayColor = ContextCompat.getColor(context, R.color.window_background)
            assertTrue("Expected docView background to be day", waitForDocViewBackgroundColor(scenario, dayColor))
        }
    }

    private fun waitForDocReady(scenario: ActivityScenario<OpenDroidPDFActivity>): Boolean {
        val deadline = System.currentTimeMillis() + 8000
        var ready = false
        while (System.currentTimeMillis() < deadline && !ready) {
            scenario.onActivity { act ->
                val dv = act.getDocView()
                val v = dv?.selectedView
                ready = dv != null && v != null && dv.width > 0 && dv.height > 0 && v.measuredWidth > 0 && v.measuredHeight > 0
            }
            if (!ready) Thread.sleep(50)
        }
        return ready
    }

    private fun waitForDocViewBackgroundColor(scenario: ActivityScenario<OpenDroidPDFActivity>, expected: Int): Boolean {
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            var matched = false
            scenario.onActivity { act ->
                val bg = act.getDocView()?.background
                val actual = (bg as? ColorDrawable)?.color
                matched = actual != null && actual == expected
            }
            if (matched) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun waitForPrefBool(context: Context, key: String, expected: Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            val prefs = prefs(context)
            if (prefs.getBoolean(key, !expected) == expected) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun waitForPrefString(context: Context, key: String, expected: String): Boolean {
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            val prefs = prefs(context)
            if (prefs.getString(key, null) == expected) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun setPrefBool(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).commit()
    }

    private fun setPrefString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).commit()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        SettingsActivity.SHARED_PREFERENCES_STRING,
        Context.MODE_MULTI_PROCESS
    )

    private fun copyAssetToFiles(context: Context, asset: String): File {
        val out = File(context.filesDir, asset)
        if (out.exists()) out.delete()
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }
}

