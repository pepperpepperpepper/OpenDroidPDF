package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.app.alert.AlertDialogHelper;

public class AlertHostAdapter implements AlertDialogHelper.Host {
    private final androidx.appcompat.app.AlertDialog.Builder builder;
    private final android.app.Activity activity;

    public AlertHostAdapter(@NonNull android.app.Activity activity,
                            @NonNull androidx.appcompat.app.AlertDialog.Builder builder) {
        this.activity = activity;
        this.builder = builder;
    }

    @Override public @NonNull AlertDialog.Builder alertBuilder() { return builder; }
    @Override public boolean isFinishing() { return activity.isFinishing(); }
    @Override public @NonNull String t(int resId) { return activity.getString(resId); }
}

