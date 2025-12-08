package org.opendroidpdf.core

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import org.opendroidpdf.Annotation
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Coordinates annotation operations off the UI thread so legacy AsyncTasks
 * inside the reader views can be retired.
 */
class AnnotationController(private val controller: MuPdfController) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addMarkupAnnotationAsync(
        pageIndex: Int,
        quadPoints: Array<PointF>,
        type: Annotation.Type,
        callback: AnnotationCallback?
    ): AnnotationJob {
        val future = executor.submit {
            controller.addMarkupAnnotation(pageIndex, quadPoints, type)
            controller.markDocumentDirty()
            callback?.let { mainHandler.post { it.onComplete() } }
        }
        return AnnotationJob(future)
    }

    fun addTextAnnotationAsync(
        pageIndex: Int,
        quadPoints: Array<PointF>,
        contents: String?,
        callback: AnnotationCallback?
    ): AnnotationJob {
        val future = executor.submit {
            controller.addTextAnnotation(pageIndex, quadPoints, contents)
            controller.markDocumentDirty()
            callback?.let { mainHandler.post { it.onComplete() } }
        }
        return AnnotationJob(future)
    }

    fun addInkAnnotationAsync(
        pageIndex: Int,
        arcs: Array<Array<PointF>>,
        callback: AnnotationCallback?
    ): AnnotationJob {
        val future = executor.submit {
            controller.addInkAnnotation(pageIndex, arcs)
            controller.markDocumentDirty()
            callback?.let { mainHandler.post { it.onComplete() } }
        }
        return AnnotationJob(future)
    }

    fun deleteAnnotationAsync(
        pageIndex: Int,
        annotationIndex: Int,
        callback: AnnotationCallback?
    ): AnnotationJob {
        val future = executor.submit {
            controller.deleteAnnotation(pageIndex, annotationIndex)
            controller.markDocumentDirty()
            callback?.let { mainHandler.post { it.onComplete() } }
        }
        return AnnotationJob(future)
    }

    class AnnotationJob internal constructor(private val future: Future<*>) {
        fun cancel() {
            future.cancel(true)
        }

        fun await(timeout: Long, unit: TimeUnit): Boolean {
            return try {
                future.get(timeout, unit)
                true
            } catch (ignored: TimeoutException) {
                false
            } catch (ignored: Exception) {
                false
            }
        }

        fun isFinished(): Boolean = future.isDone || future.isCancelled
    }
}

fun interface AnnotationCallback {
    fun onComplete()
}
