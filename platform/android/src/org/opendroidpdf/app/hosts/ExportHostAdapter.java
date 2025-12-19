package org.opendroidpdf.app.hosts;

import android.content.Context;
import android.content.Intent;

import java.util.concurrent.Callable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.app.document.ExportController;

/**
 * Host adapter for ExportController; delegates to activity without keeping the code inline.
 */
public class ExportHostAdapter implements ExportController.Host {
    private final OpenDroidPDFActivity activity;
    private final org.opendroidpdf.app.lifecycle.SaveFlagController saveFlags;
    private final org.opendroidpdf.app.document.SaveUiDelegate saveUi;

    public ExportHostAdapter(OpenDroidPDFActivity activity,
                             org.opendroidpdf.app.lifecycle.SaveFlagController saveFlags,
                             org.opendroidpdf.app.document.SaveUiDelegate saveUi) {
        this.activity = activity;
        this.saveFlags = saveFlags;
        this.saveUi = saveUi;
    }

    @Override public MuPdfRepository getRepository() { return activity.getRepository(); }
    @Override public void showInfo(String message) { activity.showInfo(message); }
    @Override public String currentDocumentName() {
        return activity.currentDocumentNameOrAppName();
    }
    @Override public void markIgnoreSaveOnStop() {
        if (saveFlags != null) saveFlags.markIgnoreSaveOnStop();
    }
    @Override public Context getContext() { return activity; }
    @Override public android.content.ContentResolver getContentResolver() { return activity.getContentResolver(); }
    @Override public void callInBackgroundAndShowDialog(String message, Callable<Exception> background, Callable<Void> success, Callable<Void> failure) {
        if (saveUi != null) saveUi.callInBackgroundAndShowDialog(message, background, success, failure);
    }
    @Override public void commitPendingInkToCoreBlocking() { activity.commitPendingInkToCoreBlocking(); }
    @Override public void promptSaveAs() {
        org.opendroidpdf.app.document.DocumentNavigationController nav = activity.getDocumentNavigationController();
        if (nav != null) nav.promptSaveOrSaveAs();
    }
}
