package org.opendroidpdf.app.navigation;

import android.content.Intent;

import androidx.annotation.Nullable;

import org.opendroidpdf.app.document.DocumentNavigationController;

/**
 * Centralizes small navigation helpers that used to sit in the activity.
 */
public final class NavigationDelegate {
    private final DocumentNavigationController docNav;
    private final org.opendroidpdf.app.lifecycle.SaveFlagController saveFlags;

    public NavigationDelegate(@Nullable DocumentNavigationController docNav,
                              @Nullable org.opendroidpdf.app.lifecycle.SaveFlagController saveFlags) {
        this.docNav = docNav;
        this.saveFlags = saveFlags;
    }

    public void openDocument() {
        if (docNav != null) docNav.openDocument();
    }

    public void showSaveAsActivity() {
        if (docNav != null) {
            if (saveFlags != null) saveFlags.markIgnoreSaveOnStop();
            docNav.showSaveAsActivity();
        }
    }

    public void openDocumentFromIntent(@Nullable Intent intent) {
        if (docNav == null || intent == null) return;
        docNav.openDocumentFromIntent(intent);
    }

    public void onRequestPermissionsResult(int requestCode,
                                           int[] grantResults,
                                           Runnable onGranted,
                                           Runnable onDenied) {
        if (docNav != null) {
            // no-op: actual handling lives in StoragePermissionController; kept for future routing
        }
    }
}
