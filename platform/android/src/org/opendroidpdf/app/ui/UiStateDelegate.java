package org.opendroidpdf.app.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;

/**
 * Small helper to hold misc UI state helpers (title, alerts, memory checks)
 * outside the activity body.
 */
public final class UiStateDelegate {
    private final OpenDroidPDFActivity activity;
    private AlertDialog.Builder alertBuilder;

    public UiStateDelegate(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    public AlertDialog.Builder alertBuilder() {
        if (alertBuilder == null) {
            alertBuilder = new AlertDialog.Builder(activity);
            alertBuilder.setTitle(R.string.app_name);
        }
        return alertBuilder;
    }

    public void setAlertBuilder(AlertDialog.Builder builder) {
        this.alertBuilder = builder;
    }

    public void setTitle() {
        org.opendroidpdf.app.ui.TitleHelper.setTitle(activity, activity.getDocView(), activity.getCore());
    }

    public boolean isMemoryLow() {
        return org.opendroidpdf.app.ui.UiUtils.isMemoryLow(activity);
    }
}
