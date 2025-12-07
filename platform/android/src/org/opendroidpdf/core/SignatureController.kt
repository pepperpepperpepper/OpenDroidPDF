package org.opendroidpdf.core

/**
 * Keeps signature-related interactions on a small Kotlin surface so UI widgets
 * no longer touch [MuPdfController] directly.
 */
class SignatureController(private val controller: MuPdfController) {

    fun checkFocusedSignature(): String? = controller.checkFocusedSignature()

    fun signFocusedSignature(keyFile: String, password: String?): Boolean =
        controller.signFocusedSignature(keyFile, password)
}
