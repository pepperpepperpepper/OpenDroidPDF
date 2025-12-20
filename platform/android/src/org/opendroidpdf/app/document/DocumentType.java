package org.opendroidpdf.app.document;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * High-level document type derived from MuPDF's reported file format string.
 *
 * This intentionally collapses many MuPDF-supported formats into {@link #OTHER}
 * until dedicated flows are implemented (EPUB is the first non-PDF target).
 */
public enum DocumentType {
    PDF,
    EPUB,
    OTHER;

    @NonNull
    public static DocumentType fromFileFormat(@Nullable String fileFormat) {
        if (fileFormat == null) return OTHER;
        String upper = fileFormat.trim().toUpperCase();
        if (upper.startsWith("PDF")) return PDF;
        if (upper.startsWith("EPUB")) return EPUB;
        return OTHER;
    }
}

