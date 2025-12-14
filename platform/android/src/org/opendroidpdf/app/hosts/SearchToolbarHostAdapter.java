package org.opendroidpdf.app.hosts;

import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.SearchTaskManager;
import org.opendroidpdf.app.document.DocumentSetupController;
import org.opendroidpdf.app.document.DocumentViewDelegate;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.search.SearchStateDelegate;
import org.opendroidpdf.app.search.SearchActions;
import org.opendroidpdf.app.ui.OptionsMenuController;
import org.opendroidpdf.app.ui.ActionBarModeDelegate;
import org.opendroidpdf.app.ui.KeyboardHostAdapter;

/**
 * Adapter so SearchToolbarController.Host does not bloat the activity.
 */
public final class SearchToolbarHostAdapter implements SearchToolbarController.Host {
    private final Context context;
    private final ComponentName searchComponent;
    private final DocumentViewDelegate docDelegate;
    private final SearchStateDelegate searchStateDelegate;
    private final KeyboardHostAdapter keyboardHostAdapter;
    private OptionsMenuController optionsMenuController;
    private final ActionBarModeDelegate actionBarModeDelegate;
    private final DocumentSetupController documentSetupController;
    private final SearchActions searchActions = new SearchActions();

    public SearchToolbarHostAdapter(@NonNull Context context,
                                    @NonNull ComponentName searchComponent,
                                    @NonNull DocumentViewDelegate docDelegate,
                                    @NonNull SearchStateDelegate searchStateDelegate,
                                    @NonNull KeyboardHostAdapter keyboardHostAdapter,
                                    @Nullable OptionsMenuController optionsMenuController,
                                    @NonNull ActionBarModeDelegate actionBarModeDelegate,
                                    @NonNull DocumentSetupController documentSetupController) {
        this.context = context;
        this.searchComponent = searchComponent;
        this.docDelegate = docDelegate;
        this.searchStateDelegate = searchStateDelegate;
        this.keyboardHostAdapter = keyboardHostAdapter;
        this.optionsMenuController = optionsMenuController;
        this.actionBarModeDelegate = actionBarModeDelegate;
        this.documentSetupController = documentSetupController;
    }

    public void setOptionsMenuController(@NonNull OptionsMenuController controller) {
        this.optionsMenuController = controller;
    }

    @NonNull @Override public Context getContext() { return context; }
    @NonNull @Override public ComponentName getSearchComponent() { return searchComponent; }

    @NonNull @Override public CharSequence getLatestSearchQuery() { return searchStateDelegate.getLatestSearchQuery(); }
    @Override public void setLatestSearchQuery(@NonNull CharSequence query) { searchStateDelegate.setLatestSearchQuery(query); }
    @NonNull @Override public CharSequence getTextOfLastSearch() { return searchStateDelegate.getTextOfLastSearch(); }
    @Override public void setTextOfLastSearch(@NonNull CharSequence query) { searchStateDelegate.setTextOfLastSearch(query); }

    @Override public void hideKeyboard() { keyboardHostAdapter.hideKeyboard(); }
    @Override public void invalidateOptionsMenu() {
        if (optionsMenuController != null) optionsMenuController.invalidateOptionsMenuSafely();
    }
    @Override public boolean hasDocView() { return docDelegate.hasDocView(); }
    @Override public void requestDocViewFocus() { docDelegate.requestDocViewFocus(); }
    @Override public void clearSearchResults() { docDelegate.clearSearchResults(); }
    @Override public void resetupChildren() { docDelegate.resetupChildren(); }
    @Override public void setViewingMode() { docDelegate.setViewingMode(); }
    @Override public void exitSearchModeToMain() { actionBarModeDelegate.setMain(); }
    @Override public void stopSearchTaskIfRunning() {
        SearchTaskManager mgr = documentSetupController.getSearchTaskManager();
        if (mgr != null) mgr.stop();
    }

    @Override public void onSearchNavigate(int direction) {
        // Hide keyboard then advance search navigation using SearchActions
        keyboardHostAdapter.hideKeyboard();
        performSearch(direction);
    }

    @Override public void performSearch(int direction) {
        searchActions.search(new org.opendroidpdf.app.hosts.SearchHostAdapter(docDelegate, searchStateDelegate, documentSetupController), direction);
    }
}
