package org.opendroidpdf.app.dialog;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFCore;

/**
 * Thin wrappers around dialog helpers to keep Activity code minimal.
 */
public final class Dialogs {
    private Dialogs() {}

    public static void showPassword(final AppCompatActivity activity,
                                    @Nullable AlertDialog.Builder existing,
                                    final OpenDroidPDFCore core,
                                    final Runnable onCancelled) {
        AlertDialog.Builder builder = existing != null ? existing : new AlertDialog.Builder(activity);
        org.opendroidpdf.app.dialog.PasswordDialogHelper.show(activity, builder, new org.opendroidpdf.app.dialog.PasswordDialogHelper.Callback() {
            @Override public boolean onPasswordEntered(String password) {
                return core != null && core.authenticatePassword(password);
            }
            @Override public void onCancelled() { if (onCancelled != null) onCancelled.run(); }
        });
    }

    public static void showGoToPage(final AppCompatActivity activity,
                                    @Nullable AlertDialog.Builder existing,
                                    final MuPDFReaderView docView) {
        AlertDialog.Builder builder = existing != null ? existing : new AlertDialog.Builder(activity);
        org.opendroidpdf.app.dialog.GoToPageDialog.show(activity, builder, docView);
    }
}

