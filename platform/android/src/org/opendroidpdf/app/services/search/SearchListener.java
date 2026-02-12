package org.opendroidpdf.app.services.search;

import org.opendroidpdf.SearchResult;

/** Listener for search progress/results. */
public interface SearchListener {
    /** Called when a new search starts. */
    default void onStarted(int pageCount) {}

    /** Progress callback (1-based page index) while scanning pages. */
    default void onProgress(int pageIndex, int pageCount) {}

    void onResult(SearchResult result);
    void onFirstResult(SearchResult result);
    void onComplete(boolean found);
    void onCancelled();
}
