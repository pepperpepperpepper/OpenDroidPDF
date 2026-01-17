package org.opendroidpdf.app.hosts;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.app.navigation.BackPressController;
import org.opendroidpdf.app.ui.KeyboardHostAdapter;
import org.opendroidpdf.app.ui.FullscreenController;
import org.opendroidpdf.app.ui.ActionBarMode;

import androidx.appcompat.app.AlertDialog;

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
    @Override public boolean hasDocumentView() { return activity.getDocView() != null && activity.isTaskRoot(); }
    @Override public void showDashboard() {
        org.opendroidpdf.app.navigation.DashboardDelegate dd = activity.getDashboardDelegate();
        if (dd != null) dd.showDashboardIfAvailable();
    }
    @Override public boolean dashboardIsShown() { return activity.dashboardIsShown() && activity.getDocView() != null; }
    @Override public void hideDashboard() { activity.hideDashboard(); }

    @Override public ActionBarMode getMode() { return activity.getActionBarMode(); }

    @Override public void hideKeyboard() { if (keyboardHostAdapter != null) keyboardHostAdapter.hideKeyboard(); }
    @Override public void clearSearchQuery() { if (activity.getSearchToolbarController() != null) activity.getSearchToolbarController().clearQuery(); }
    @Override public void clearSearchResults() { if (activity.getDocView()!=null) activity.getDocView().clearSearchResults(); }
    @Override public void resetupChildren() { if (activity.getDocView()!=null) activity.getDocView().resetupChildren(); }
    @Override public void setViewingMode() {
        final ActionBarMode mode = activity.getActionBarMode();
        try {
            MuPDFReaderView dv = activity.getDocView();
            if (dv != null) {
                MuPDFView v = (MuPDFView) dv.getSelectedView();
                if (v != null) {
                    if (mode == ActionBarMode.Annot && v.getDrawingSize() > 0) {
                        final MuPDFView view = v;
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.discard_ink_title)
                                .setMessage(R.string.discard_ink_message)
                                .setNegativeButton(R.string.menu_cancel, null)
                                .setPositiveButton(R.string.menu_discard, (d, w) -> {
                                    try { view.deselectText(); } catch (Throwable ignore) {}
                                    try { view.cancelDraw(); } catch (Throwable ignore) {}
                                    org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
                                    if (dvd != null) dvd.setViewingMode();
                                })
                                .show();
                        return;
                    }
                    if (mode == ActionBarMode.Annot) {
                        try { v.deselectText(); } catch (Throwable ignore) {}
                        try { v.cancelDraw(); } catch (Throwable ignore) {}
                    } else if (mode == ActionBarMode.Edit || mode == ActionBarMode.AddingTextAnnot) {
                        try { v.deselectAnnotation(); } catch (Throwable ignore) {}
                        try { v.deselectText(); } catch (Throwable ignore) {}
                    }
                }
            }
        } catch (Throwable ignored) {}

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
