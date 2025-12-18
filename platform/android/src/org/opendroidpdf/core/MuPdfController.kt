package org.opendroidpdf.core

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import org.opendroidpdf.Annotation
import org.opendroidpdf.LinkInfo
import org.opendroidpdf.MuPDFCore
import org.opendroidpdf.PassClickResult
import org.opendroidpdf.TextWord

/**
 * Higher-level façade around [MuPdfRepository] that provides a stable surface for UI code.
 * Future refactors can wrap these calls with coroutines/flows without touching JNI bindings.
 */
class MuPdfController(private val repository: MuPdfRepository) {

    fun annotations(pageIndex: Int): Array<Annotation> =
        repository.loadAnnotations(pageIndex)

    fun widgetAreas(pageIndex: Int): Array<RectF> =
        repository.getWidgetAreas(pageIndex)

    fun textLines(pageIndex: Int): Array<Array<TextWord>>? =
        repository.extractTextLines(pageIndex)

    fun addMarkupAnnotation(pageIndex: Int, quadPoints: Array<PointF>, type: Annotation.Type) {
        repository.addMarkupAnnotation(pageIndex, quadPoints, type)
        repository.markDocumentDirty()
    }

    fun addTextAnnotation(pageIndex: Int, quadPoints: Array<PointF>, contents: String?) {
        repository.addTextAnnotation(pageIndex, quadPoints, contents)
        repository.markDocumentDirty()
    }

    fun deleteAnnotation(pageIndex: Int, annotationIndex: Int) {
        repository.deleteAnnotation(pageIndex, annotationIndex)
        repository.markDocumentDirty()
    }

    fun deleteAnnotationByObjectNumber(pageIndex: Int, objectNumber: Long) {
        repository.deleteAnnotationByObjectNumber(pageIndex, objectNumber)
        repository.markDocumentDirty()
    }

    fun addInkAnnotation(pageIndex: Int, arcs: Array<Array<PointF>>) {
        repository.addInkAnnotation(pageIndex, arcs)
        repository.markDocumentDirty()
    }

    fun markDocumentDirty() = repository.markDocumentDirty()

    fun setWidgetText(pageIndex: Int, text: String?): Boolean =
        repository.setWidgetText(pageIndex, text)

    fun setWidgetChoice(selection: Array<String>) {
        repository.setWidgetChoice(selection)
    }

    fun checkFocusedSignature(): String? = repository.checkFocusedSignature()

    fun signFocusedSignature(keyFile: String, password: String?): Boolean =
        repository.signFocusedSignature(keyFile, password)

    fun javascriptSupported(): Boolean = repository.javascriptSupported()

    fun refreshAnnotationAppearance(pageIndex: Int) {
        repository.refreshAnnotationAppearance(pageIndex)
    }

    fun pageCount(): Int = repository.getPageCount()

    fun pageSize(pageIndex: Int): PointF = repository.getPageSize(pageIndex)

    fun links(pageIndex: Int): Array<LinkInfo> = repository.getLinks(pageIndex)

    fun passClick(pageIndex: Int, x: Float, y: Float): PassClickResult =
        repository.passClick(pageIndex, x, y)

    fun newRenderCookie(): MuPDFCore.Cookie = repository.newRenderCookie()

    fun drawPage(
        bitmap: Bitmap,
        page: Int,
        pageWidth: Int,
        pageHeight: Int,
        patchX: Int,
        patchY: Int,
        patchWidth: Int,
        patchHeight: Int,
        cookie: MuPDFCore.Cookie
    ) {
        repository.drawPage(
            bitmap,
            page,
            pageWidth,
            pageHeight,
            patchX,
            patchY,
            patchWidth,
            patchHeight,
            cookie
        )
    }

    fun updatePage(
        bitmap: Bitmap,
        page: Int,
        pageWidth: Int,
        pageHeight: Int,
        patchX: Int,
        patchY: Int,
        patchWidth: Int,
        patchHeight: Int,
        cookie: MuPDFCore.Cookie
    ) {
        repository.updatePage(
            bitmap,
            page,
            pageWidth,
            pageHeight,
            patchX,
            patchY,
            patchWidth,
            patchHeight,
            cookie
        )
    }

    fun rawRepository(): MuPdfRepository = repository
}
