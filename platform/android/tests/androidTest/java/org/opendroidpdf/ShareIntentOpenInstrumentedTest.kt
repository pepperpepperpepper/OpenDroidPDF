package org.opendroidpdf

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ShareIntentOpenInstrumentedTest {

    @Test
    fun actionSend_withExtraStream_opensDocument() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        val pdf = copyAssetToCache(context, "two_page_sample.pdf")
        val uri = FileProvider.getUriForFile(context, "org.opendroidpdf.fileprovider", pdf)

        val intent = Intent(context, OpenDroidPDFActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        ActivityScenario.launch<OpenDroidPDFActivity>(intent).use { scenario ->
            assertTrue("DocView not ready", waitForDocReady(scenario))
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

    private fun copyAssetToCache(context: Context, asset: String): File {
        val outDir = File(context.cacheDir, "tmpfiles")
        if (!outDir.exists()) outDir.mkdirs()
        val out = File(outDir, asset)
        if (out.exists()) out.delete()
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }
}

