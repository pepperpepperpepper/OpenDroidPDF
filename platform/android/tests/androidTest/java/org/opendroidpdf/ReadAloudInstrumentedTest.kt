package org.opendroidpdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ReadAloudInstrumentedTest {

    @Test
    fun readAloud_overflowMenu_showsPlaybackBarOrMessage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        val pdf = copyAssetToFiles(context, "two_page_sample.pdf")
        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))

            Espresso.openActionBarOverflowOrOptionsMenu(context)
            onView(withText(R.string.menu_read_aloud)).perform(click())

            assertTrue(
                "Expected Read aloud UI (bar or toast) after menu click",
                waitForReadAloudUiOrToast(scenario)
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

    private fun waitForReadAloudUiOrToast(scenario: ActivityScenario<OpenDroidPDFActivity>): Boolean {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (readAloudBarVisible(scenario)) return true
            if (toastVisible(scenario, scenario, R.string.read_aloud_tts_unavailable)) return true
            if (toastVisible(scenario, scenario, R.string.read_aloud_no_text)) return true
            Thread.sleep(50)
        }
        return false
    }

    private fun readAloudBarVisible(scenario: ActivityScenario<OpenDroidPDFActivity>): Boolean {
        var visible = false
        scenario.onActivity { act ->
            val bar = act.findViewById<View>(R.id.reader_read_aloud_bar)
            visible = bar != null && bar.visibility == View.VISIBLE
        }
        return visible
    }

    private fun toastVisible(
        scenario: ActivityScenario<OpenDroidPDFActivity>,
        forDecorView: ActivityScenario<OpenDroidPDFActivity>,
        stringId: Int
    ): Boolean {
        var decor: View? = null
        forDecorView.onActivity { act -> decor = act.window?.decorView }
        val windowDecor = decor ?: return false
        return try {
            onView(withText(stringId))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.withDecorView(not(`is`(windowDecor))))
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            true
        } catch (_: Throwable) {
            false
        }
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

