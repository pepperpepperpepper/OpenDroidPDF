package org.opendroidpdf.app.hosts;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.annotation.TextAnnotationStyleController;

/** Host adapter for TextAnnotationStyleController to keep OpenDroidPDFActivity slim. */
public final class TextAnnotationStyleHostAdapter implements TextAnnotationStyleController.Host {
    private final OpenDroidPDFActivity activity;
    private final DocumentViewHostAdapter documentViewHostAdapter;

    public TextAnnotationStyleHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                          @NonNull DocumentViewHostAdapter documentViewHostAdapter) {
        this.activity = activity;
        this.documentViewHostAdapter = documentViewHostAdapter;
    }

    @NonNull @Override public Context getContext() { return activity; }

    @NonNull @Override public LayoutInflater getLayoutInflater() { return activity.getLayoutInflater(); }

    @Nullable @Override public MuPDFPageView activePageViewOrNull() {
        return documentViewHostAdapter != null ? documentViewHostAdapter.currentPageViewOrNull() : null;
    }

    @Override public void showAnnotationInfo(@NonNull String message) { activity.showInfo(message); }
}

