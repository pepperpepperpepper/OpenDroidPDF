package org.opendroidpdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class EdgeScrubberTabInstrumentedTest {

    @Test
    fun edgeScrubberTab_tapOpensGoToPageDialog() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        setPrefBool(context, SettingsActivity.PREF_READING_MODE, false)
        setPrefBool(context, SettingsActivity.PREF_SEEN_PAGE_INDICATOR_NAV_HINT, true)

        val pdf = copyAssetToFiles(context, "two_page_sample.pdf")
        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))
            scenario.onActivity { it.setTitle() }

            onView(withId(R.id.page_scrubber_tab)).check(matches(isDisplayed()))
            onView(withId(R.id.page_scrubber_tab)).perform(click())

            onView(withId(R.id.dialog_text_input)).check(matches(isDisplayed()))
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

    private fun setPrefBool(context: Context, key: String, value: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(
            SettingsActivity.SHARED_PREFERENCES_STRING,
            Context.MODE_MULTI_PROCESS
        )
        prefs.edit().putBoolean(key, value).commit()
    }

    private fun copyAssetToFiles(context: Context, asset: String): File {
        val out = File(context.filesDir, asset)
        if (out.exists()) out.delete()
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }
}

