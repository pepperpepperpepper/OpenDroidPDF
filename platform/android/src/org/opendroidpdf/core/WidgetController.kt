package org.opendroidpdf.core

import android.graphics.RectF

/**
 * Coordinates widget interactions so view classes do not call [MuPdfController]
 * directly for every form/text/signature update.
 */
class WidgetController(private val controller: MuPdfController) {

    fun widgetAreas(pageIndex: Int): Array<RectF> = controller.widgetAreas(pageIndex)

    fun setWidgetText(pageIndex: Int, contents: String?): Boolean =
        controller.setWidgetText(pageIndex, contents)

    fun setWidgetChoice(selection: Array<String>) {
        controller.setWidgetChoice(selection)
    }

    fun javascriptSupported(): Boolean = controller.javascriptSupported()
}
