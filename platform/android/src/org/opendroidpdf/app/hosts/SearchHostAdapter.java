package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.document.DocumentViewDelegate;
import org.opendroidpdf.app.search.SearchStateDelegate;
import org.opendroidpdf.app.search.SearchActions;
import org.opendroidpdf.app.services.SearchService;

public class SearchHostAdapter implements SearchActions.Host {
    private final DocumentViewDelegate docDelegate;
    private final SearchStateDelegate searchStateDelegate;
    private final SearchService searchService;

    public SearchHostAdapter(@NonNull DocumentViewDelegate docDelegate,
                             @NonNull SearchStateDelegate searchStateDelegate,
                             @NonNull SearchService searchService) {
        this.docDelegate = docDelegate;
        this.searchStateDelegate = searchStateDelegate;
        this.searchService = searchService;
    }

    @Override public boolean docHasSearchResults() { return docDelegate.docHasSearchResults(); }
    @Override public void goToNextSearchResult(int direction) { docDelegate.goToNextSearchResult(direction); }
    @Override public int currentDisplayPage() { return docDelegate.currentDisplayPage(); }
    @Override public @NonNull CharSequence latestQuery() { return searchStateDelegate.getLatestSearchQuery(); }
    @Override public void setTextOfLastSearch(@NonNull CharSequence q) { searchStateDelegate.setTextOfLastSearch(q); }
    @Override public @NonNull CharSequence getTextOfLastSearch() { return searchStateDelegate.getTextOfLastSearch(); }
    @Override public SearchService searchService() { return searchService; }
}
