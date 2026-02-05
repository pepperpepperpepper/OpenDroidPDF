package org.opendroidpdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class SinglePageTapZonesInstrumentedTest {

    @Test
    fun singlePageTapZones_leftRightNavigatePrevNext() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        setPrefString(context, SettingsActivity.PREF_READER_SCROLL_MODE, "paged")

        val pdf = copyAssetToFiles(context, "two_page_sample.pdf")
        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))
            setPage(scenario, 0)
            assertTrue("Expected to be on page 0 after reset", waitForPage(scenario, 0))

            // Center tap should not flip pages.
            onView(isAssignableFrom(MuPDFReaderView::class.java)).perform(
                GeneralClickAction(Tap.SINGLE, GeneralLocation.CENTER, Press.FINGER)
            )
            assertTrue("Expected to remain on page 0 after center tap", waitForPage(scenario, 0))

            // Tap right side -> next page.
            onView(isAssignableFrom(MuPDFReaderView::class.java)).perform(
                GeneralClickAction(Tap.SINGLE, GeneralLocation.CENTER_RIGHT, Press.FINGER)
            )
            assertTrue("Expected to advance to page 1 after right tap", waitForPage(scenario, 1))

            // Tap left side -> previous page.
            onView(isAssignableFrom(MuPDFReaderView::class.java)).perform(
                GeneralClickAction(Tap.SINGLE, GeneralLocation.CENTER_LEFT, Press.FINGER)
            )
            assertTrue("Expected to go back to page 0 after left tap", waitForPage(scenario, 0))
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

    private fun currentPage(scenario: ActivityScenario<OpenDroidPDFActivity>): Int {
        var page = -1
        scenario.onActivity { act ->
            page = act.getDocView()?.selectedItemPosition ?: -1
        }
        return page
    }

    private fun setPage(scenario: ActivityScenario<OpenDroidPDFActivity>, page: Int) {
        scenario.onActivity { act ->
            act.getDocView()?.setDisplayedViewIndex(page)
        }
    }

    private fun waitForPage(scenario: ActivityScenario<OpenDroidPDFActivity>, expected: Int): Boolean {
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            if (currentPage(scenario) == expected) return true
            Thread.sleep(50)
        }
        return false
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

