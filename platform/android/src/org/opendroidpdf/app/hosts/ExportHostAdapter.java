package org.opendroidpdf.app.hosts;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.concurrent.Callable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.app.document.ExportController;

/**
 * Host adapter for ExportController; delegates to activity without keeping the code inline.
 */
public class ExportHostAdapter implements ExportController.Host {
    private final OpenDroidPDFActivity activity;

    public ExportHostAdapter(OpenDroidPDFActivity activity) { this.activity = activity; }

    @Override public MuPdfRepository getRepository() { return activity.getRepository(); }
    @Override public void showInfo(String message) { activity.showInfo(message); }
    @Override public String currentDocumentName() {
        MuPdfRepository repo = activity.getRepository();
        return repo != null ? repo.getDocumentName() : activity.getString(org.opendroidpdf.R.string.app_name);
    }
    @Override public void setLastExportedUri(Uri uri) { activity.setLastExportedUri(uri); }
    @Override public Uri getLastExportedUri() { return activity.getLastExportedUri(); }
    @Override public void markIgnoreSaveOnStop() { activity.markIgnoreSaveOnStop(); }
    @Override public Context getContext() { return activity; }
    @Override public android.content.ContentResolver getContentResolver() { return activity.getContentResolver(); }
    @Override public void callInBackgroundAndShowDialog(String message, Callable<Exception> background, Callable<Void> success, Callable<Void> failure) {
        activity.getSaveUiDelegate().callInBackgroundAndShowDialog(message, background, success, failure);
    }
    @Override public void commitPendingInkToCoreBlocking() { activity.commitPendingInkToCoreBlocking(); }
}
