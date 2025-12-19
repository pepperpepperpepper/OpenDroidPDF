package org.opendroidpdf.app.hosts;

import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.document.DocumentViewDelegate;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.search.SearchActions;
import org.opendroidpdf.app.ui.OptionsMenuController;
import org.opendroidpdf.app.ui.KeyboardHostAdapter;
import org.opendroidpdf.app.services.SearchService;
import org.opendroidpdf.app.services.search.SearchSession;

/**
 * Adapter so SearchToolbarController.Host does not bloat the activity.
 */
public final class SearchToolbarHostAdapter implements SearchToolbarController.Host {
    private final Context context;
    private final ComponentName searchComponent;
    private final DocumentViewDelegate docDelegate;
    private final KeyboardHostAdapter keyboardHostAdapter;
    private OptionsMenuController optionsMenuController;
    private final SearchActions searchActions = new SearchActions();
    private final SearchService searchService;

    public SearchToolbarHostAdapter(@NonNull Context context,
                                    @NonNull ComponentName searchComponent,
                                    @NonNull DocumentViewDelegate docDelegate,
                                    @NonNull KeyboardHostAdapter keyboardHostAdapter,
                                    @Nullable OptionsMenuController optionsMenuController,
                                    @NonNull SearchService searchService) {
        this.context = context;
        this.searchComponent = searchComponent;
        this.docDelegate = docDelegate;
        this.keyboardHostAdapter = keyboardHostAdapter;
        this.optionsMenuController = optionsMenuController;
        this.searchService = searchService;
    }

    public void setOptionsMenuController(@NonNull OptionsMenuController controller) {
        this.optionsMenuController = controller;
    }

    @NonNull @Override public Context getContext() { return context; }
    @NonNull @Override public ComponentName getSearchComponent() { return searchComponent; }

    @NonNull @Override public CharSequence getLatestSearchQuery() { return searchService.session().latestQuery(); }
    @Override public void setLatestSearchQuery(@NonNull CharSequence query) { searchService.session().setLatestQuery(query); }
    @NonNull @Override public CharSequence getTextOfLastSearch() { return searchService.session().lastSubmittedQuery(); }
    @Override public void setTextOfLastSearch(@NonNull CharSequence query) { searchService.session().setLastSubmittedQuery(query); }

    @Override public void hideKeyboard() { keyboardHostAdapter.hideKeyboard(); }
    @Override public void invalidateOptionsMenu() {
        if (optionsMenuController != null) optionsMenuController.invalidateOptionsMenuSafely();
    }
    @Override public boolean hasDocView() { return docDelegate.hasDocView(); }
    @Override public void requestDocViewFocus() { docDelegate.requestDocViewFocus(); }
    @Override public void clearSearchResults() { docDelegate.clearSearchResults(); }
    @Override public void resetupChildren() { docDelegate.resetupChildren(); }
    @Override public void setViewingMode() { docDelegate.setViewingMode(); }
    @Override public void stopSearchTaskIfRunning() {
        SearchSession session = searchService.session();
        if (session.isActive()) session.stop();
    }

    @Override public void onSearchNavigate(int direction) {
        // Hide keyboard then advance search navigation using SearchActions
        keyboardHostAdapter.hideKeyboard();
        performSearch(direction);
    }

    @Override public void performSearch(int direction) {
        searchActions.search(new org.opendroidpdf.app.hosts.SearchHostAdapter(docDelegate, searchService.session()), direction);
    }
}
