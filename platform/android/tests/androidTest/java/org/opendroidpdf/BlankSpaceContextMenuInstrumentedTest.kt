package org.opendroidpdf

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class BlankSpaceContextMenuInstrumentedTest {

    @Test
    fun blankLongPress_showsQuickToolsContextMenu_andSelectingDrawEntersDrawMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        setPrefBool(context, SettingsActivity.PREF_READING_MODE, false)
        setPrefBool(context, SettingsActivity.PREF_SEEN_PAGE_INDICATOR_NAV_HINT, true)

        val pdf = writeBlankPdfToFiles(context, "blank_context_menu.pdf")
        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))

            onView(isAssignableFrom(MuPDFReaderView::class.java)).perform(longClick())

            assertTrue(
                "Expected Draw entry to appear in blank-space context menu",
                waitForPopupText(R.string.menu_draw)
            )

            onView(withText(R.string.menu_draw))
                .inRoot(isPlatformPopup())
                .perform(click())

            assertTrue(
                "Expected annot bottom bar visible after selecting Draw from context menu",
                waitForViewVisibility(scenario, R.id.reader_annot_actions_bar, View.VISIBLE)
            )
            onView(withId(R.id.reader_annot_actions_bar)).check(matches(isDisplayed()))
        }
    }

    private fun waitForPopupText(textResId: Int): Boolean {
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withText(textResId)).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
                return true
            } catch (t: Throwable) {
                Thread.sleep(50)
            }
        }
        return false
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

    private fun writeBlankPdfToFiles(context: Context, name: String): File {
        val out = File(context.filesDir, name)
        if (out.exists()) out.delete()

        val doc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create()
            val page = doc.startPage(pageInfo)
            doc.finishPage(page)
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally {
            try { doc.close() } catch (t: Throwable) {}
        }
        return out
    }
}

