package org.opendroidpdf.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Runs save/export tasks off the UI thread and marshals the result back to the main thread.
 */
class SaveController {

    private val executor: ExecutorService = EXECUTOR
    private val mainHandler = Handler(Looper.getMainLooper())

    fun run(task: Callable<Exception?>, callback: SaveCallback): SaveJob {
        val future = executor.submit {
            val error = try {
                task.call()
            } catch (t: Exception) {
                t
            }
            if (!Thread.currentThread().isInterrupted) {
                mainHandler.post { callback.onComplete(error) }
            }
        }
        return SaveJob(future)
    }

    class SaveJob internal constructor(private val future: Future<*>) {
        fun cancel() {
            future.cancel(true)
        }

        fun isFinished(): Boolean = future.isDone || future.isCancelled
    }
}

fun interface SaveCallback {
    fun onComplete(error: Exception?)
}

private val EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor()
