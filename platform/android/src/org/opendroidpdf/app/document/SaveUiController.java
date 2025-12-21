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
        boolean hasUnsavedChanges();
        boolean canSaveToCurrentUri();
        void showSaveAsActivity();

        /**
         * Hook for save failure handling (e.g., permission revoked).
         *
         * <p>Called on the main thread after the background save task completes.</p>
         */
        default void onSaveFailure(@NonNull Exception error, boolean attemptedSaveToCurrentUri) {
            // no-op by default
        }
    }

    private final Host host;
    private final SaveController saveController = new SaveController();
    private SaveController.SaveJob activeSaveJob;
    private boolean lastSaveAttemptWasToCurrentUri;

    public SaveUiController(@NonNull Host host) {
        this.host = host;
    }

    /**
     * Prompt to save if dirty, then invoke the callable if the user chose to proceed.
     *
     * Note: if the document cannot be saved back to its current URI, this will open the Save-As
     * flow and will not automatically continue (the caller can re-trigger the action after save-as).
     */
    public void checkSaveThenCall(@NonNull final Callable<?> callable) {
        if (!host.hasUnsavedChanges()) {
            callIgnore(callable);
            return;
        }

        AlertDialog alert = host.alertBuilder().create();
        alert.setTitle(host.t(R.string.save_question));
        alert.setMessage(host.t(R.string.document_has_changes_save_them));

        alert.setButton(AlertDialog.BUTTON_POSITIVE,
                host.canSaveToCurrentUri() ? host.t(R.string.save) : host.t(R.string.saveas),
                (d, w) -> {
                    if (host.canSaveToCurrentUri()) {
                        saveInBackground(new Callable<Void>() {
                            @Override public Void call() {
                                callIgnore(callable);
                                return null;
                            }
                        }, null);
                    } else {
                        host.showSaveAsActivity();
                    }
                });
        alert.setButton(AlertDialog.BUTTON_NEUTRAL, host.t(R.string.cancel), (d, w) -> {});
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, host.t(R.string.no), (d, w) -> callIgnore(callable));
        alert.show();
    }

    /**
     * Prompt to save if dirty. If the user chooses Save and it succeeds, {@code onAfterSave} runs.
     * If they choose No, {@code onDiscard} runs.
     *
     * Note: if the document cannot be saved to the current URI, Save triggers the Save-As flow and
     * neither callback is invoked.
     *
     * @return true if a prompt was shown, false if not dirty.
     */
    public boolean promptToSaveIfDirty(@NonNull final Runnable onAfterSave,
                                       @NonNull final Runnable onDiscard) {
        if (!host.hasUnsavedChanges()) {
            return false;
        }

        AlertDialog alert = host.alertBuilder().create();
        alert.setTitle(host.t(R.string.save_question));
        alert.setMessage(host.t(R.string.document_has_changes_save_them));
        alert.setButton(AlertDialog.BUTTON_POSITIVE,
                host.canSaveToCurrentUri() ? host.t(R.string.save) : host.t(R.string.saveas),
                (d, w) -> {
                    if (host.canSaveToCurrentUri()) {
                        saveInBackground(new Callable<Void>() {
                            @Override public Void call() {
                                try { onAfterSave.run(); } catch (Throwable ignored) {}
                                return null;
                            }
                        }, null);
                    } else {
                        host.showSaveAsActivity();
                    }
                });
        alert.setButton(AlertDialog.BUTTON_NEUTRAL, host.t(R.string.cancel), (d, w) -> {});
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, host.t(R.string.no), (d, w) -> {
            try { onDiscard.run(); } catch (Throwable ignored) {}
        });
        alert.show();
        return true;
    }

    public void saveInBackground(final Callable<?> successCallable, final Callable<?> failureCallable) {
        lastSaveAttemptWasToCurrentUri = true;
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
        lastSaveAttemptWasToCurrentUri = false;
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
                    try {
                        host.onSaveFailure(error, lastSaveAttemptWasToCurrentUri);
                    } catch (Throwable ignore) {
                    }
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

    private void callIgnore(Callable<?> c) {
        try { c.call(); } catch (Exception ignored) {}
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
