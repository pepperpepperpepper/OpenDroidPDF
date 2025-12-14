package org.opendroidpdf.app.navigation;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.app.document.DocumentNavigationController;

/**
 * Centralizes small navigation helpers that used to sit in the activity.
 */
public final class NavigationDelegate {
    private final OpenDroidPDFActivity activity;
    private final DocumentNavigationController docNav;

    public NavigationDelegate(@NonNull OpenDroidPDFActivity activity,
                              @Nullable DocumentNavigationController docNav) {
        this.activity = activity;
        this.docNav = docNav;
    }

    public void openDocument() {
        if (docNav != null) docNav.openDocument();
    }

    public void showSaveAsActivity() {
        if (docNav != null) {
            activity.markIgnoreSaveOnStop();
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
