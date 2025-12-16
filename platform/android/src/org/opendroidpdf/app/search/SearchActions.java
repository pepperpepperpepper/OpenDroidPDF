package org.opendroidpdf.app.search;

import androidx.annotation.NonNull;

import org.opendroidpdf.SearchResult;
import org.opendroidpdf.app.services.search.SearchDirection;
import org.opendroidpdf.app.services.search.SearchRequest;
import org.opendroidpdf.app.services.search.SearchSession;

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
        SearchSession searchSession();
    }

    public void search(Host host, int direction) {
        if (host.docHasSearchResults() && host.getTextOfLastSearch().toString().contentEquals(host.latestQuery())) {
            host.goToNextSearchResult(direction);
            return;
        }
        SearchSession session = host.searchSession();
        if (session == null) return;
        SearchRequest req = new SearchRequest(
                host.latestQuery().toString(),
                SearchDirection.fromInt(direction),
                host.currentDisplayPage());
        session.start(req);
        host.setTextOfLastSearch(host.latestQuery());
    }
}
