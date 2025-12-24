package org.opendroidpdf.app.document;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.concurrent.Callable;

/**
 * Lazy wrapper around {@link SaveUiController} to keep the activity lean.
 */
public final class SaveUiDelegate {
    private final SaveUiController.Host host;
    private SaveUiController controller;

    public SaveUiDelegate(@NonNull SaveUiController.Host host) {
        this.host = host;
    }

    private SaveUiController controller() {
        if (controller == null) {
            controller = new SaveUiController(host);
        }
        return controller;
    }

    public void saveAsInBackground(@NonNull final Uri uri,
                                   final Callable<?> successCallable,
                                   final Callable<?> failureCallable) {
        controller().saveAsInBackground(uri, successCallable, failureCallable);
    }

    public void saveAsInBackgroundCompat(@NonNull final Uri uri,
                                         final Callable<?> successCallable,
                                         final Callable<?> failureCallable) {
        controller().saveAsInBackground(uri, successCallable, failureCallable);
    }

    public void saveInBackground(final Callable<?> successCallable,
                                 final Callable<?> failureCallable) {
        controller().saveInBackground(successCallable, failureCallable);
    }

    public void callInBackgroundAndShowDialog(final String message,
                                              final Callable<Exception> saveCallable,
                                              final Callable<?> successCallable,
                                              final Callable<?> failureCallable) {
        controller().callInBackgroundAndShowDialog(message, saveCallable, successCallable, failureCallable);
    }

    public void cancelActiveSaveJob() {
        controller().cancelActiveSaveJob();
    }

    public void checkSaveThenCall(@NonNull Callable<?> callable) {
        controller().checkSaveThenCall(callable);
    }

    public boolean promptToSaveIfDirty(@NonNull Runnable onAfterSave,
                                       @NonNull Runnable onDiscard) {
        return controller().promptToSaveIfDirty(onAfterSave, onDiscard);
    }
}
