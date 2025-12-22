package org.opendroidpdf.app.hosts;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.document.SaveUiController;
import org.opendroidpdf.core.MuPdfRepository;

/** Adapter for SaveUiController.Host that delegates to OpenDroidPDFActivity. */
public final class SaveUiHostAdapter implements SaveUiController.Host {
    private final OpenDroidPDFActivity activity;

    public SaveUiHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @NonNull @Override public MuPdfRepository getRepository() { return activity.getRepository(); }
    @Override public void commitPendingInkToCoreBlocking() { activity.commitPendingInkToCoreBlocking(); }
    @Override public void onSaveAsCompleted(@NonNull Uri newUri) {
        activity.getIntent().setData(newUri);
        activity.clearSaveToCurrentUriFailureOverride();
        activity.saveViewportAndRecentFiles(newUri);
        activity.tryToTakePersistablePermissions(activity.getIntent());
        if (activity.getComposition() != null && activity.getComposition().tempUriPermissionHostAdapter != null) {
            activity.getComposition().tempUriPermissionHostAdapter.remember(activity.getIntent());
        }
    }
    @Override public void onSaveCompleted(@NonNull Uri uri) { activity.saveViewportAndRecentFiles(uri); }
    @NonNull @Override public AlertDialog.Builder alertBuilder() { return activity.getAlertBuilder(); }
    @NonNull @Override public Context getContext() { return activity; }
    @Override public boolean isFinishing() { return activity.isFinishing(); }
    @Override public void showInfo(@NonNull String message) { activity.showInfo(message); }
    @NonNull @Override public String t(int resId) { return activity.getString(resId); }
    @Override public Uri currentDocumentUriOrNull() { return activity.currentDocumentUriOrNull(); }
    @Override public boolean hasUnsavedChanges() { return activity.hasUnsavedChanges(); }
    @Override public boolean canSaveToCurrentUri() { return activity.canSaveToCurrentUri(); }
    @Override public void showSaveAsActivity() { activity.showSaveAsActivity(); }

    @Override
    public void onSaveFailure(@NonNull Exception error, boolean attemptedSaveToCurrentUri) {
        if (!attemptedSaveToCurrentUri) return;
        if (activity.currentDocumentType() != DocumentType.PDF) return;
        if (!looksLikePermissionDenied(error)) return;

        activity.markSaveToCurrentUriFailureOverride();

        org.opendroidpdf.app.ui.UiStateDelegate ui = activity.getUiStateDelegate();
        Uri uri = activity.currentDocumentUriOrNull();
        if (ui != null && uri != null) {
            ui.showPdfReadOnlyBanner(
                    R.string.pdf_readonly_banner,
                    R.string.pdf_enable_saving,
                    () -> activity.showOpenDocumentForEditActivity());
        }
    }

    private static boolean looksLikePermissionDenied(@NonNull Exception error) {
        if (error instanceof SecurityException) return true;
        if (error instanceof java.io.FileNotFoundException) {
            // Many content providers throw FileNotFoundException when write access is denied.
            return true;
        }
        String msg = error.getMessage();
        if (msg == null) return false;
        String s = msg.toLowerCase(java.util.Locale.US);
        return s.contains("permission denied") || s.contains("eacces") || s.contains("eperm") || s.contains("read-only");
    }
}
