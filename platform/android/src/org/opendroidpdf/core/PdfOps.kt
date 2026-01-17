package org.opendroidpdf.core

import android.util.Log
import com.penandpdf.qpdf.QpdfNative
import java.io.File

/**
 * Kotlin-facing façade for qpdf operations. For now it only surfaces the
 * native version string to prove the JNI loader is wired; later we can mirror
 * the merge/split API shape here.
 */
object PdfOps {
    private const val TAG = "PdfOps"

    /**
    * Attempts to load qpdf and return its version. Returns null if the native
    * libraries are unavailable so callers can degrade gracefully.
    */
    fun qpdfVersion(): String? {
        return try {
            val version = QpdfNative.version()
            Log.i(TAG, "qpdf JNI loaded: $version")
            version
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load qpdf JNI", e)
            null
        }
    }

    /**
     * Concatenate two PDFs into [output]. Caller is responsible for providing
     * readable inputs and a writable parent directory.
     */
    fun mergePdfs(inputA: File, inputB: File, output: File): Boolean {
        return try {
            output.parentFile?.mkdirs()
            if (output.exists()) {
                output.delete()
            }
            val ok = QpdfNative.merge(
                inputA.absolutePath,
                inputB.absolutePath,
                output.absolutePath
            )
            if (!ok) {
                Log.w(TAG, "qpdf merge returned false for ${output.name}")
            }
            ok
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load qpdf JNI for merge", e)
            false
        }
    }

    /** Extract a page selection (e.g., "1-2,5") into [output]. */
    fun extractPages(input: File, pageSpec: String, output: File): Boolean {
        return runJob(
            arrayOf(
                "qpdf",
                input.absolutePath,
                "--pages",
                input.absolutePath,
                pageSpec,
                "--",
                output.absolutePath
            )
        )
    }

    /**
     * Assemble a new PDF by selecting pages from one or more input PDFs.
     *
     * [pageSelections] is a flat array of alternating `path, pageSpec` values, e.g.:
     * `arrayOf(srcPath, "1-2", otherPath, "1-z")`.
     */
    fun assemblePages(pageSelections: Array<String>, output: File): Boolean {
        if (pageSelections.isEmpty() || pageSelections.size % 2 != 0) {
            Log.w(TAG, "assemblePages: invalid selections (len=${pageSelections.size})")
            return false
        }
        val args = ArrayList<String>(4 + pageSelections.size + 2)
        args.add("qpdf")
        args.add("--empty")
        args.add("--pages")
        args.addAll(pageSelections.asList())
        args.add("--")
        args.add(output.absolutePath)
        return runJob(args.toTypedArray())
    }

    /** Rotate pages with qpdf rotate expression, e.g., "+90:1" or "+90:1,3-5". */
    fun rotatePages(input: File, rotateExpr: String, output: File): Boolean {
        return runJob(
            arrayOf(
                "qpdf",
                input.absolutePath,
                "--rotate=$rotateExpr",
                "--",
                output.absolutePath
            )
        )
    }

    /** Linearize (web-optimize) a PDF for fast web view. */
    fun linearizePdf(input: File, output: File): Boolean {
        return runJob(
            arrayOf(
                "qpdf",
                "--linearize",
                input.absolutePath,
                "--",
                output.absolutePath
            )
        )
    }

    /**
     * Encrypt a PDF with user/owner passwords. Uses 256-bit key (AES) by default.
     * The caller supplies passwords; permissions default to allow-all.
     */
    fun encryptPdf(
        input: File,
        userPassword: String,
        ownerPassword: String,
        output: File,
        keyLength: String = "256"
    ): Boolean {
        return runJob(
            arrayOf(
                "qpdf",
                "--encrypt",
                userPassword,
                ownerPassword,
                keyLength,
                "--",
                input.absolutePath,
                output.absolutePath
            )
        )
    }

    /**
     * Decrypt an encrypted PDF using the provided password.
     */
    fun decryptPdf(
        input: File,
        password: String,
        output: File
    ): Boolean {
        return runJob(
            arrayOf(
                "qpdf",
                "--password=$password",
                "--decrypt",
                input.absolutePath,
                output.absolutePath
            )
        )
    }

    private fun runJob(args: Array<String>): Boolean {
        return try {
            val ok = QpdfNative.run(args)
            if (!ok) {
                Log.w(TAG, "qpdf run failed for ${args.joinToString(" ")}")
            }
            ok
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load qpdf JNI for run", e)
            false
        }
    }
}
