package org.opendroidpdf.core

import kotlinx.coroutines.Job
import org.opendroidpdf.Annotation
import org.opendroidpdf.LinkInfo
import org.opendroidpdf.TextWord
import org.opendroidpdf.app.AppCoroutines

/**
 * Handles document text/link/annotation loading off the UI thread so legacy AsyncTasks in
 * [org.opendroidpdf.PageView] can be retired.
 */
class DocumentContentController(private val controller: MuPdfController) {

    fun loadTextAsync(
        pageIndex: Int,
        callback: DocumentTextCallback
    ): DocumentJob {
        val job = AppCoroutines.launchIo {
            val text = controller.textLines(pageIndex)
            AppCoroutines.launchMain { callback.onResult(text) }
        }
        return DocumentJob(job)
    }

    fun loadLinkInfoAsync(
        pageIndex: Int,
        callback: DocumentLinkCallback
    ): DocumentJob {
        val job = AppCoroutines.launchIo {
            val links = controller.links(pageIndex)
            AppCoroutines.launchMain { callback.onResult(links) }
        }
        return DocumentJob(job)
    }

    fun loadAnnotationsAsync(
        pageIndex: Int,
        callback: DocumentAnnotationCallback
    ): DocumentJob {
        val job = AppCoroutines.launchIo {
            val annotations = controller.annotations(pageIndex)
            AppCoroutines.launchMain { callback.onResult(annotations) }
        }
        return DocumentJob(job)
    }

    class DocumentJob internal constructor(private val job: Job) {
        fun cancel() { job.cancel() }
        fun isFinished(): Boolean = job.isCompleted || job.isCancelled
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
