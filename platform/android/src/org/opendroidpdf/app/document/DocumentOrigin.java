package org.opendroidpdf.app.document;

/**
 * Identifies how the current reader session was produced.
 *
 * <p>This is intentionally independent of {@link DocumentType}:
 * an imported document may still be rendered as a PDF after conversion.</p>
 */
public enum DocumentOrigin {
    /** Opened directly (PDF/EPUB/etc). */
    NATIVE,
    /** Imported from a Word document (.doc/.docx) by converting to PDF. */
    WORD,
}

