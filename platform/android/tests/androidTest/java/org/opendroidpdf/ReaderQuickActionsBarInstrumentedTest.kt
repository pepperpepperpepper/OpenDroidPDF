package org.opendroidpdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ReaderQuickActionsBarInstrumentedTest {

    @Test
    fun bottomBars_swapBetweenQuickActions_andToolSpecificBars() {
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

            onView(withId(R.id.reader_quick_actions_bar)).check(matches(isDisplayed()))

            onView(withId(R.id.quick_action_highlight)).perform(click())
            assertTrue(
                "Expected quick actions bar hidden in selection mode",
                waitForViewVisibility(scenario, R.id.reader_quick_actions_bar, View.GONE)
            )
            onView(withId(R.id.reader_quick_actions_bar)).check(matches(not(isDisplayed())))
            assertTrue(
                "Expected selection bottom bar visible in selection mode",
                waitForViewVisibility(scenario, R.id.reader_selection_actions_bar, View.VISIBLE)
            )
            onView(withId(R.id.reader_selection_actions_bar)).check(matches(isDisplayed()))

            onView(withId(R.id.selection_action_done)).perform(click())
            assertTrue(
                "Expected quick actions bar visible again after exiting selection mode",
                waitForViewVisibility(scenario, R.id.reader_quick_actions_bar, View.VISIBLE)
            )

            onView(withId(R.id.quick_action_add_text)).perform(click())
            assertTrue(
                "Expected add-text bottom bar visible in add-text mode",
                waitForViewVisibility(scenario, R.id.reader_add_text_actions_bar, View.VISIBLE)
            )
            onView(withId(R.id.reader_add_text_actions_bar)).check(matches(isDisplayed()))
            onView(withId(R.id.reader_quick_actions_bar)).check(matches(not(isDisplayed())))

            onView(withId(R.id.add_text_action_cancel)).perform(click())
            assertTrue(
                "Expected quick actions bar visible again after exiting add-text mode",
                waitForViewVisibility(scenario, R.id.reader_quick_actions_bar, View.VISIBLE)
            )

            onView(withId(R.id.quick_action_draw)).perform(click())
            assertTrue(
                "Expected annot bottom bar visible in drawing mode",
                waitForViewVisibility(scenario, R.id.reader_annot_actions_bar, View.VISIBLE)
            )
            onView(withId(R.id.reader_annot_actions_bar)).check(matches(isDisplayed()))
            onView(withId(R.id.reader_quick_actions_bar)).check(matches(not(isDisplayed())))

            onView(withId(R.id.annot_action_done)).perform(click())
            assertTrue(
                "Expected quick actions bar visible again after exiting drawing mode",
                waitForViewVisibility(scenario, R.id.reader_quick_actions_bar, View.VISIBLE)
            )
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

    private fun waitForViewVisibility(
        scenario: ActivityScenario<OpenDroidPDFActivity>,
        viewId: Int,
        expectedVisibility: Int,
    ): Boolean {
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            var vis: Int? = null
            scenario.onActivity { act ->
                vis = act.findViewById<View?>(viewId)?.visibility
            }
            if (vis == expectedVisibility) return true
            Thread.sleep(50)
        }
        return false
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
