package org.opendroidpdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opendroidpdf.core.MuPdfController
import org.opendroidpdf.core.MuPdfRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.max

@RunWith(AndroidJUnit4::class)
class InkColorExportInstrumentedTest {

    private lateinit var context: Context
    private lateinit var pdfFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        pdfFile = File(context.filesDir, "ink_color_export_test.pdf")
        copyAsset("two_page_sample.pdf", pdfFile)
    }

    @After
    fun tearDown() {
        if (pdfFile.exists()) {
            pdfFile.delete()
        }
        context.filesDir.listFiles()?.filter { it.name.startsWith("export_ink_test") || it.name.startsWith("export_text_test") }?.forEach { it.delete() }
    }

    @Test
    fun inkColorsRenderAndSurviveExport() {
        val core = OpenDroidPDFCore(context, Uri.fromFile(pdfFile))
        val repository = MuPdfRepository(core)
        val controller = MuPdfController(repository)
        var bitmap: Bitmap? = null
        var exportedCore: OpenDroidPDFCore? = null
        try {
            val redStroke = arrayOf(
                arrayOf(
                    PointF(40f, 60f),
                    PointF(180f, 70f),
                    PointF(320f, 90f)
                )
            )
            repository.setInkColor(1f, 0f, 0f)
            controller.addInkAnnotation(0, redStroke)
            repository.refreshAnnotationAppearance(0)

            val blueStroke = arrayOf(
                arrayOf(
                    PointF(60f, 200f),
                    PointF(200f, 230f),
                    PointF(340f, 260f)
                )
            )
            repository.setInkColor(0f, 0f, 1f)
            controller.addInkAnnotation(0, blueStroke)
            repository.refreshAnnotationAppearance(0)

            bitmap = renderPage(repository, 320, 360)
            val counts = countColors(bitmap)
            assertTrue("Expected red pixels but saw only ${counts.red}", counts.red > 20)
            assertTrue("Expected blue pixels but saw only ${counts.blue}", counts.blue > 20)

            val exportFile = File(context.filesDir, "export_ink_test.pdf")
            if (exportFile.exists()) exportFile.delete()
            val saved = repository.saveCopy(exportFile.absolutePath)
            assertTrue("Export file was not created", saved && exportFile.exists() && exportFile.length() > 0)

            exportedCore = OpenDroidPDFCore(context, Uri.fromFile(exportFile))
            val reopenedRepo = MuPdfRepository(exportedCore)
            val annotations = reopenedRepo.loadAnnotations(0)
            assertNotNull("Annotations should be present after export", annotations)
            android.util.Log.i("InkColorExportTest", "Reopened annotations count=${annotations.size}")
            assertTrue("Expected at least two annotations in exported PDF but saw ${annotations.size}", annotations.size >= 2)
        } finally {
            bitmap?.recycle()
            exportedCore?.onDestroy()
            core.onDestroy()
        }
    }

    @Test
    fun freeTextAnnotationPersistsAcrossSave() {
        val core = OpenDroidPDFCore(context, Uri.fromFile(pdfFile))
        val repository = MuPdfRepository(core)
        val controller = MuPdfController(repository)
        var exportedCore: OpenDroidPDFCore? = null
        try {
            val quad = arrayOf(
                PointF(80f, 120f),
                PointF(220f, 120f),
                PointF(220f, 170f),
                PointF(80f, 170f)
            )
            val note = "Hello from androidTest"
            controller.addTextAnnotation(0, quad, note)
            repository.refreshAnnotationAppearance(0)

            val annots = repository.loadAnnotations(0)
            val hasNote = annots.any { it.type == Annotation.Type.FREETEXT && note == it.text }
            assertTrue("FREETEXT annotation should be readable immediately after creation", hasNote)

            val exportFile = File(context.filesDir, "export_text_test.pdf")
            if (exportFile.exists()) exportFile.delete()
            val saved = repository.saveCopy(exportFile.absolutePath)
            assertTrue("Export file was not created for text test", saved && exportFile.exists() && exportFile.length() > 0)

            exportedCore = OpenDroidPDFCore(context, Uri.fromFile(exportFile))
            val reopenedRepo = MuPdfRepository(exportedCore)
            val reopenedAnnots = reopenedRepo.loadAnnotations(0)
            val reopenedHasNote = reopenedAnnots.any { it.type == Annotation.Type.FREETEXT && note == it.text }
            android.util.Log.i("InkColorExportTest", "Reopened annotations count=${reopenedAnnots.size} hasNote=$reopenedHasNote")
            assertTrue("Exported PDF should still contain the FREETEXT annotation (count=${reopenedAnnots.size})", reopenedHasNote)
        } finally {
            exportedCore?.onDestroy()
            core.onDestroy()
        }
    }

    private fun renderPage(repo: MuPdfRepository, width: Int, height: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val cookie = repo.newRenderCookie()
        try {
            repo.drawPage(bmp, 0, width, height, 0, 0, width, height, cookie)
        } finally {
            cookie.destroy()
        }
        return bmp
    }

    private data class ColorCounts(val red: Int, val blue: Int)

    private fun countColors(bitmap: Bitmap): ColorCounts {
        var redCount = 0
        var blueCount = 0
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width)
        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val c = pixels[x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                if (r > 170 && r > g * 1.5 && r > b * 1.5) {
                    redCount++
                } else if (b > 170 && b > r * 1.5 && b > g * 1.5) {
                    blueCount++
                }
            }
        }
        return ColorCounts(red = redCount, blue = blueCount)
    }

    private fun copyAsset(assetName: String, dest: File) {
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        if (dest.exists()) dest.delete()
        try {
            instrumentationContext.assets.open(assetName).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            throw AssertionError("Unable to copy asset $assetName", e)
        }
    }
}
