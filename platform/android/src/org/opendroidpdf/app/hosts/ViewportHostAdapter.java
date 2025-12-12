package org.opendroidpdf.app.hosts;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.document.DocumentViewportController;
import org.opendroidpdf.app.document.RecentFilesController;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Adapter to expose required hooks from the activity to DocumentViewportController
 * without keeping large helper code in the activity.
 */
public final class ViewportHostAdapter implements DocumentViewportController.Host {
    private final OpenDroidPDFActivity activity;

    public ViewportHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @NonNull @Override public Context getContext() { return activity.getApplicationContext(); }
    @NonNull @Override public SharedPreferences getSharedPreferences(@NonNull String name, int mode) { return activity.getSharedPreferences(name, mode); }
    @Nullable @Override public MuPDFReaderView getDocView() { return activity.getDocView(); }
    @Nullable @Override public RecentFilesController getRecentFilesController() { return activity.getRecentFilesController(); }
    @Nullable @Override public Uri getCoreUri() {
        MuPdfRepository repo = activity.getRepository();
        return repo != null ? repo.getDocumentUri() : null;
    }
}

