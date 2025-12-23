package org.opendroidpdf.app.helpers;

import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.R;

/**
 * Extracted UI for storage-permission rationale dialog. Keeps Activity slimmer.
 */
public final class StoragePermissionDialogHelper {
    private StoragePermissionDialogHelper() {}

    /**
     * Show rationale dialog if not already showing.
     */
    public static void show(AppCompatActivity activity,
                            StoragePermissionController controller,
                            @StringRes int messageResId,
                            final Runnable onContinue) {
        if (activity == null || controller == null || controller.isShowingStoragePermissionDialog()) return;

        controller.setShowingStoragePermissionDialog(true);

        try {
        final View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_permission_rationale, null, false);
        final TextView messageView = dialogView.findViewById(R.id.dialog_permission_message);
        messageView.setText(messageResId);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.storage_permission_title)
                .setView(dialogView)
                .setPositiveButton(R.string.storage_permission_continue, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        if (onContinue != null) onContinue.run();
                    }
                })
                .setNegativeButton(R.string.storage_permission_not_now, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                    }
                })
                .create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface d) {
                controller.setShowingStoragePermissionDialog(false);
            }
        });
        dialog.show();
        } catch (Throwable t) {
            controller.setShowingStoragePermissionDialog(false);
        }
    }
}
