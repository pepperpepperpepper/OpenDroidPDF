package org.opendroidpdf.app.services.search;

import org.opendroidpdf.SearchResult;

/** Listener for search progress/results. */
public interface SearchListener {
    void onResult(SearchResult result);
    void onFirstResult(SearchResult result);
    void onComplete(boolean found);
    void onCancelled();
}
