package org.opendroidpdf.core

import android.os.Handler
import android.os.Looper
import org.opendroidpdf.Annotation
import org.opendroidpdf.LinkInfo
import org.opendroidpdf.TextWord
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Handles document text/link/annotation loading off the UI thread so legacy AsyncTasks in
 * [org.opendroidpdf.PageView] can be retired.
 */
class DocumentContentController(private val controller: MuPdfController) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadTextAsync(
        pageIndex: Int,
        callback: DocumentTextCallback
    ): DocumentJob {
        val future = executor.submit {
            val text = controller.textLines(pageIndex)
            if (Thread.currentThread().isInterrupted) {
                return@submit
            }
            mainHandler.post { callback.onResult(text) }
        }
        return DocumentJob(future)
    }

    fun loadLinkInfoAsync(
        pageIndex: Int,
        callback: DocumentLinkCallback
    ): DocumentJob {
        val future = executor.submit {
            val links = controller.links(pageIndex)
            if (Thread.currentThread().isInterrupted) {
                return@submit
            }
            mainHandler.post { callback.onResult(links) }
        }
        return DocumentJob(future)
    }

    fun loadAnnotationsAsync(
        pageIndex: Int,
        callback: DocumentAnnotationCallback
    ): DocumentJob {
        val future = executor.submit {
            val annotations = controller.annotations(pageIndex)
            if (Thread.currentThread().isInterrupted) {
                return@submit
            }
            mainHandler.post { callback.onResult(annotations) }
        }
        return DocumentJob(future)
    }

    class DocumentJob internal constructor(private val future: Future<*>) {
        fun cancel() {
            future.cancel(true)
        }

        fun isFinished(): Boolean = future.isDone || future.isCancelled
    }
}

fun interface DocumentTextCallback {
    fun onResult(result: Array<Array<TextWord>>?)
}

fun interface DocumentLinkCallback {
    fun onResult(result: Array<LinkInfo>?)
}

fun interface DocumentAnnotationCallback {
    fun onResult(result: Array<Annotation>?)
}
