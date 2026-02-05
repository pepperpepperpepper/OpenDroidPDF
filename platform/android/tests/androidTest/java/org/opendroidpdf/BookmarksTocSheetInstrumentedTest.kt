package org.opendroidpdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class BookmarksTocSheetInstrumentedTest {

    @Test
    fun bookmarks_addRenameDelete_andNavigate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        setPrefBool(context, SettingsActivity.PREF_SEEN_PAGE_INDICATOR_NAV_HINT, true)

        val pdf = copyAssetToFiles(context, "two_page_sample.pdf")
        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))
            setPage(scenario, 1)
            assertTrue("Expected to be on page 1 before adding bookmark", waitForPage(scenario, 1))

            scenario.onActivity { it.setTitle() }

            onView(withId(R.id.navigation_menu_button)).perform(click())
            onView(withId(R.id.navigation_menu_action_bookmarks)).perform(click())
            onView(withId(R.id.bookmarks_toc_sheet_root)).check(matches(isDisplayed()))

            onView(withId(R.id.bookmarks_add_action)).perform(click())
            onView(withText("Page 2")).check(matches(isDisplayed()))

            val overflowDesc = context.getString(R.string.bookmark_item_overflow_content_description, 2)
            onView(withContentDescription(overflowDesc)).perform(click())
            onView(withText(R.string.bookmark_action_rename)).perform(click())

            onView(isAssignableFrom(EditText::class.java)).perform(replaceText("Chapter 2"))
            onView(withText(android.R.string.ok)).perform(click())
            onView(withText("Chapter 2")).check(matches(isDisplayed()))

            setPage(scenario, 0)
            assertTrue("Expected to be on page 0 before navigating via bookmark", waitForPage(scenario, 0))

            onView(allOf(withId(R.id.bookmark_item_root), hasDescendant(withText("Chapter 2")))).perform(click())
            assertTrue("Expected to navigate to page 1 from bookmark", waitForPage(scenario, 1))

            // Re-open and delete
            onView(withId(R.id.navigation_menu_button)).perform(click())
            onView(withId(R.id.navigation_menu_action_bookmarks)).perform(click())
            onView(withContentDescription(overflowDesc)).perform(click())
            onView(withText(R.string.bookmark_action_delete)).perform(click())
            onView(withId(R.id.bookmarks_empty)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun contentsEntry_opensSheetWithTocTabSelected() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        setPrefBool(context, SettingsActivity.PREF_SEEN_PAGE_INDICATOR_NAV_HINT, true)

        val pdf = copyAssetToFiles(context, "two_page_sample.pdf")
        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))

            scenario.onActivity { it.setTitle() }

            onView(withId(R.id.navigation_menu_button)).perform(click())
            onView(withId(R.id.navigation_menu_action_contents)).perform(click())
            onView(withId(R.id.bookmarks_toc_sheet_root)).check(matches(isDisplayed()))
            onView(withId(R.id.toc_tab_container)).check(matches(isDisplayed()))
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
