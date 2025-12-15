package org.opendroidpdf.app.search;

import androidx.annotation.NonNull;

import org.opendroidpdf.SearchResult;
import org.opendroidpdf.app.services.SearchService;

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
        SearchService searchService();
    }

    public void search(Host host, int direction) {
        if (host.docHasSearchResults() && host.getTextOfLastSearch().toString().contentEquals(host.latestQuery())) {
            host.goToNextSearchResult(direction);
            return;
        }
        SearchService service = host.searchService();
        if (service == null) return;
        service.start(host.latestQuery().toString(), direction, host.currentDisplayPage());
        host.setTextOfLastSearch(host.latestQuery());
    }
}
