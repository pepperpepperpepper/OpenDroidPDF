package org.opendroidpdf.app.document;

import android.content.Intent;

/**
 * Centralizes common intents for requesting durable document access.
 *
 * <p>Keep this small and declarative: callers choose request codes / transitions.</p>
 */
public final class DocumentAccessIntents {
    private DocumentAccessIntents() {}

    public static final String MIME_PDF = "application/pdf";
    public static final String MIME_EPUB = "application/epub+zip";
    public static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String MIME_DOC = "application/msword";
    public static final String MIME_JSON = "application/json";

    /**
     * Builds an {@link Intent#ACTION_OPEN_DOCUMENT} intent that requests read + write +
     * persistable permissions and filters to supported document types.
     */
    public static Intent newOpenDocumentIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                MIME_PDF,
                MIME_EPUB,
                MIME_DOCX,
                MIME_DOC,
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    /**
     * Builds an {@link Intent#ACTION_OPEN_DOCUMENT} intent that requests read + write +
     * persistable permissions, suitable for re-opening a document when the app lacks save access.
     */
    public static Intent newOpenDocumentForEditIntent() {
        return newOpenDocumentIntent();
    }

    /**
     * Builds an {@link Intent#ACTION_CREATE_DOCUMENT} intent for creating a new PDF destination
     * for "Save As" flows.
     */
    public static Intent newCreatePdfDocumentIntent(String suggestedTitleOrNull) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(MIME_PDF);
        if (suggestedTitleOrNull != null && !suggestedTitleOrNull.isEmpty()) {
            intent.putExtra(Intent.EXTRA_TITLE, suggestedTitleOrNull);
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    /**
     * Builds an {@link Intent#ACTION_OPEN_DOCUMENT} intent for selecting a sidecar annotations
     * bundle (JSON) to import into the currently open document.
     */
    public static Intent newOpenSidecarBundleIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Do not over-filter by MIME type: DocumentsProviders vary in how they classify *.json
        // files (some return text/plain). We validate the bundle format after selection.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }
}
