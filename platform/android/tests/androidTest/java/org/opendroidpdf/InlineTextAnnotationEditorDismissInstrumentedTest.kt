package org.opendroidpdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class InlineTextAnnotationEditorDismissInstrumentedTest {

    @Test
    fun backPressDismissesInlineTextAnnotationEditor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        val pdf = copyAssetToFiles(context, "two_page_sample.pdf")
        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))
            showInlineEditor(scenario)
            assertTrue("Inline editor did not appear", waitForInlineEditorVisible(scenario, expectedVisible = true))

            pressBack()

            assertTrue("Inline editor did not dismiss on back", waitForInlineEditorVisible(scenario, expectedVisible = false))
            scenario.onActivity { act ->
                assertTrue("Activity should remain open after dismissing editor", !act.isFinishing)
                assertNotNull("DocView should still be present after dismissing editor", act.getDocView())
            }
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

    private fun showInlineEditor(scenario: ActivityScenario<OpenDroidPDFActivity>) {
        scenario.onActivity { act ->
            val pageView = act.getDocView()?.selectedView
            if (pageView is MuPDFPageView) {
                val annotation = Annotation(
                    80f,
                    120f,
                    260f,
                    170f,
                    Annotation.Type.FREETEXT,
                    null,
                    "Inline editor test",
                    -1L
                )
                pageView.showInlineTextAnnotationEditor(annotation)
            }
        }
    }

    private fun waitForInlineEditorVisible(
        scenario: ActivityScenario<OpenDroidPDFActivity>,
        expectedVisible: Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            var visible = false
            scenario.onActivity { act ->
                val v = act.findViewById<android.view.View?>(R.id.dialog_text_input)
                visible = v != null && v.isShown
            }
            if (visible == expectedVisible) return true
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

