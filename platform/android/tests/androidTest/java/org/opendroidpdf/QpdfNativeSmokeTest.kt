package org.opendroidpdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.opendroidpdf.core.PdfOps
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class QpdfNativeSmokeTest {

    @Test
    fun qpdfLoadsAndReportsVersion() {
        Assume.assumeTrue("qpdf disabled", BuildConfig.ENABLE_QPDF_OPS)
        val version = PdfOps.qpdfVersion()
        assertNotNull("qpdf version should be available", version)
        assertFalse("qpdf version must not be blank", version!!.isBlank())
    }

    @Test
    fun mergeTwoAssetsAndValidatePageCount() {
        Assume.assumeTrue("qpdf disabled", BuildConfig.ENABLE_QPDF_OPS)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cache = instrumentation.targetContext.cacheDir.also { it.mkdirs() }

        val first = copyAsset("two_page_sample.pdf", cache)
        val second = copyAsset("cjk_sample.pdf", cache)
        val merged = File(cache, "merged_qpdf_test.pdf")

        val mergedOk = PdfOps.mergePdfs(first, second, merged)
        assertTrue("qpdf merge should succeed", mergedOk)
        assertTrue("merged file should exist", merged.exists())
        assertTrue("merged file should have content", merged.length() > 0)

        val countFirst = pageCount(first)
        val countSecond = pageCount(second)
        val mergedCount = pageCount(merged)
        assertTrue(
            "merged page count should equal sum of inputs",
            mergedCount == countFirst + countSecond
        )
    }

    @Test
    fun extractFirstPageOnly() {
        Assume.assumeTrue("qpdf disabled", BuildConfig.ENABLE_QPDF_OPS)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cache = instrumentation.targetContext.cacheDir.also { it.mkdirs() }

        val src = copyAsset("two_page_sample.pdf", cache)
        val subset = File(cache, "subset_qpdf_test.pdf")

        val ok = PdfOps.extractPages(src, "1", subset)
        assertTrue("extract should succeed", ok)
        assertTrue(subset.exists())
        assertTrue(subset.length() > 0)
        assertTrue("subset should have 1 page", pageCount(subset) == 1)
    }

    @Test
    fun rotateDoesNotChangePageCount() {
        Assume.assumeTrue("qpdf disabled", BuildConfig.ENABLE_QPDF_OPS)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cache = instrumentation.targetContext.cacheDir.also { it.mkdirs() }

        val src = copyAsset("two_page_sample.pdf", cache)
        val rotated = File(cache, "rotated_qpdf_test.pdf")

        val ok = PdfOps.rotatePages(src, "+90:1", rotated)
        assertTrue("rotate should succeed", ok)
        assertTrue(rotated.exists())
        assertTrue(rotated.length() > 0)
        assertTrue("rotate should preserve page count", pageCount(rotated) == pageCount(src))
    }

    @Test
    fun linearizeKeepsContent() {
        Assume.assumeTrue("qpdf disabled", BuildConfig.ENABLE_QPDF_OPS)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cache = instrumentation.targetContext.cacheDir.also { it.mkdirs() }

        val src = copyAsset("two_page_sample.pdf", cache)
        val linearized = File(cache, "linearized_qpdf_test.pdf")

        val ok = PdfOps.linearizePdf(src, linearized)
        assertTrue("linearize should succeed", ok)
        assertTrue(linearized.exists())
        assertTrue(linearized.length() > 0)
        assertTrue(
            "linearized page count should match source",
            pageCount(linearized) == pageCount(src)
        )
    }

    @Test
    fun encryptThenDecryptRestoresDoc() {
        Assume.assumeTrue("qpdf disabled", BuildConfig.ENABLE_QPDF_OPS)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cache = instrumentation.targetContext.cacheDir.also { it.mkdirs() }

        val src = copyAsset("two_page_sample.pdf", cache)
        val encrypted = File(cache, "encrypted_qpdf_test.pdf")
        val decrypted = File(cache, "decrypted_qpdf_test.pdf")

        val userPw = "userpw"
        val ownerPw = "ownerpw"

        val encryptedOk = PdfOps.encryptPdf(src, userPw, ownerPw, encrypted)
        assertTrue("encrypt should succeed", encryptedOk)
        assertTrue(encrypted.exists())
        assertTrue(encrypted.length() > 0)

        val decryptedOk = PdfOps.decryptPdf(encrypted, userPw, decrypted)
        assertTrue("decrypt should succeed", decryptedOk)
        assertTrue(decrypted.exists())
        assertTrue(decrypted.length() > 0)
        assertTrue(
            "decrypted page count should match source",
            pageCount(decrypted) == pageCount(src)
        )
    }

    private fun pageCount(file: File): Int {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(pfd).use { renderer ->
            return renderer.pageCount
        }
    }

    private fun copyAsset(name: String, cache: File): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testCtx = instrumentation.context
        val dest = File(cache, name)
        testCtx.assets.open(name).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        return dest
    }
}
