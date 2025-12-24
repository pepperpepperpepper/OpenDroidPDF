package org.opendroidpdf.app.hosts;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.navigation.BackPressController;
import org.opendroidpdf.app.ui.KeyboardHostAdapter;
import org.opendroidpdf.app.ui.FullscreenController;
import org.opendroidpdf.app.ui.ActionBarMode;

/**
 * Bridges OpenDroidPDFActivity to BackPressController.Host to keep the activity slim.
 */
public final class BackPressHostAdapter implements BackPressController.Host {
    private final OpenDroidPDFActivity activity;
    private final FullscreenController fullscreenController = new FullscreenController();
    private final KeyboardHostAdapter keyboardHostAdapter;

    public BackPressHostAdapter(OpenDroidPDFActivity activity, KeyboardHostAdapter keyboardHostAdapter) {
        this.activity = activity;
        this.keyboardHostAdapter = keyboardHostAdapter;
    }

    @Override public boolean isActionBarHidden() {
        return activity.getSupportActionBar() != null && !activity.getSupportActionBar().isShowing();
    }

    @Override public void exitFullScreen() {
        fullscreenController.exitFullscreen(new FullscreenHostAdapter(activity));
    }
    @Override public boolean dashboardIsShown() { return activity.dashboardIsShown() && activity.getDocView() != null; }
    @Override public void hideDashboard() { activity.hideDashboard(); }

    @Override public ActionBarMode getMode() { return activity.getActionBarMode(); }

    @Override public void hideKeyboard() { if (keyboardHostAdapter != null) keyboardHostAdapter.hideKeyboard(); }
    @Override public void clearSearchQuery() { if (activity.getSearchToolbarController() != null) activity.getSearchToolbarController().clearQuery(); }
    @Override public void clearSearchResults() { if (activity.getDocView()!=null) activity.getDocView().clearSearchResults(); }
    @Override public void resetupChildren() { if (activity.getDocView()!=null) activity.getDocView().resetupChildren(); }
    @Override public void setViewingMode() {
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd != null) dvd.setViewingMode();
    }
    @Override public void deselectTextOnCurrentPage() {
        try {
            MuPDFReaderView dv = activity.getDocView();
            if (dv != null) {
                MuPDFView v = (MuPDFView) dv.getSelectedView();
                if (v != null) v.deselectText();
            }
        } catch (Throwable ignored) {}
    }

    @Override public boolean hasUnsavedChanges() { return activity.hasUnsavedChanges(); }
    @Override public void promptToSaveForExit() {
        if (activity.getSaveUiDelegate() != null) {
            activity.getSaveUiDelegate().promptToSaveIfDirty(this::finish, this::finish);
        } else {
            finish();
        }
    }
    @Override public void finish() { activity.setIgnoreSaveFlagsForFinish(); activity.finish(); }
}
