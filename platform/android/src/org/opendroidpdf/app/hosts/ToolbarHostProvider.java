package org.opendroidpdf.app.hosts;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.hosts.ToolbarHostAdapter.Provider;
import org.opendroidpdf.app.services.DrawingService;

public class ToolbarHostProvider implements Provider {
    private final OpenDroidPDFActivity activity;
    private final DocumentViewHostAdapter documentViewHostAdapter;
    private final DrawingService drawingService;

    public ToolbarHostProvider(OpenDroidPDFActivity activity,
                               DocumentViewHostAdapter documentViewHostAdapter,
                               DrawingService drawingService) {
        this.activity = activity;
        this.documentViewHostAdapter = documentViewHostAdapter;
        this.drawingService = drawingService;
    }

    @Override public boolean hasOpenDocument() { return activity.hasCore(); }
    @Override public boolean hasUnsavedChanges() { return activity.hasUnsavedChanges(); }
    @Override public boolean hasDocumentView() { return activity.getDocView() != null; }
    @Override public boolean hasLinkTarget() { return activity.isLinkBackAvailable(); }
    @Override public boolean isPdfDocument() { return documentViewHostAdapter != null && documentViewHostAdapter.isPdfDocument(); }
    @Override public boolean isEpubDocument() { return documentViewHostAdapter != null && documentViewHostAdapter.isEpubDocument(); }
    @Override public boolean canSaveToCurrentUri() { return activity.canSaveToCurrentUri(); }
    @Override public boolean isViewingNoteDocument() { return activity.isCurrentNoteDocument(); }
    @Override public boolean isDrawingModeActive() { return drawingService != null && drawingService.isDrawingModeActive(); }
    @Override public boolean isErasingModeActive() { return drawingService != null && drawingService.isErasingModeActive(); }
    @Override public boolean isFormFieldHighlightEnabled() {
        try {
            org.opendroidpdf.MuPDFReaderView v = activity.getDocView();
            return v != null && v.isFormFieldHighlightEnabled();
        } catch (Throwable ignore) {
            return false;
        }
    }
    @Override public boolean areCommentsVisible() {
        try {
            org.opendroidpdf.MuPDFReaderView v = activity.getDocView();
            return v == null || v.areCommentsVisible();
        } catch (Throwable ignore) {
            return true;
        }
    }
    @Override public boolean areSidecarNotesStickyModeEnabled() {
        try {
            org.opendroidpdf.MuPDFReaderView v = activity.getDocView();
            return v != null && v.areSidecarNotesStickyModeEnabled();
        } catch (Throwable ignore) {
            return false;
        }
    }
    @Override public boolean isSelectedAnnotationEditable() { return documentViewHostAdapter != null && documentViewHostAdapter.isSelectedAnnotationEditable(); }
    @Override public boolean isPreparingOptionsMenu() { return activity.isPreparingOptionsMenu(); }
    @Override public void invalidateOptionsMenu() { activity.invalidateOptionsMenuSafely(); }
}
