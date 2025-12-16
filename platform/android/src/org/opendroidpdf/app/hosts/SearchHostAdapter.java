package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.document.DocumentViewDelegate;
import org.opendroidpdf.app.search.SearchActions;
import org.opendroidpdf.app.services.search.SearchSession;

public class SearchHostAdapter implements SearchActions.Host {
    private final DocumentViewDelegate docDelegate;
    private final SearchSession searchSession;

    public SearchHostAdapter(@NonNull DocumentViewDelegate docDelegate,
                             @NonNull SearchSession searchSession) {
        this.docDelegate = docDelegate;
        this.searchSession = searchSession;
    }

    @Override public boolean docHasSearchResults() { return docDelegate.docHasSearchResults(); }
    @Override public void goToNextSearchResult(int direction) { docDelegate.goToNextSearchResult(direction); }
    @Override public int currentDisplayPage() { return docDelegate.currentDisplayPage(); }
    @Override public @NonNull CharSequence latestQuery() { return searchSession.latestQuery(); }
    @Override public void setTextOfLastSearch(@NonNull CharSequence q) { searchSession.setLastSubmittedQuery(q); }
    @Override public @NonNull CharSequence getTextOfLastSearch() { return searchSession.lastSubmittedQuery(); }
    @Override public SearchSession searchSession() { return searchSession; }
}
