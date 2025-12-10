package org.opendroidpdf.core

import kotlinx.coroutines.Job
import org.opendroidpdf.app.AppCoroutines

/**
 * Keeps signature-related interactions on a small Kotlin surface so UI widgets
 * no longer touch [MuPdfController] directly.
 */
class SignatureController(private val controller: MuPdfController) {

    fun checkFocusedSignature(): String? = controller.checkFocusedSignature()

    fun signFocusedSignature(keyFile: String, password: String?): Boolean =
        controller.signFocusedSignature(keyFile, password)

    fun checkFocusedSignatureAsync(callback: SignatureStringCallback): SignatureJob {
        val job = AppCoroutines.launchIo {
            val result = controller.checkFocusedSignature()
            AppCoroutines.launchMain { callback.onResult(result) }
        }
        return SignatureJob(job)
    }

    fun signFocusedSignatureAsync(
        keyFile: String,
        password: String?,
        callback: SignatureBooleanCallback
    ): SignatureJob {
        val job = AppCoroutines.launchIo {
            val success = controller.signFocusedSignature(keyFile, password)
            AppCoroutines.launchMain { callback.onResult(success) }
        }
        return SignatureJob(job)
    }

    class SignatureJob internal constructor(private val job: Job) {
        fun cancel() { job.cancel() }
        fun isFinished(): Boolean = job.isCompleted || job.isCancelled
    }
}

fun interface SignatureStringCallback {
    fun onResult(result: String?)
}

fun interface SignatureBooleanCallback {
    fun onResult(success: Boolean)
}
