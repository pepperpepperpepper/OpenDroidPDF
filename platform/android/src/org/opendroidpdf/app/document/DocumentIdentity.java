package org.opendroidpdf.app.document;

import androidx.annotation.NonNull;

/**
 * Document identity used for sidecar persistence and per-document state.
 *
 * <p>{@code docId} is the canonical identifier (prefer content-based). {@code legacyDocId} is
 * the historical identifier used by older app versions (currently {@code uri.toString()}).</p>
 */
public final class DocumentIdentity {
    @NonNull private final String docId;
    @NonNull private final String legacyDocId;

    public DocumentIdentity(@NonNull String docId, @NonNull String legacyDocId) {
        this.docId = docId;
        this.legacyDocId = legacyDocId;
    }

    @NonNull public String docId() { return docId; }
    @NonNull public String legacyDocId() { return legacyDocId; }
}

