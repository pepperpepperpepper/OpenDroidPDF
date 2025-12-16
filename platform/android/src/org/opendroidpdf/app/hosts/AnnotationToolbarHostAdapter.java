package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.PageView;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.services.DrawingService;

/**
 * Host adapter for AnnotationToolbarController to keep OpenDroidPDFActivity slim.
 */
public final class AnnotationToolbarHostAdapter implements AnnotationToolbarController.Host {
    private final OpenDroidPDFActivity activity;
    private final DrawingService drawingService;

    public AnnotationToolbarHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                        @NonNull DrawingService drawingService) {
        this.activity = activity;
        this.drawingService = drawingService;
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

    @Override public boolean isSelectedAnnotationEditable() { return activity.isSelectedAnnotationEditable(); }

    @Override public @Nullable PageView getActivePageView() {
        return activity.getSelectedPageView();
    }

    @Override public boolean hasDocumentView() { return activity.getDocView() != null; }

    @Override public void notifyStrokeCountChanged(int strokeCount) {
        drawingService.notifyStrokeCountChanged(strokeCount);
    }

    @Override public void requestSaveDialog() {
        org.opendroidpdf.app.services.ServiceLocator.ExportService es = activity.getExportService();
        if (es != null) es.saveDoc();
    }

    @Override public void cancelAnnotationMode() {
        drawingService.cancelAnnotationMode(activity.getActionBarMode());
    }

    @Override public void confirmAnnotationChanges() {
        drawingService.confirmAnnotationChanges(activity.getActionBarMode());
    }
}
