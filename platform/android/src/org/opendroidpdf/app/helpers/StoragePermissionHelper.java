package org.opendroidpdf.app.helpers;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;

import java.util.ArrayList;

/**
 * Centralizes storage-permission checks for opening documents.
 * Mirrors the legacy logic but keeps it reusable for future intent routing.
 */
public final class StoragePermissionHelper {

    private StoragePermissionHelper() {}

    public static boolean ensureStoragePermissionForIntent(final OpenDroidPDFActivity activity, Intent intent) {
        if (activity == null || intent == null) {
            return false;
        }

        boolean hasDocumentData = intent.getData() != null;
        boolean isFileUri = hasDocumentData && "file".equalsIgnoreCase(intent.getData().getScheme());

        // If we already have scoped storage (SAF) or internal data, allow.
        if (hasDocumentData && activity.isUriInAppPrivateStorage(intent.getData()))
            return true;

        // For SAF URIs the framework mediates permissions; no runtime perms needed.
        if (hasDocumentData && "content".equalsIgnoreCase(intent.getData().getScheme()))
            return true;

        // Android 11 MANAGE_EXTERNAL_STORAGE path
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                if (isFileUri)
                    return true;
            } else {
                // request broad access if a file:// is being opened
                Intent manageIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                manageIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(manageIntent, OpenDroidPDFActivity.MANAGE_STORAGE_REQUEST);
                activity.setAwaitingManageStoragePermission(true);
                return false;
            }
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M)
            return true;

        String[] permissions = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
        {
            // For API 33+ we rely on SAF grants or MANAGE_EXTERNAL_STORAGE;
            // no runtime storage permissions are required for opening PDFs.
        }
        else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
        {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        else
        {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }

        if (permissions == null || permissions.length == 0)
            return true;

        final ArrayList<String> missingPermissions = new ArrayList<String>();
        for (String permissionName : permissions)
        {
            if (ContextCompat.checkSelfPermission(activity, permissionName) != PackageManager.PERMISSION_GRANTED)
            {
                missingPermissions.add(permissionName);
            }
        }

        if (missingPermissions.isEmpty())
            return true;

        showStoragePermissionExplanation(activity, R.string.storage_permission_standard_message, new Runnable() {
                @Override
                public void run() {
                    ActivityCompat.requestPermissions(activity,
                                                      missingPermissions.toArray(new String[missingPermissions.size()]),
                                                      OpenDroidPDFActivity.STORAGE_PERMISSION_REQUEST);
                }
            });
        return false;
    }

    private static void showStoragePermissionExplanation(final OpenDroidPDFActivity activity, @StringRes int messageResId, final Runnable onContinue)
    {
        if (activity.isShowingStoragePermissionDialog())
            return;

        final View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_permission_rationale, null, false);
        final TextView messageView = dialogView.findViewById(R.id.dialog_permission_message);
        messageView.setText(messageResId);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.storage_permission_title)
            .setView(dialogView)
            .setPositiveButton(R.string.storage_permission_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        activity.setShowingStoragePermissionDialog(false);
                        dialogInterface.dismiss();
                        if (onContinue != null)
                            onContinue.run();
                    }
                })
            .setNegativeButton(R.string.storage_permission_not_now, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        activity.setShowingStoragePermissionDialog(false);
                        dialogInterface.dismiss();
                    }
                })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        activity.setShowingStoragePermissionDialog(false);
                    }
                })
            .create();

        activity.setShowingStoragePermissionDialog(true);
        dialog.show();
    }
}
