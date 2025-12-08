package org.opendroidpdf.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.opendroidpdf.MuPDFAlert
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Listens for MuPDF alert callbacks off the UI thread and dispatches them to the host activity.
 */
class AlertController(private val repository: MuPdfRepository) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var listener: AlertListener? = null
    @Volatile private var active = false
    @Volatile private var currentFuture: Future<*>? = null

    fun start(listener: AlertListener) {
        this.listener = listener
        active = true
        queueNext()
    }

    fun stop() {
        active = false
        currentFuture?.cancel(true)
        currentFuture = null
        listener = null
    }

    fun shutdown() {
        stop()
        executor.shutdownNow()
    }

    fun reply(alert: MuPDFAlert) {
        if (!active) {
            return
        }
        repository.replyToAlert(alert)
        queueNext()
    }

    private fun queueNext() {
        val activeListener = listener ?: return
        if (!active) {
            return
        }
        currentFuture?.cancel(true)
        var future: Future<*>? = null
        future = executor.submit {
            try {
                val alert = repository.waitForAlert() ?: return@submit
                if (!active || future?.isCancelled == true) {
                    return@submit
                }
                mainHandler.post {
                    if (active) {
                        listener?.onAlert(alert)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to wait for MuPDF alert", t)
                if (active) {
                    mainHandler.post { queueNext() }
                }
            }
        }
        currentFuture = future
    }

    fun interface AlertListener {
        fun onAlert(alert: MuPDFAlert)
    }

    private companion object {
        private const val TAG = "AlertController"
    }
}
