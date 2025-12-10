package org.opendroidpdf.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable
import org.opendroidpdf.app.AppCoroutines

/**
 * Runs save/export tasks off the UI thread and marshals the result back to the main thread.
 */
class SaveController {

    @JvmOverloads
    fun run(task: Callable<Exception?>, callback: SaveCallback, scope: CoroutineScope = AppCoroutines.ioScope()): SaveJob {
        val job = AppCoroutines.launchIo(scope) {
            val error = try {
                task.call()
            } catch (t: Exception) {
                t
            }
            AppCoroutines.launchMain { callback.onComplete(error) }
        }
        return SaveJob(job)
    }

    class SaveJob internal constructor(private val job: Job) {
        fun cancel() { job.cancel() }
        fun isFinished(): Boolean = job.isCompleted || job.isCancelled
    }
}

fun interface SaveCallback {
    fun onComplete(error: Exception?)
}

// Executor removed; SaveController now uses AppCoroutines scopes.
