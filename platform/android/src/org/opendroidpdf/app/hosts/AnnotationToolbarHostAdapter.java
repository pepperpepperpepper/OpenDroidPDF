package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.PageView;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;

/**
 * Host adapter for AnnotationToolbarController to keep OpenDroidPDFActivity slim.
 */
public final class AnnotationToolbarHostAdapter implements AnnotationToolbarController.Host {
    private final OpenDroidPDFActivity activity;

    public AnnotationToolbarHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
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
        MuPDFReaderView v = activity.getDocView();
        if (v == null) return null;
        android.view.View sel = v.getSelectedView();
        return (sel instanceof PageView) ? (PageView) sel : null;
    }

    @Override public boolean hasDocumentView() { return activity.getDocView() != null; }

    @Override public boolean isDrawingModeActive() { return activity.isDrawingModeActive(); }
    @Override public boolean isErasingModeActive() { return activity.isErasingModeActive(); }

    @Override public void switchToDrawingMode() {
        org.opendroidpdf.AnnotationModeController.switchToDrawingMode(activity.getDocView());
    }

    @Override public void switchToErasingMode() {
        org.opendroidpdf.AnnotationModeController.switchToErasingMode(activity.getDocView());
    }

    @Override public void switchToViewingMode() {
        org.opendroidpdf.AnnotationModeController.switchToViewingMode(activity.getDocView());
    }

    @Override public void switchToAddingTextMode() {
        org.opendroidpdf.AnnotationModeController.switchToAddingTextMode(activity.getDocView());
    }

    @Override public void notifyStrokeCountChanged(int strokeCount) {
        org.opendroidpdf.AnnotationModeController.notifyStrokeCountChanged(activity.getDocView(), strokeCount);
    }

    @Override public void cancelAnnotationMode() {
        org.opendroidpdf.AnnotationModeController.cancelAnnotationMode(activity.getDocView(), activity.getActionBarMode());
    }

    @Override public void confirmAnnotationChanges() {
        org.opendroidpdf.AnnotationModeController.confirmAnnotationChanges(activity.getDocView(), activity.getActionBarMode());
    }
}
