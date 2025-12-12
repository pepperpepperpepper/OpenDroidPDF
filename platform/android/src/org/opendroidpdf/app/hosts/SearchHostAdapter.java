package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.SearchTaskManager;
import org.opendroidpdf.app.search.SearchActions;

public class SearchHostAdapter implements SearchActions.Host {
    private final OpenDroidPDFActivity activity;

    public SearchHostAdapter(OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override public boolean docHasSearchResults() {
        MuPDFReaderView v = activity.getDocView();
        return v != null && v.hasSearchResults();
    }
    @Override public void goToNextSearchResult(int direction) {
        MuPDFReaderView v = activity.getDocView();
        if (v != null) v.goToNextSearchResult(direction);
    }
    @Override public int currentDisplayPage() {
        MuPDFReaderView v = activity.getDocView();
        return v != null ? v.getSelectedItemPosition() : 0;
    }
    @Override public @NonNull CharSequence latestQuery() { return activity.getLatestSearchQuery(); }
    @Override public void setTextOfLastSearch(@NonNull CharSequence q) { activity.setTextOfLastSearch(q); }
    @Override public @NonNull CharSequence getTextOfLastSearch() { return activity.getTextOfLastSearch(); }
    @Override public SearchTaskManager manager() { return activity.getSearchTaskManager(); }
}
