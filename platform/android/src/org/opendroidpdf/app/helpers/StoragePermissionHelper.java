package org.opendroidpdf.app.helpers;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;

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

    public static boolean ensureStoragePermissionForIntent(final OpenDroidPDFActivity activity,
                                                          final StoragePermissionController controller,
                                                          Intent intent) {
        if (activity == null || controller == null || intent == null) {
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
                activity.startActivityForResult(manageIntent, RequestCodes.MANAGE_STORAGE);
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

        StoragePermissionDialogHelper.show(activity, controller, R.string.storage_permission_standard_message, new Runnable() {
                @Override
                public void run() {
                    ActivityCompat.requestPermissions(activity,
                                                      missingPermissions.toArray(new String[missingPermissions.size()]),
                                                      RequestCodes.STORAGE_PERMISSION);
                }
            });
        return false;
    }
}
