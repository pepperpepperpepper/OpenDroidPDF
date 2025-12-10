package org.opendroidpdf.core

import android.graphics.PointF
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import org.opendroidpdf.Annotation
import org.opendroidpdf.app.AppCoroutines
import java.util.concurrent.TimeUnit

/**
 * Coordinates annotation operations off the UI thread so legacy AsyncTasks
 * inside the reader views can be retired.
 */
class AnnotationController(private val controller: MuPdfController) {
    private val tag = "AnnotationController"

    fun addMarkupAnnotationAsync(
        pageIndex: Int,
        quadPoints: Array<PointF>,
        type: Annotation.Type,
        callback: AnnotationCallback?
    ): AnnotationJob {
        val job = AppCoroutines.launchIo {
            Log.d(tag, "addMarkupAnnotationAsync page=$pageIndex type=$type quads=${quadPoints.size}")
            controller.addMarkupAnnotation(pageIndex, quadPoints, type)
            controller.markDocumentDirty()
            callback?.let { AppCoroutines.launchMain { it.onComplete() } }
        }
        return AnnotationJob(job)
    }

    fun addTextAnnotationAsync(
        pageIndex: Int,
        quadPoints: Array<PointF>,
        contents: String?,
        callback: AnnotationCallback?
    ): AnnotationJob {
        val job = AppCoroutines.launchIo {
            Log.d(tag, "addTextAnnotationAsync page=$pageIndex quads=${quadPoints.size} hasText=${!contents.isNullOrEmpty()}")
            controller.addTextAnnotation(pageIndex, quadPoints, contents)
            controller.markDocumentDirty()
            callback?.let { AppCoroutines.launchMain { it.onComplete() } }
        }
        return AnnotationJob(job)
    }

    fun addInkAnnotationAsync(
        pageIndex: Int,
        arcs: Array<Array<PointF>>,
        callback: AnnotationCallback?
    ): AnnotationJob {
        val job = AppCoroutines.launchIo {
            Log.d(tag, "addInkAnnotationAsync page=$pageIndex arcs=${arcs.size}")
            controller.addInkAnnotation(pageIndex, arcs)
            controller.markDocumentDirty()
            callback?.let { AppCoroutines.launchMain { it.onComplete() } }
        }
        return AnnotationJob(job)
    }

    fun deleteAnnotationAsync(
        pageIndex: Int,
        annotationIndex: Int,
        callback: AnnotationCallback?
    ): AnnotationJob {
        val job = AppCoroutines.launchIo {
            Log.d(tag, "deleteAnnotationAsync page=$pageIndex index=$annotationIndex")
            controller.deleteAnnotation(pageIndex, annotationIndex)
            controller.markDocumentDirty()
            callback?.let { AppCoroutines.launchMain { it.onComplete() } }
        }
        return AnnotationJob(job)
    }

    class AnnotationJob internal constructor(private val job: Job) {
        fun cancel() { job.cancel() }

        suspend fun await(timeout: Long, unit: TimeUnit): Boolean {
            val ms = unit.toMillis(timeout)
            return withTimeoutOrNull(ms) {
                // Wait cooperatively until the job completes
                job.join()
                true
            } ?: false
        }

        fun isFinished(): Boolean = job.isCompleted || job.isCancelled
    }
}

fun interface AnnotationCallback {
    fun onComplete()
}
