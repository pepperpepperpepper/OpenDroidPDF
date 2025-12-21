package org.opendroidpdf.app.document;

import android.content.Intent;

/**
 * Centralizes common intents for requesting durable document access.
 *
 * <p>Keep this small and declarative: callers choose request codes / transitions.</p>
 */
public final class DocumentAccessIntents {
    private DocumentAccessIntents() {}

    /**
     * Builds an {@link Intent#ACTION_OPEN_DOCUMENT} intent that requests read + write +
     * persistable permissions, suitable for re-opening a document when the app lacks save access.
     */
    public static Intent newOpenDocumentForEditIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "application/pdf",
                "application/epub+zip",
        });
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }
}

