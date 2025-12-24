package org.opendroidpdf.app.helpers;

import android.app.Activity;
import android.content.Intent;

/**
 * Holds ephemeral state for the storage-permission flow and delegates the
 * actual permission checks/requests to StoragePermissionHelper. Extracted to
 * slim the activity and make behavior testable.
 */
public final class StoragePermissionController {
    private boolean showingStoragePermissionDialog = false;

    public boolean ensureForIntent(Activity activity, Intent intent) {
        return StoragePermissionHelper.ensureStoragePermissionForIntent(activity, this, intent);
    }

    public boolean isShowingStoragePermissionDialog() {
        return showingStoragePermissionDialog;
    }

    public void setShowingStoragePermissionDialog(boolean showing) {
        this.showingStoragePermissionDialog = showing;
    }

    /**
     * Centralizes the permission result handling so the activity doesn't need to
     * duplicate logic. Returns true if the requestCode was recognized and handled.
     */
    public boolean handleRequestPermissionsResult(int requestCode,
                                                  int[] grantResults,
                                                  Runnable onGranted,
                                                  Runnable onDenied) {
        if (requestCode != RequestCodes.STORAGE_PERMISSION) {
            return false;
        }
        boolean granted = grantResults != null && grantResults.length > 0;
        if (granted) {
            for (int result : grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
        }
        if (granted) {
            if (onGranted != null) onGranted.run();
        } else {
            if (onDenied != null) onDenied.run();
        }
        return true;
    }
}
