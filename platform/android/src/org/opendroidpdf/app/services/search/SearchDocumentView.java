package org.opendroidpdf.app.services.search;

import androidx.annotation.NonNull;

import org.opendroidpdf.SearchResult;

/**
 * Narrow document surface that SearchService needs in order to:
 *  - accumulate results, and
 *  - navigate to the first result.
 *
 * This avoids leaking MuPDFReaderView (UI) types across the SearchService boundary.
 */
public interface SearchDocumentView {
    void addSearchResult(@NonNull SearchResult result);
    void goToResult(@NonNull SearchResult result);
}

