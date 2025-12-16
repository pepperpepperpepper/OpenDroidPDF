package org.opendroidpdf.app.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.services.search.SearchRequest;
import org.opendroidpdf.app.services.search.SearchSession;

/**
 * Document-search boundary. UI/toolbars talk only to SearchSession; the
 * underlying SearchTaskManager is encapsulated inside the service impl.
 */
public interface SearchService {
    /**
     * Bind a document to the search service. Passing a new docId replaces any
     * existing session and cancels in-flight searches.
     */
    void bindDocument(@NonNull String docId,
                      @NonNull org.opendroidpdf.core.SearchController searchController,
                      @NonNull org.opendroidpdf.MuPDFReaderView docView);

    /** Clear the bound document, cancelling active searches. */
    void clearDocument();

    /** Acquire the active session for the currently bound document (never null). */
    @NonNull SearchSession session();
}
