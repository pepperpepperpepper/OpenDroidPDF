package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.debug.DebugActionsController;
import org.opendroidpdf.core.MuPdfRepository;

/** Adapter for {@link DebugActionsController.Host} delegating to the activity. */
public final class DebugActionsHostAdapter implements DebugActionsController.Host {
    private final OpenDroidPDFActivity activity;

    public DebugActionsHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override public @NonNull Context context() { return activity; }
    @Override public @Nullable MuPDFReaderView docViewOrNull() { return activity.getDocView(); }
    @Override public @Nullable org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController textMultiSelectOrNull() {
        MuPDFReaderView v = activity.getDocView();
        return v != null ? v.getTextAnnotationMultiSelectController() : null;
    }
    @Override public @Nullable MuPdfRepository repositoryOrNull() { return activity.getRepository(); }
    @Override public void commitPendingInkToCoreBlocking() { activity.commitPendingInkToCoreBlocking(); }
    @Override public @Nullable AlertDialog.Builder alertBuilder() { return activity.getAlertBuilder(); }
    @Override public void setAlertBuilder(@NonNull AlertDialog.Builder builder) { activity.setAlertBuilder(builder); }
}
