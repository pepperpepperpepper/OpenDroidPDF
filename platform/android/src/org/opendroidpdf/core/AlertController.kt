package org.opendroidpdf.core

import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import org.opendroidpdf.MuPDFAlert
import org.opendroidpdf.app.AppCoroutines

/**
 * Listens for MuPDF alert callbacks off the UI thread and dispatches them to the host activity.
 */
class AlertController(private val repository: MuPdfRepository) {

    @Volatile private var listener: AlertListener? = null
    @Volatile private var active = false
    @Volatile private var currentJob: Job? = null

    fun start(listener: AlertListener) {
        this.listener = listener
        active = true
        queueNext()
    }

    fun stop() {
        active = false
        currentJob?.cancel()
        currentJob = null
        listener = null
    }

    fun shutdown() {
        stop()
    }

    fun reply(alert: MuPDFAlert) {
        if (!active) {
            return
        }
        repository.replyToAlert(alert)
        queueNext()
    }

    private fun queueNext() {
        if (!active) return
        val host = listener ?: return
        currentJob?.cancel()
        currentJob = AppCoroutines.launchIo {
            try {
                val alert = repository.waitForAlert()
                if (alert != null && active) {
                    AppCoroutines.launchMain {
                        if (active) host.onAlert(alert)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to wait for MuPDF alert", t)
                if (active) {
                    AppCoroutines.launchMain { queueNext() }
                }
            }
        }
    }

    fun interface AlertListener {
        fun onAlert(alert: MuPDFAlert)
    }

    private companion object {
        private const val TAG = "AlertController"
    }
}
