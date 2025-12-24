package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.document.DocumentLifecycleManager;

/** Adapter for {@link DocumentLifecycleManager.Host} that delegates to the activity. */
public final class DocumentLifecycleHostAdapter implements DocumentLifecycleManager.Host {
    private final OpenDroidPDFActivity activity;

    public DocumentLifecycleHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override public @NonNull Context context() { return activity; }

    @Override
    public @Nullable AlertDialog.Builder alertBuilder() {
        return activity.getAlertBuilder();
    }
}

