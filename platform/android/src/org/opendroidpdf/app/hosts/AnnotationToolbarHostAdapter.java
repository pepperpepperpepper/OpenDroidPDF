package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.PageView;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.services.DrawingService;

/**
 * Host adapter for AnnotationToolbarController to keep OpenDroidPDFActivity slim.
 */
public final class AnnotationToolbarHostAdapter implements AnnotationToolbarController.Host {
    private final OpenDroidPDFActivity activity;
    private final DocumentViewHostAdapter documentViewHostAdapter;
    private final DrawingService drawingService;
    private final ExportController exportController;

    public AnnotationToolbarHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                        @NonNull DocumentViewHostAdapter documentViewHostAdapter,
                                        @NonNull DrawingService drawingService,
                                        @NonNull ExportController exportController) {
        this.activity = activity;
        this.documentViewHostAdapter = documentViewHostAdapter;
        this.drawingService = drawingService;
        this.exportController = exportController;
    }

    @NonNull @Override public Context getContext() { return activity; }

    @Override public void showAnnotationInfo(@NonNull String message) { activity.showInfo(message); }

    @Override public void showPenSizeDialog() {
        org.opendroidpdf.app.annotation.PenSettingsController pc = activity.getPenSettingsController();
        if (pc != null) pc.show();
    }

    @Override public void showInkColorDialog() {
        // Unified pen settings dialog handles both color and size.
        org.opendroidpdf.app.annotation.PenSettingsController pc = activity.getPenSettingsController();
        if (pc != null) pc.show();
    }

    @Override public boolean isSelectedAnnotationEditable() {
        return documentViewHostAdapter != null && documentViewHostAdapter.isSelectedAnnotationEditable();
    }

    @Override public @Nullable PageView getActivePageView() {
        return activity.getSelectedPageView();
    }

    @Override public boolean hasDocumentView() { return activity.getDocView() != null; }

    @Override public void notifyStrokeCountChanged(int strokeCount) {
        drawingService.notifyStrokeCountChanged(strokeCount);
    }

    @Override
    public boolean finalizePendingInk() {
        return drawingService.finalizePendingInk();
    }

    @Override public void requestSaveDialog() {
        if (exportController != null) exportController.saveDoc();
    }

    @Override public void cancelAnnotationMode() {
        drawingService.cancelAnnotationMode(activity.getActionBarMode());
    }

    @Override public void confirmAnnotationChanges() {
        drawingService.confirmAnnotationChanges(activity.getActionBarMode());
    }
}
