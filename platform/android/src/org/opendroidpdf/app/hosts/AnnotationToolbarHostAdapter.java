package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.PageView;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.annotation.TextAnnotationStyleController;
import org.opendroidpdf.app.comments.CommentsListController;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.fillsign.FillSignAction;
import org.opendroidpdf.app.reader.gesture.ReaderMode;
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
        org.opendroidpdf.app.document.SaveUiDelegate saveUi = activity.getSaveUiDelegate();
        if (saveUi != null) {
            saveUi.saveInBackground(null, null);
            return;
        }
        if (exportController != null) exportController.saveDoc();
    }

    @Override
    public void requestCommentsList() {
        MuPDFReaderView doc = activity.getDocView();
        org.opendroidpdf.core.MuPdfRepository repo = activity.getRepository();
        if (doc == null || repo == null) return;
        new CommentsListController().show(
                activity,
                doc,
                repo,
                documentViewHostAdapter != null ? documentViewHostAdapter.sidecarAnnotationProviderOrNull() : null);
    }

    @Override
    public void requestTextSelectionMode() {
        try { drawingService.switchToViewingMode(); } catch (Throwable ignore) {}
        MuPDFReaderView docView = activity.getDocView();
        if (docView != null) {
            try { docView.requestMode(ReaderMode.SELECTING); } catch (Throwable ignore) {}
        }
    }

    @Override
    public void requestFillSign() {
        MuPDFReaderView docView = activity.getDocView();
        if (docView == null) return;
        if (!isPdfDocument()) return;

        final CharSequence[] items = new CharSequence[] {
                activity.getString(org.opendroidpdf.R.string.fill_sign_action_signature),
                activity.getString(org.opendroidpdf.R.string.fill_sign_action_initials),
                activity.getString(org.opendroidpdf.R.string.fill_sign_action_checkmark),
                activity.getString(org.opendroidpdf.R.string.fill_sign_action_cross),
                activity.getString(org.opendroidpdf.R.string.fill_sign_action_date),
                activity.getString(org.opendroidpdf.R.string.fill_sign_action_name),
        };

        new androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle(org.opendroidpdf.R.string.fill_sign_dialog_title)
                .setItems(items, (d, which) -> {
                    FillSignAction action;
                    switch (which) {
                        case 0: action = FillSignAction.SIGNATURE; break;
                        case 1: action = FillSignAction.INITIALS; break;
                        case 2: action = FillSignAction.CHECKMARK; break;
                        case 3: action = FillSignAction.CROSS; break;
                        case 4: action = FillSignAction.DATE; break;
                        case 5: action = FillSignAction.NAME; break;
                        default: action = null;
                    }
                    if (action != null) {
                        try {
                            docView.requestFillSignAction(action);
                        } catch (Throwable ignore) {
                        }
                    }
                })
                .show();
    }

    @Override
    public boolean isPdfDocument() {
        return documentViewHostAdapter != null && documentViewHostAdapter.isPdfDocument();
    }

    @Override public void cancelAnnotationMode() {
        ActionBarMode currentMode = activity.getActionBarMode();
        if (currentMode == null) return;
        PageView pageView = activity.getSelectedPageView();
        switch (currentMode) {
            case Annot:
                if (pageView != null) {
                    try { pageView.deselectText(); } catch (Throwable ignore) {}
                    try { pageView.cancelDraw(); } catch (Throwable ignore) {}
                }
                drawingService.switchToViewingMode();
                break;
            case Edit:
            case AddingTextAnnot:
                if (pageView != null) {
                    try { pageView.deselectAnnotation(); } catch (Throwable ignore) {}
                    try { pageView.deselectText(); } catch (Throwable ignore) {}
                }
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
            case Selection:
                if (pageView != null) {
                    try { pageView.deselectText(); } catch (Throwable ignore) {}
                }
                org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
                if (dvd != null) {
                    dvd.setViewingMode();
                } else {
                    MuPDFReaderView docView = activity.getDocView();
                    if (docView != null) {
                        try { docView.requestMode(ReaderMode.VIEWING); } catch (Throwable ignore) {}
                    }
                }
                break;
            default:
                break;
        }
    }
}
