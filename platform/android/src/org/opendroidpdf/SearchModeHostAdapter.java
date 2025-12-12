package org.opendroidpdf;

/**
 * Enters search mode: sets ActionBar mode and puts the doc view into Searching.
 * Kept in the base package to access MuPDFReaderView.Mode without extra wrappers.
 */
public final class SearchModeHostAdapter {
    private final OpenDroidPDFActivity activity;

    public SearchModeHostAdapter(OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    public void requestSearchMode() {
        if (activity == null) return;
        activity.setActionBarModeSearch();
        MuPDFReaderView v = activity.getDocView();
        if (v != null) {
            v.setMode(MuPDFReaderView.Mode.Searching);
        }
        activity.invalidateOptionsMenuSafely();
    }
}

