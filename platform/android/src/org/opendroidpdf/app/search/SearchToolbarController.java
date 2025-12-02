package org.opendroidpdf.app.search;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuItemCompat;

import org.opendroidpdf.R;

/**
 * Encapsulates the search toolbar/menu wiring so OpenDroidPDFActivity doesn't need to own the
 * SearchView lifecycle directly. This keeps Search-specific bindings alongside the feature.
 */
public final class SearchToolbarController {

    public interface Host extends SearchView.OnQueryTextListener, SearchView.OnCloseListener {
        @NonNull Context getContext();

        @NonNull ComponentName getSearchComponent();

        @NonNull CharSequence getLatestSearchQuery();

        void onSearchNavigate(int direction);
    }

    private final Host host;
    private SearchView searchView;

    public SearchToolbarController(@NonNull Host host) {
        this.host = host;
    }

    public void inflateSearchMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.search_menu, menu);
        final MenuItem searchItem = menu.findItem(R.id.menu_search_box);
        if (searchItem == null) {
            return;
        }
        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        if (searchView == null) {
            return;
        }
        final SearchManager searchManager = (SearchManager) host.getContext().getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(host.getSearchComponent()));
        }
        searchView.setIconified(false);
        searchView.setOnCloseListener(host);
        searchView.setOnQueryTextListener(host);

        final CharSequence latest = host.getLatestSearchQuery();
        if (!TextUtils.isEmpty(latest)) {
            searchView.setQuery(latest, true);
        } else {
            searchView.setQuery("", false);
        }
    }

    public void clearQuery() {
        if (searchView != null) {
            searchView.setQuery("", false);
        }
    }

    public boolean handleMenuItem(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_next:
                return handleNavigation(1);
            case R.id.menu_previous:
                return handleNavigation(-1);
            default:
                return false;
        }
    }

    public void detach() {
        searchView = null;
    }

    private boolean handleNavigation(int direction) {
        final CharSequence latest = host.getLatestSearchQuery();
        if (TextUtils.isEmpty(latest)) {
            return false;
        }
        host.onSearchNavigate(direction);
        return true;
    }
}
