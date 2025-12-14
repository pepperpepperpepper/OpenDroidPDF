package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.SearchTaskManager;
import org.opendroidpdf.app.document.DocumentSetupController;
import org.opendroidpdf.app.document.DocumentViewDelegate;
import org.opendroidpdf.app.search.SearchStateDelegate;
import org.opendroidpdf.app.search.SearchActions;

public class SearchHostAdapter implements SearchActions.Host {
    private final DocumentViewDelegate docDelegate;
    private final SearchStateDelegate searchStateDelegate;
    private final DocumentSetupController documentSetupController;

    public SearchHostAdapter(@NonNull DocumentViewDelegate docDelegate,
                             @NonNull SearchStateDelegate searchStateDelegate,
                             @NonNull DocumentSetupController documentSetupController) {
        this.docDelegate = docDelegate;
        this.searchStateDelegate = searchStateDelegate;
        this.documentSetupController = documentSetupController;
    }

    @Override public boolean docHasSearchResults() { return docDelegate.docHasSearchResults(); }
    @Override public void goToNextSearchResult(int direction) { docDelegate.goToNextSearchResult(direction); }
    @Override public int currentDisplayPage() { return docDelegate.currentDisplayPage(); }
    @Override public @NonNull CharSequence latestQuery() { return searchStateDelegate.getLatestSearchQuery(); }
    @Override public void setTextOfLastSearch(@NonNull CharSequence q) { searchStateDelegate.setTextOfLastSearch(q); }
    @Override public @NonNull CharSequence getTextOfLastSearch() { return searchStateDelegate.getTextOfLastSearch(); }
    @Override public SearchTaskManager manager() { return documentSetupController.getSearchTaskManager(); }
}
