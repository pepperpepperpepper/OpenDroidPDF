package org.opendroidpdf.core

import android.util.Log
import com.penandpdf.qpdf.QpdfNative
import org.opendroidpdf.BuildConfig
import java.io.File

/**
 * Central wiring for qpdf-backed structural operations. Controlled via
 * BuildConfig.ENABLE_QPDF_OPS so we can gate rollouts.
 */
object PdfOpsService {
    private const val TAG = "PdfOpsService"

    private val enabled: Boolean
        get() = BuildConfig.ENABLE_QPDF_OPS

    fun merge(inputA: File, inputB: File, output: File): Result<Unit> =
        runGuarded("merge") {
            if (!PdfOps.mergePdfs(inputA, inputB, output)) {
                error("qpdf merge returned false")
            }
        }

    fun extract(input: File, pageSpec: String, output: File): Result<Unit> =
        runGuarded("extract") {
            if (!PdfOps.extractPages(input, pageSpec, output)) {
                error("qpdf extract returned false")
            }
        }

    fun rotate(input: File, rotateExpr: String, output: File): Result<Unit> =
        runGuarded("rotate") {
            if (!PdfOps.rotatePages(input, rotateExpr, output)) {
                error("qpdf rotate returned false")
            }
        }

    fun linearize(input: File, output: File): Result<Unit> =
        runGuarded("linearize") {
            if (!PdfOps.linearizePdf(input, output)) {
                error("qpdf linearize returned false")
            }
        }

    fun encrypt(input: File, userPassword: String, ownerPassword: String, output: File): Result<Unit> =
        runGuarded("encrypt") {
            if (!PdfOps.encryptPdf(input, userPassword, ownerPassword, output)) {
                error("qpdf encrypt returned false")
            }
        }

    fun decrypt(input: File, password: String, output: File): Result<Unit> =
        runGuarded("decrypt") {
            if (!PdfOps.decryptPdf(input, password, output)) {
                error("qpdf decrypt returned false")
            }
        }

    private inline fun runGuarded(
        op: String,
        block: () -> Unit
    ): Result<Unit> {
        if (!enabled) {
            val msg = "qpdf ops disabled by flag"
            Log.i(TAG, "$op skipped: $msg")
            return Result.failure(IllegalStateException(msg))
        }
        return runCatching { block() }.onFailure {
            Log.e(TAG, "qpdf $op failed", it)
        }
    }
}
