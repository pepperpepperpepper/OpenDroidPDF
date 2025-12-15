package org.opendroidpdf.app.document;

import android.content.SharedPreferences;
import android.content.Context;
import android.net.Uri;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.core.MuPdfController;

/**
 * Bridges DocumentSetupController.Host calls onto OpenDroidPDFActivity while
 * keeping that class lean.
 */
public final class DocumentSetupHostAdapter implements DocumentSetupController.Host {
    private final OpenDroidPDFActivity activity;
    private final org.opendroidpdf.app.hosts.FilePickerHostAdapter filePickerHost;

    public DocumentSetupHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                    @NonNull org.opendroidpdf.app.hosts.FilePickerHostAdapter filePickerHost) {
        this.activity = activity;
        this.filePickerHost = filePickerHost;
    }

    @Nullable @Override public OpenDroidPDFCore getCore() { return activity.getCore(); }
    @Override public void setCoreInstance(OpenDroidPDFCore core) { activity.setCoreInstance(core); }
    @Override public androidx.appcompat.app.AlertDialog.Builder alertBuilder() { return activity.getAlertBuilder(); }
    @Override public SharedPreferences getSharedPreferences(String name, int mode) { return activity.getSharedPreferences(name, mode); }
    @Override public void requestPassword() { activity.requestPassword(); }
    @Override public org.opendroidpdf.core.SearchController getSearchController() { return activity.getSearchController(); }
    @Override public MuPDFReaderView getDocView() { return activity.getDocView(); }
    @Override public void showInfo(String message) { activity.showInfo(message); }
    @Override public Context getContext() { return activity; }
    @Override public void setTitle() { activity.setTitle(); }
    @Override public org.opendroidpdf.app.hosts.FilePickerHostAdapter filePickerHost() { return filePickerHost; }
    @Override public int getActionBarHeightPx() {
        try {
            androidx.appcompat.app.ActionBar ab = activity.getSupportActionBar();
            if (ab != null && ab.isShowing()) {
                android.util.TypedValue tv = new android.util.TypedValue();
                if (activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
                    return android.util.TypedValue.complexToDimensionPixelSize(tv.data, activity.getResources().getDisplayMetrics());
                }
            }
        } catch (Throwable ignore) { }
        return 0;
    }
    @Override public void hideDashboard() {
        org.opendroidpdf.app.navigation.DashboardDelegate dd = activity.getDashboardDelegate();
        if (dd != null) dd.hideDashboard();
    }
    @Override public ViewGroup ensureDocumentContainer() {
        org.opendroidpdf.app.navigation.DashboardDelegate dd = activity.getDashboardDelegate();
        return dd != null ? dd.ensureDocumentContainer() : null;
    }
    @Override public void createDocViewIfNeeded() {
        if (activity.getCore() == null || activity.getDocView() != null) return;
        activity.setDocView(org.opendroidpdf.DocViewFactory.create(activity, filePickerHost));
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd != null) dvd.markDocViewNeedsNewAdapter();
    }
    @Override public void attachDocViewToContainer(ViewGroup container) {
        org.opendroidpdf.app.navigation.DashboardDelegate dd = activity.getDashboardDelegate();
        if (dd != null) dd.attachDocViewToContainer(container);
    }
    @Override public void onDocViewAttached() {
        MuPDFReaderView doc = activity.getDocView();
        if (doc == null) return;
        doc.setMode(doc.getMode());
        doc.clearSearchResults();
    }
    @Override public void ensureDocAdapter() {
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd == null) return;
        org.opendroidpdf.core.MuPdfRepository repo = activity.getRepository();
        org.opendroidpdf.core.MuPdfController controller = activity.getMuPdfController();
        dvd.ensureDocAdapter(activity.getCore(), repo, controller, dvd.docViewNeedsNewAdapter());
    }
    @Override public void restoreViewportIfAny() {
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd == null) return;
        dvd.restoreViewportIfAny(activity.getCurrentDocumentUri());
    }
    @Override public void restoreDocViewStateIfAny() {
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd != null) dvd.restoreDocViewStateIfAny();
    }
    @Override public void syncDocViewPreferences() {
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd != null) dvd.syncPreferences();
    }
}
