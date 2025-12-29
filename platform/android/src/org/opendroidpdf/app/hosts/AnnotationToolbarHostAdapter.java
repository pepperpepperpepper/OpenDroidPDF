package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.PageView;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.annotation.TextAnnotationStyleController;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.services.DrawingService;
import org.opendroidpdf.app.ui.ActionBarMode;

/**
 * Host adapter for AnnotationToolbarController to keep OpenDroidPDFActivity slim.
 */
public final class AnnotationToolbarHostAdapter implements AnnotationToolbarController.Host {
    private final OpenDroidPDFActivity activity;
    private final DocumentViewHostAdapter documentViewHostAdapter;
    private final DrawingService drawingService;
    private final ExportController exportController;
    private final TextAnnotationStyleController textAnnotationStyleController;

    public AnnotationToolbarHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                        @NonNull DocumentViewHostAdapter documentViewHostAdapter,
                                        @NonNull DrawingService drawingService,
                                        @NonNull ExportController exportController,
                                        @NonNull TextAnnotationStyleController textAnnotationStyleController) {
        this.activity = activity;
        this.documentViewHostAdapter = documentViewHostAdapter;
        this.drawingService = drawingService;
        this.exportController = exportController;
        this.textAnnotationStyleController = textAnnotationStyleController;
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

    @Override
    public void showTextStyleDialog() {
        if (textAnnotationStyleController != null) textAnnotationStyleController.show();
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
        ActionBarMode currentMode = activity.getActionBarMode();
        if (currentMode == null) return;
        switch (currentMode) {
            case Annot:
            case Edit:
            case AddingTextAnnot:
                drawingService.switchToViewingMode();
                break;
            default:
                break;
        }
    }

    @Override public void confirmAnnotationChanges() {
        ActionBarMode currentMode = activity.getActionBarMode();
        if (currentMode == null) return;
        PageView pageView = activity.getSelectedPageView();
        switch (currentMode) {
            case Annot:
                // Use the DrawingService finalization surface so ink commit behavior stays centralized.
                drawingService.finalizePendingInk();
                drawingService.switchToViewingMode();
                break;
            case Edit:
                if (pageView != null) {
                    pageView.deselectAnnotation();
                }
                drawingService.switchToViewingMode();
                break;
            default:
                break;
        }
    }
}
