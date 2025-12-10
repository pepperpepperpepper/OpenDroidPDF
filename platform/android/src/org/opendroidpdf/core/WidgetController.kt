package org.opendroidpdf.core

import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.opendroidpdf.PassClickResult
import org.opendroidpdf.app.AppCoroutines

/**
 * Coordinates widget interactions so view classes do not call [MuPdfController]
 * directly for every form/text/signature update.
 */
class WidgetController(private val controller: MuPdfController) {

    fun widgetAreas(pageIndex: Int): Array<RectF> = controller.widgetAreas(pageIndex)

    fun loadWidgetAreasAsync(pageIndex: Int, callback: WidgetAreasCallback): WidgetJob {
        val job = AppCoroutines.launchIo {
            val areas = controller.widgetAreas(pageIndex)
            AppCoroutines.launchMain { callback.onResult(areas) }
        }
        return WidgetJob(job)
    }

    fun setWidgetText(pageIndex: Int, contents: String?): Boolean =
        controller.setWidgetText(pageIndex, contents)

    fun setWidgetTextAsync(pageIndex: Int, contents: String?, callback: WidgetBooleanCallback): WidgetJob {
        val job = AppCoroutines.launchIo {
            val result = controller.setWidgetText(pageIndex, contents)
            AppCoroutines.launchMain { callback.onResult(result) }
        }
        return WidgetJob(job)
    }

    fun setWidgetChoice(selection: Array<String>) {
        controller.setWidgetChoice(selection)
    }

    fun setWidgetChoiceAsync(selection: Array<String>, callback: WidgetCompletionCallback): WidgetJob {
        val job = AppCoroutines.launchIo {
            controller.setWidgetChoice(selection)
            AppCoroutines.launchMain { callback.onComplete() }
        }
        return WidgetJob(job)
    }

    fun passClickAsync(
        pageIndex: Int,
        docRelX: Float,
        docRelY: Float,
        callback: WidgetPassClickCallback
    ): WidgetJob {
        val job = AppCoroutines.launchIo {
            val result = controller.passClick(pageIndex, docRelX, docRelY)
            AppCoroutines.launchMain { callback.onResult(result) }
        }
        return WidgetJob(job)
    }

    fun javascriptSupported(): Boolean = controller.javascriptSupported()

    class WidgetJob internal constructor(private val job: Job) {
        fun cancel() {
            job.cancel()
        }

        fun isFinished(): Boolean = job.isCompleted || job.isCancelled
    }
}

fun interface WidgetBooleanCallback {
    fun onResult(result: Boolean)
}

fun interface WidgetAreasCallback {
    fun onResult(areas: Array<RectF>)
}

fun interface WidgetCompletionCallback {
    fun onComplete()
}

fun interface WidgetPassClickCallback {
    fun onResult(result: PassClickResult)
}
