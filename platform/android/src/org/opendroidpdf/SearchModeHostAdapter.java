package org.opendroidpdf;

/**
 * Enters search mode by putting the doc view into {@link MuPDFReaderView.Mode#Searching}.
 * Action bar mode is derived from the reader mode via {@link DocViewFactory}'s setMode mapping.
 */
public final class SearchModeHostAdapter {
    private final OpenDroidPDFActivity activity;

    public SearchModeHostAdapter(OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    public void requestSearchMode() {
        if (activity == null) return;
        MuPDFReaderView v = activity.getDocView();
        if (v != null) {
            v.requestMode(MuPDFReaderView.Mode.Searching);
            return; // mode mapping invalidates options menu
        }
        activity.invalidateOptionsMenuSafely();
    }
}
