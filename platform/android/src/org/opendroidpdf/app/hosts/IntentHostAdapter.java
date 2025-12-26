package org.opendroidpdf.app.hosts;

import android.content.Intent;
import android.net.Uri;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.navigation.DashboardDelegate;
import org.opendroidpdf.app.helpers.IntentRouter;
import org.opendroidpdf.app.document.ExportController;

/**
 * Host adapter to keep IntentRouter callbacks out of the activity file.
 */
public class IntentHostAdapter implements IntentRouter.Host {
    private final OpenDroidPDFActivity activity;
    private final ExportController exportController;

    public IntentHostAdapter(OpenDroidPDFActivity activity, ExportController exportController) {
        this.activity = activity;
        this.exportController = exportController;
    }

    @Override public boolean hasCore() { return activity.hasCore(); }
    @Override public void showDashboard() {
        DashboardDelegate dd = activity.getDashboardDelegate();
        if (dd != null) dd.showDashboardIfAvailable();
    }
    @Override public void openDocumentFromIntent(Intent intent) { activity.openDocumentFromIntent(intent); }
    @Override public void resetDocumentStateForIntent() { activity.resetDocumentStateForIntent(); }
    @Override public boolean ensureStoragePermission(Intent intent) { return activity.ensureStoragePermission(intent); }
    @Override public void importSidecarAnnotationsBundleFromUri(Uri uri, boolean forceImport) {
        if (exportController == null || uri == null) return;
        exportController.importSidecarAnnotationsBundleFromUri(uri, forceImport);
    }
}
