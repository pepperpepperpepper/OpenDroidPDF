package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;

/**
 * Tiny adapter that shows the password dialog so the heavy lifting code
 * lives outside OpenDroidPDFActivity.
 */
public final class PasswordHostAdapter {
    private final OpenDroidPDFActivity activity;

    public PasswordHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    public void requestPassword(@NonNull OpenDroidPDFCore core,
                                @NonNull AlertDialog.Builder alertBuilder) {
        org.opendroidpdf.app.dialog.Dialogs.showPassword(
                activity,
                alertBuilder,
                core,
                new Runnable() { @Override public void run() { activity.finish(); } }
        );
    }
}

