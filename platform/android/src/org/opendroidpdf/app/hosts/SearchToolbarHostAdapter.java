package org.opendroidpdf.app.hosts;

import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.search.SearchToolbarController;

/**
 * Adapter so SearchToolbarController.Host does not bloat the activity.
 */
public final class SearchToolbarHostAdapter implements SearchToolbarController.Host {
    private final OpenDroidPDFActivity activity;

    public SearchToolbarHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @NonNull @Override public Context getContext() { return activity; }
    @NonNull @Override public ComponentName getSearchComponent() { return activity.getComponentName(); }

    @NonNull @Override public CharSequence getLatestSearchQuery() { return activity.getLatestSearchQuery(); }
    @Override public void setLatestSearchQuery(@NonNull CharSequence query) { activity.setLatestSearchQuery(query); }
    @NonNull @Override public CharSequence getTextOfLastSearch() { return activity.getTextOfLastSearch(); }
    @Override public void setTextOfLastSearch(@NonNull CharSequence query) { activity.setTextOfLastSearch(query); }

    @Override public void hideKeyboard() { activity.hideKeyboard(); }
    @Override public void invalidateOptionsMenu() { activity.invalidateOptionsMenuSafely(); }
    @Override public boolean hasDocView() { return activity.getDocView() != null; }
    @Override public void requestDocViewFocus() { if (activity.getDocView() != null) activity.getDocView().requestFocus(); }
    @Override public void clearSearchResults() { if (activity.getDocView() != null) activity.getDocView().clearSearchResults(); }
    @Override public void resetupChildren() { if (activity.getDocView() != null) activity.getDocView().resetupChildren(); }
    @Override public void setViewingMode() { activity.setViewingMode(); }
    @Override public void exitSearchModeToMain() { activity.exitSearchModeToMain(); }
    @Override public void stopSearchTaskIfRunning() { if (activity.getSearchTaskManager() != null) activity.getSearchTaskManager().stop(); }

    @Override public void onSearchNavigate(int direction) { activity.onSearchNavigate(direction); }
    @Override public void performSearch(int direction) { activity.performSearch(direction); }
}
