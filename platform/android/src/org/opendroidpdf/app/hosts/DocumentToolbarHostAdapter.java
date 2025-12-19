package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.document.DocumentToolbarController;
import org.opendroidpdf.app.navigation.DashboardDelegate;
import org.opendroidpdf.app.navigation.LinkBackHelper;

/** Adapter so DocumentToolbarController.Host doesn't bloat the activity. */
public final class DocumentToolbarHostAdapter implements DocumentToolbarController.Host {
    private final OpenDroidPDFActivity activity;
    private final LinkBackHelper linkBackHelper;
    private final ExportController exportController;

    public DocumentToolbarHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                      @NonNull ExportController exportController,
                                      @NonNull LinkBackHelper linkBackHelper) {
        this.activity = activity;
        this.exportController = exportController;
        this.linkBackHelper = linkBackHelper;
    }

    @Override public boolean hasDocumentLoaded() { return activity.hasDocumentLoaded(); }
    @Override public boolean hasDocumentView() { return activity.getDocView() != null; }
    @Override public boolean isViewingNoteDocument() { return activity.isCurrentNoteDocument(); }
    @Override public boolean isLinkBackAvailable() { return activity.isLinkBackAvailable(); }
    @NonNull @Override public androidx.appcompat.app.AppCompatActivity getActivity() { return activity; }
    @NonNull @Override public AlertDialog.Builder alertBuilder() { return activity.getAlertBuilder(); }
    @NonNull @Override public MuPDFReaderView getDocView() { return activity.getDocView(); }
    @Override public void requestAddBlankPage() {
        org.opendroidpdf.core.MuPdfRepository repo = activity.getRepository();
        org.opendroidpdf.MuPDFReaderView doc = activity.getDocView();
        if (repo == null || doc == null) return;
        if (repo.insertBlankPageAtEnd()) {
            int lastPage = Math.max(0, repo.getPageCount() - 1);
            doc.setDisplayedViewIndex(lastPage, true);
            doc.setScale(1.0f);
            doc.setNormalizedScroll(0.0f, 0.0f);
            activity.invalidateOptionsMenuSafely();
        }
    }
    @Override public void requestFullscreen() {
        new org.opendroidpdf.app.ui.FullscreenController()
                .enterFullscreen(new org.opendroidpdf.app.hosts.FullscreenHostAdapter(activity));
    }
    @Override public void requestSettings() {
        android.content.Intent intent = new android.content.Intent(activity, org.opendroidpdf.SettingsActivity.class);
        activity.startActivity(intent);
        activity.overridePendingTransition(org.opendroidpdf.R.animator.enter_from_left, org.opendroidpdf.R.animator.fade_out);
    }
    @Override public void requestPrint() {
        if (exportController != null) exportController.printDoc();
    }
    @Override public void requestShare() {
        if (exportController != null) exportController.shareDoc();
    }
    @Override public void requestSearchMode() { new org.opendroidpdf.SearchModeHostAdapter(activity).requestSearchMode(); }
    @Override public void requestDashboard() {
        DashboardDelegate dd = activity.getDashboardDelegate();
        if (dd != null) dd.showDashboardIfAvailable();
    }
    @Override public void requestDeleteNote() {
        org.opendroidpdf.app.notes.NotesController nc = activity.getNotesController();
        if (nc != null) nc.requestDeleteNote();
    }
    @Override public void requestSaveDialog() {
        if (exportController != null) exportController.saveDoc();
    }
    @Override public void requestLinkBackNavigation() {
        new LinkBackHostAdapter(activity, linkBackHelper).requestLinkBackNavigation();
    }
}
