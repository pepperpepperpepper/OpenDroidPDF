package org.opendroidpdf.core

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import org.opendroidpdf.PassClickResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Coordinates widget interactions so view classes do not call [MuPdfController]
 * directly for every form/text/signature update.
 */
class WidgetController(private val controller: MuPdfController) {

    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun widgetAreas(pageIndex: Int): Array<RectF> = controller.widgetAreas(pageIndex)

    fun loadWidgetAreasAsync(pageIndex: Int, callback: WidgetAreasCallback): WidgetJob {
        val future = executor.submit {
            val areas = controller.widgetAreas(pageIndex)
            mainHandler.post { callback.onResult(areas) }
        }
        return WidgetJob(future)
    }

    fun setWidgetText(pageIndex: Int, contents: String?): Boolean =
        controller.setWidgetText(pageIndex, contents)

    fun setWidgetTextAsync(pageIndex: Int, contents: String?, callback: WidgetBooleanCallback): WidgetJob {
        val future = executor.submit {
            val result = controller.setWidgetText(pageIndex, contents)
            mainHandler.post { callback.onResult(result) }
        }
        return WidgetJob(future)
    }

    fun setWidgetChoice(selection: Array<String>) {
        controller.setWidgetChoice(selection)
    }

    fun setWidgetChoiceAsync(selection: Array<String>, callback: WidgetCompletionCallback): WidgetJob {
        val future = executor.submit {
            controller.setWidgetChoice(selection)
            mainHandler.post { callback.onComplete() }
        }
        return WidgetJob(future)
    }

    fun passClickAsync(
        pageIndex: Int,
        docRelX: Float,
        docRelY: Float,
        callback: WidgetPassClickCallback
    ): WidgetJob {
        val future = executor.submit {
            val result = controller.passClick(pageIndex, docRelX, docRelY)
            mainHandler.post { callback.onResult(result) }
        }
        return WidgetJob(future)
    }

    fun javascriptSupported(): Boolean = controller.javascriptSupported()

    class WidgetJob internal constructor(private val future: Future<*>) {
        fun cancel() {
            future.cancel(true)
        }

        fun isFinished(): Boolean = future.isDone || future.isCancelled
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
