package org.opendroidpdf.app.navigation;

import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.OpenDroidPDFActivity;

import java.util.concurrent.Callable;

/**
 * Bridges OpenDroidPDFActivity to BackPressController.Host to keep the activity slim.
 */
public final class BackPressHostAdapter implements BackPressController.Host {
    private final OpenDroidPDFActivity activity;

    public BackPressHostAdapter(OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override public boolean isActionBarHidden() {
        return activity.getSupportActionBar() != null && !activity.getSupportActionBar().isShowing();
    }

    @Override public void exitFullScreen() { activity.exitFullScreen(); }
    @Override public boolean dashboardIsShown() { return activity.dashboardIsShown() && activity.getDocView() != null; }
    @Override public void hideDashboard() { activity.hideDashboard(); }

    @Override public BackPressController.Mode getMode() { return activity.getBackPressMode(); }
    @Override public void setMode(BackPressController.Mode mode) { activity.setBackPressMode(mode); activity.invalidateOptionsMenu(); }

    @Override public void hideKeyboard() { activity.hideKeyboard(); }
    @Override public void clearSearchQuery() { if (activity.getSearchToolbarController() != null) activity.getSearchToolbarController().clearQuery(); }
    @Override public void clearSearchResults() { if (activity.getDocView()!=null) activity.getDocView().clearSearchResults(); }
    @Override public void resetupChildren() { if (activity.getDocView()!=null) activity.getDocView().resetupChildren(); }
    @Override public void setViewingMode() { activity.setViewingMode(); }
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
    @Override public boolean canSaveToCurrentUri() { return activity.canSaveToCurrentUri(); }
    @Override public void saveInBackground(Callable<?> success, Callable<?> failure) { activity.saveInBackground(success, failure); }
    @Override public void showSaveAsActivity() { activity.showSaveAsActivity(); }
    @Override public void finish() { activity.setIgnoreSaveFlagsForFinish(); activity.finish(); }
    @Override public AlertDialog.Builder alertBuilder() { return activity.getAlertBuilder(); }
    @Override public String t(int resId) { return activity.getString(resId); }
}
