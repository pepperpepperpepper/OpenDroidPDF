package org.opendroidpdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.penandpdf.qpdf.QpdfNative
import org.junit.Assume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opendroidpdf.core.PdfOps
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class QpdfOpsRegressionTest {

    @Test
    fun goldenPipeline_hashesMatch() {
        Assume.assumeTrue("qpdf regression disabled", BuildConfig.ENABLE_QPDF_REGRESSION)
        Assume.assumeTrue("qpdf disabled", BuildConfig.ENABLE_QPDF_OPS)
        Assume.assumeTrue("qpdf JNI unavailable", PdfOps.qpdfVersion() != null)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cache = instrumentation.targetContext.cacheDir.also { it.mkdirs() }

        val mixed = copyAsset("mixed_orientation_3p.pdf", cache)
        val form = copyAsset("form_nav.pdf", cache)
        val encryptedFixture = copyAsset("password_user_testpw.pdf", cache)

        val merged = File(cache, "qpdf_reg_merged.pdf")
        assertTrue("merge should succeed", qpdfRun(arrayOf(
            "qpdf",
            "--deterministic-id",
            "--empty",
            "--pages",
            mixed.absolutePath, "1-z",
            form.absolutePath, "1-z",
            "--",
            merged.absolutePath
        )))
        assertTrue(merged.exists() && merged.length() > 0)

        val mergedQdf = File(cache, "qpdf_reg_merged.qdf.pdf")
        canonicalizeForHash(merged, mergedQdf)
        assertSha256Equals(EXPECTED_MERGED_QDF_SHA256, mergedQdf, "merged.qdf")

        val extracted = File(cache, "qpdf_reg_extracted.pdf")
        assertTrue("extract should succeed", qpdfRun(arrayOf(
            "qpdf",
            "--deterministic-id",
            merged.absolutePath,
            "--pages",
            merged.absolutePath,
            "2,4",
            "--",
            extracted.absolutePath
        )))
        assertTrue(extracted.exists() && extracted.length() > 0)

        val extractedQdf = File(cache, "qpdf_reg_extracted.qdf.pdf")
        canonicalizeForHash(extracted, extractedQdf)
        assertSha256Equals(EXPECTED_EXTRACTED_QDF_SHA256, extractedQdf, "extracted.qdf")

        val rotated = File(cache, "qpdf_reg_rotated.pdf")
        assertTrue("rotate should succeed", qpdfRun(arrayOf(
            "qpdf",
            "--deterministic-id",
            "--rotate=+90:1",
            extracted.absolutePath,
            "--",
            rotated.absolutePath
        )))
        assertTrue(rotated.exists() && rotated.length() > 0)

        val rotatedQdf = File(cache, "qpdf_reg_rotated.qdf.pdf")
        canonicalizeForHash(rotated, rotatedQdf)
        assertSha256Equals(EXPECTED_ROTATED_QDF_SHA256, rotatedQdf, "rotated.qdf")

        val linearized = File(cache, "qpdf_reg_linearized.pdf")
        assertTrue("linearize should succeed", qpdfRun(arrayOf(
            "qpdf",
            "--deterministic-id",
            "--linearize",
            rotated.absolutePath,
            "--",
            linearized.absolutePath
        )))
        assertTrue(linearized.exists() && linearized.length() > 0)

        val linearizedQdf = File(cache, "qpdf_reg_linearized.qdf.pdf")
        canonicalizeForHash(linearized, linearizedQdf)
        assertSha256Equals(EXPECTED_LINEARIZED_QDF_SHA256, linearizedQdf, "linearized.qdf")

        val encrypted = File(cache, "qpdf_reg_encrypted.pdf")
        assertTrue("encrypt should succeed", qpdfRun(arrayOf(
            "qpdf",
            "--encrypt",
            "userpw",
            "ownerpw",
            "256",
            "--",
            linearized.absolutePath,
            encrypted.absolutePath
        )))
        assertTrue(encrypted.exists() && encrypted.length() > 0)

        val decrypted = File(cache, "qpdf_reg_decrypted.pdf")
        assertTrue("decrypt should succeed", qpdfRun(arrayOf(
            "qpdf",
            "--deterministic-id",
            "--password=userpw",
            "--decrypt",
            encrypted.absolutePath,
            decrypted.absolutePath
        )))
        assertTrue(decrypted.exists() && decrypted.length() > 0)

        val decryptedQdf = File(cache, "qpdf_reg_decrypted.qdf.pdf")
        canonicalizeForHash(decrypted, decryptedQdf)
        assertSha256Equals(EXPECTED_DECRYPTED_QDF_SHA256, decryptedQdf, "decrypted.qdf")

        val fixtureDecrypted = File(cache, "qpdf_reg_fixture_decrypted.pdf")
        assertTrue("decrypt fixture should succeed", qpdfRun(arrayOf(
            "qpdf",
            "--deterministic-id",
            "--password=test",
            "--decrypt",
            encryptedFixture.absolutePath,
            fixtureDecrypted.absolutePath
        )))
        assertTrue(fixtureDecrypted.exists() && fixtureDecrypted.length() > 0)

        val fixtureDecryptedQdf = File(cache, "qpdf_reg_fixture_decrypted.qdf.pdf")
        canonicalizeForHash(fixtureDecrypted, fixtureDecryptedQdf)
        assertSha256Equals(EXPECTED_FIXTURE_DECRYPTED_QDF_SHA256, fixtureDecryptedQdf, "fixture_decrypted.qdf")
    }

    private fun canonicalizeForHash(input: File, output: File) {
        if (output.exists()) output.delete()
        val ok = qpdfRun(arrayOf(
            "qpdf",
            "--deterministic-id",
            "--qdf",
            "--object-streams=disable",
            "--normalize-content=y",
            input.absolutePath,
            output.absolutePath
        ))
        assertTrue("canonicalize should succeed for ${input.name}", ok)
        assertTrue("canonicalize should produce output for ${input.name}", output.exists() && output.length() > 0)
    }

    private fun qpdfRun(args: Array<String>): Boolean {
        return QpdfNative.run(args)
    }

    private fun assertSha256Equals(expected: String, file: File, label: String) {
        val actual = sha256Hex(file)
        assertEquals("$label sha256 mismatch (got $actual)", expected, actual)
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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

    private companion object {
        // Update these hashes when intentionally changing qpdf or fixture inputs.
        private const val EXPECTED_MERGED_QDF_SHA256 = "c6ff64eb6dc61ccdf815a792ee69733e3e5c80e8c43f17f7974518bb1937a6b1"
        private const val EXPECTED_EXTRACTED_QDF_SHA256 = "4473cfefaaa97c2eb95e3abd99d8e0d064e490cab1b211de8324d71232a6ec8c"
        private const val EXPECTED_ROTATED_QDF_SHA256 = "c9002c4ecc2bb5db38aa4de0f1a49d8e51d3c9575c90cdb4c05bb7ec4b005de7"
        private const val EXPECTED_LINEARIZED_QDF_SHA256 = "e21847b184ff4729c4b9300017bff8b5cd674c57eb3f5710c2947d6fd5a288a2"
        private const val EXPECTED_DECRYPTED_QDF_SHA256 = "ad5b94dc87417f41bcc9ff2d9653d1970df238c083ee9d1672d6096d313b071c"
        private const val EXPECTED_FIXTURE_DECRYPTED_QDF_SHA256 = "4c009e96184a4fcbec3ce50819f193108d0dd9139074b3c85b9b89d34103d2f4"
    }
}
