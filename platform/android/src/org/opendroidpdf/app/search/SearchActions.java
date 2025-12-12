package org.opendroidpdf.app.search;

import androidx.annotation.NonNull;

import org.opendroidpdf.SearchResult;
import org.opendroidpdf.SearchTaskManager;

/**
 * Encapsulates search navigation logic used by the activity and toolbar host.
 */
public class SearchActions {
    public interface Host {
        boolean docHasSearchResults();
        void goToNextSearchResult(int direction);
        int currentDisplayPage();
        @NonNull CharSequence latestQuery();
        void setTextOfLastSearch(@NonNull CharSequence query);
        @NonNull CharSequence getTextOfLastSearch();
        SearchTaskManager manager();
    }

    public void search(Host host, int direction) {
        if (host.docHasSearchResults() && host.getTextOfLastSearch().toString().contentEquals(host.latestQuery())) {
            host.goToNextSearchResult(direction);
            return;
        }
        SearchTaskManager mgr = host.manager();
        if (mgr == null) return;
        mgr.start(host.latestQuery().toString(), direction, host.currentDisplayPage());
        host.setTextOfLastSearch(host.latestQuery());
    }
}

