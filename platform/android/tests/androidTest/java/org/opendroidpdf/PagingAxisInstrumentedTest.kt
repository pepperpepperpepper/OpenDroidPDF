package org.opendroidpdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PagingAxisInstrumentedTest {

    @Test
    fun pagingAxisHorizontal_swipeLeftMovesToNextPage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        setPagingAxisPref(context, "horizontal")

        val pdf = copyAssetToFiles(context, "two_page_sample.pdf")
        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))
            setPage(scenario, 0)
            assertTrue("Expected to be on page 0 after reset", waitForPage(scenario, 0))

            onView(isAssignableFrom(MuPDFReaderView::class.java)).perform(
                GeneralSwipeAction(Swipe.FAST, GeneralLocation.CENTER_RIGHT, GeneralLocation.CENTER_LEFT, Press.FINGER)
            )

            assertTrue("Expected page to advance to 1 after swipe left", waitForPage(scenario, 1))
        }
    }

    @Test
    fun pagingAxisVertical_swipeUpMovesToNextPage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        setPagingAxisPref(context, "vertical")

        val pdf = copyAssetToFiles(context, "two_page_sample.pdf")
        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))
            setPage(scenario, 0)
            assertTrue("Expected to be on page 0 after reset", waitForPage(scenario, 0))

            onView(isAssignableFrom(MuPDFReaderView::class.java)).perform(
                GeneralSwipeAction(Swipe.FAST, GeneralLocation.BOTTOM_CENTER, GeneralLocation.TOP_CENTER, Press.FINGER)
            )

            assertTrue("Expected page to advance to 1 after swipe up", waitForPage(scenario, 1))
        }
    }

    private fun setPagingAxisPref(context: Context, value: String) {
        val sp = context.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS)
        sp.edit().putString(SettingsActivity.PREF_PAGE_PAGING_AXIS, value).apply()
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

    private fun copyAssetToFiles(context: Context, asset: String): File {
        val out = File(context.filesDir, asset)
        if (out.exists()) out.delete()
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }
}
