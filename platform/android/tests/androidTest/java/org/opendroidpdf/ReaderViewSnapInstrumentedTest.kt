package org.opendroidpdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Ignore
import org.junit.runner.RunWith
import org.opendroidpdf.app.reader.ReaderGeometry
import org.opendroidpdf.app.reader.ZoomController
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@Ignore("Flaky integration path; keep skipped in full suite.")
class ReaderViewSnapInstrumentedTest {

    @Test
    fun snapToFitDebugHelperBringsWidthToContainer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val pdf = copyAssetToFiles(context, "two_page_sample.pdf")

        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(pdf), "application/pdf")
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            // Enable fit-width
            scenario.onActivity { act ->
                val sp = act.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS)
                sp.edit().putBoolean(SettingsActivity.PREF_FIT_WIDTH, true).apply()
            }

            // Wait for doc/child to be measured
            val waitDeadline = System.currentTimeMillis() + 6000
            var ready = false
            while (System.currentTimeMillis() < waitDeadline && !ready) {
                scenario.onActivity { act ->
                    val dv = act.getDocView()
                    val v = dv?.selectedView
                    ready = dv != null && v != null && v.measuredWidth > 0 && v.measuredHeight > 0 && dv.width > 0
                }
                if (!ready) Thread.sleep(50)
            }
            assertTrue("DocView or child view not ready", ready)

            // Compute, seed and trigger on UI thread
            val widths = IntArray(3)
            scenario.onActivity { act ->
                val dv = act.getDocView()
                val v = dv.selectedView
                val containerW = dv.width
                val containerH = dv.height
                widths[0] = v.width // before
                val fill = ReaderGeometry.fillScreenScale(
                    containerW, containerH,
                    dv.paddingLeft, dv.paddingRight, dv.paddingTop, dv.paddingBottom,
                    v.measuredWidth, v.measuredHeight
                )
                val fitWidthScale = containerW.toFloat() / (v.measuredWidth * fill)
                dv.setScale(maxOf(1.0f, fitWidthScale * 0.93f))
                dv.debugTriggerSnapToFitWidthIfEligible()
                widths[1] = containerW
            }

            instrumentation.waitForIdleSync()
            Thread.sleep(150)

            scenario.onActivity { act ->
                val dv = act.getDocView()
                widths[2] = dv.selectedView.width // after
            }

            val beforeWidth = widths[0]
            val containerW = widths[1]
            val afterWidth = widths[2]
            val closeToContainer = kotlin.math.abs(afterWidth - containerW) <= 4
            assertTrue(
                "Expected snap-to-fit to make page width ~ container (after=$afterWidth, container=$containerW, before=$beforeWidth)",
                closeToContainer && afterWidth >= beforeWidth
            )
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
