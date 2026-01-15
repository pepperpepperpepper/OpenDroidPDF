package org.opendroidpdf.pdfboxops

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import java.io.InputStream
import java.io.OutputStream

/**
 * Lightweight PDFBox-backed helpers kept out of the base APK.
 *
 * These functions assume the caller executes off the UI thread and manages
 * temp/input/output URIs. The module can be packaged as a dynamic feature or
 * optional AAR to keep the base app slim.
 */
object PdfBoxOps {
    @Volatile
    private var initialized = false

    private fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }
    }

    data class FlattenResult(
        val pageCount: Int,
        val hadAcroForm: Boolean
    )

    /**
     * Flatten AcroForm fields into page content. Saves to [output] to keep the
     * source immutable. Returns page count and whether a form was present.
     */
    @WorkerThread
    fun flattenForm(context: Context, input: Uri, output: Uri): FlattenResult {
        ensureInit(context)
        return context.contentResolver.openInputStream(input).use { inputStream ->
            requireNotNull(inputStream) { "Missing input stream for $input" }
            PDDocument.load(inputStream).use { doc ->
                val acro: PDAcroForm? = doc.documentCatalog.acroForm
                val hadForm = acro != null
                acro?.apply {
                    // Generate appearances before flattening to keep field styling.
                    setNeedAppearances(true)
                    refreshAppearances()
                    flatten()
                }
                context.contentResolver.openOutputStream(output, "w").use { out ->
                    requireNotNull(out) { "Missing output stream for $output" }
                    doc.save(out)
                    out.flush()
                }
                FlattenResult(doc.numberOfPages, hadForm)
            }
        }
    }

    data class MetadataRequest(
        val title: String? = null,
        val author: String? = null,
        val subject: String? = null,
        val keywords: List<String>? = null
    )

    /**
     * Copy a PDF while applying simple document metadata. Returns the page count.
     */
    @WorkerThread
    fun applyMetadata(
        context: Context,
        input: Uri,
        output: Uri,
        request: MetadataRequest
    ): Int {
        ensureInit(context)
        return context.contentResolver.openInputStream(input).use { inputStream ->
            requireNotNull(inputStream) { "Missing input stream for $input" }
            PDDocument.load(inputStream).use { doc ->
                val info: PDDocumentInformation = doc.documentInformation ?: PDDocumentInformation()
                request.title?.let { info.title = it }
                request.author?.let { info.author = it }
                request.subject?.let { info.subject = it }
                request.keywords?.takeIf { it.isNotEmpty() }?.let { info.keywords = it.joinToString(", ") }
                doc.documentInformation = info
                context.contentResolver.openOutputStream(output, "w").use { out ->
                    requireNotNull(out) { "Missing output stream for $output" }
                    doc.save(out)
                    out.flush()
                }
                doc.numberOfPages
            }
        }
    }
}
