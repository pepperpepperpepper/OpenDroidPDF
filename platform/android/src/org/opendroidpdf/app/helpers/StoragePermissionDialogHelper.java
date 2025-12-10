package org.opendroidpdf.app.helpers;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.R;

/**
 * Extracted UI for storage-permission rationale dialog. Keeps Activity slimmer.
 */
public final class StoragePermissionDialogHelper {
    private StoragePermissionDialogHelper() {}

    /**
     * Show rationale dialog if not already showing. Returns the new
     * value for the "showing" flag (true if dialog displayed).
     */
    public static boolean show(Context context, boolean alreadyShowing, int messageResId, final Runnable onContinue) {
        if (context == null || alreadyShowing) return alreadyShowing;

        final View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_permission_rationale, null, false);
        final TextView messageView = dialogView.findViewById(R.id.dialog_permission_message);
        messageView.setText(messageResId);

        AlertDialog dialog = new AlertDialog.Builder(context)
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
        dialog.show();
        return true;
    }
}

