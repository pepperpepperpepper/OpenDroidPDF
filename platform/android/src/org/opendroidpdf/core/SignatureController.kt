package org.opendroidpdf.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Keeps signature-related interactions on a small Kotlin surface so UI widgets
 * no longer touch [MuPdfController] directly.
 */
class SignatureController(private val controller: MuPdfController) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkFocusedSignature(): String? = controller.checkFocusedSignature()

    fun signFocusedSignature(keyFile: String, password: String?): Boolean =
        controller.signFocusedSignature(keyFile, password)

    fun checkFocusedSignatureAsync(callback: SignatureStringCallback): SignatureJob {
        val future = executor.submit {
            val result = controller.checkFocusedSignature()
            mainHandler.post { callback.onResult(result) }
        }
        return SignatureJob(future)
    }

    fun signFocusedSignatureAsync(
        keyFile: String,
        password: String?,
        callback: SignatureBooleanCallback
    ): SignatureJob {
        val future = executor.submit {
            val success = controller.signFocusedSignature(keyFile, password)
            mainHandler.post { callback.onResult(success) }
        }
        return SignatureJob(future)
    }

    class SignatureJob internal constructor(private val future: Future<*>) {
        fun cancel() {
            future.cancel(true)
        }

        fun isFinished(): Boolean = future.isDone || future.isCancelled
    }
}

fun interface SignatureStringCallback {
    fun onResult(result: String?)
}

fun interface SignatureBooleanCallback {
    fun onResult(success: Boolean)
}
