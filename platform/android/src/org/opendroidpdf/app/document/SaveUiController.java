package org.opendroidpdf.app.document;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.R;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.core.SaveCallback;
import org.opendroidpdf.core.SaveController;

import java.util.concurrent.Callable;

/**
 * Owns background save/save-as execution and the simple progress dialog.
 * Activity provides host hooks for ink commit and viewport/recents updates.
 */
public final class SaveUiController {

    public interface Host {
        @NonNull MuPdfRepository getRepository();
        void commitPendingInkToCoreBlocking();
        void onSaveAsCompleted(@NonNull Uri newUri);
        void onSaveCompleted(@NonNull Uri uri);
        @NonNull AlertDialog.Builder alertBuilder();
        @NonNull Context getContext();
        boolean isFinishing();
        void showInfo(@NonNull String message);
        @NonNull String t(int resId);
        Uri currentDocumentUriOrNull();
        Uri lastExportedUriOrNull();
    }

    private final Host host;
    private final SaveController saveController = new SaveController();
    private SaveController.SaveJob activeSaveJob;

    public SaveUiController(@NonNull Host host) {
        this.host = host;
    }

    public void saveInBackground(final Callable<?> successCallable, final Callable<?> failureCallable) {
        callInBackgroundAndShowDialog(host.t(R.string.saving), new Callable<Exception>() {
            @Override public Exception call() {
                host.commitPendingInkToCoreBlocking();
                return saveInternal();
            }
        }, successCallable, failureCallable);
    }

    public void saveAsInBackground(@NonNull final Uri uri,
                                   final Callable<?> successCallable,
                                   final Callable<?> failureCallable) {
        callInBackgroundAndShowDialog(host.t(R.string.saving), new Callable<Exception>() {
            @Override public Exception call() {
                host.commitPendingInkToCoreBlocking();
                return saveAsInternal(uri);
            }
        }, successCallable, failureCallable);
    }

    public void callInBackgroundAndShowDialog(final String message,
                                              final Callable<Exception> background,
                                              final Callable<?> successCallable,
                                              final Callable<?> failureCallable) {
        final AlertDialog waitDialog = host.alertBuilder().create();
        waitDialog.setTitle(message);
        waitDialog.setCancelable(false);
        waitDialog.setCanceledOnTouchOutside(false);
        android.view.View progressView = android.view.LayoutInflater.from(host.getContext())
                .inflate(R.layout.dialog_progress, null, false);
        waitDialog.setView(progressView);
        if (!host.isFinishing()) waitDialog.show();

        cancelActiveSaveJob();
        activeSaveJob = saveController.run(background, new SaveCallback() {
            @Override public void onComplete(Exception error) {
                if (waitDialog.isShowing()) {
                    try { waitDialog.dismiss(); } catch (IllegalArgumentException ignore) {}
                }
                activeSaveJob = null;
                if (error == null) {
                    if (successCallable != null) callQuiet(successCallable);
                } else {
                    host.showInfo(host.t(R.string.error_saveing) + ": " + error);
                    if (failureCallable != null) callQuiet(failureCallable);
                }
            }
        });
    }

    public void cancelActiveSaveJob() {
        if (activeSaveJob != null) {
            activeSaveJob.cancel();
            activeSaveJob = null;
        }
    }

    private void callQuiet(Callable<?> c) {
        try { c.call(); } catch (Exception e) { host.showInfo(host.t(R.string.error_saveing) + ": " + e); }
    }

    private Exception saveInternal() {
        MuPdfRepository repo = host.getRepository();
        if (repo == null) return new Exception("repository is not ready");
        try {
            repo.saveDocument(host.getContext());
        } catch (Exception e) {
            return e;
        }
        Uri uri = repo.getDocumentUri();
        if (uri != null) host.onSaveCompleted(uri);
        return null;
    }

    private Exception saveAsInternal(@NonNull Uri uri) {
        MuPdfRepository repo = host.getRepository();
        if (repo == null) return new Exception("repository is not ready");
        try {
            repo.saveCopy(host.getContext(), uri);
        } catch (Exception e) {
            return e;
        }
        host.onSaveAsCompleted(uri);
        return null;
    }
}

