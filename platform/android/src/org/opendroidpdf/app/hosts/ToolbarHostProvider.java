package org.opendroidpdf.app.hosts;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.hosts.ToolbarHostAdapter.Provider;

public class ToolbarHostProvider implements Provider {
    private final OpenDroidPDFActivity activity;
    private final DocumentViewHostAdapter documentViewHostAdapter;

    public ToolbarHostProvider(OpenDroidPDFActivity activity,
                               DocumentViewHostAdapter documentViewHostAdapter) {
        this.activity = activity;
        this.documentViewHostAdapter = documentViewHostAdapter;
    }

    @Override public boolean hasOpenDocument() { return activity.hasCore(); }
    @Override public boolean hasUnsavedChanges() { return activity.hasUnsavedChanges(); }
    @Override public boolean hasDocumentView() { return activity.getDocView() != null; }
    @Override public boolean hasLinkTarget() { return activity.isLinkBackAvailable(); }
    @Override public boolean isPdfDocument() { return documentViewHostAdapter != null && documentViewHostAdapter.isPdfDocument(); }
    @Override public boolean isEpubDocument() { return documentViewHostAdapter != null && documentViewHostAdapter.isEpubDocument(); }
    @Override public boolean canSaveToCurrentUri() { return activity.canSaveToCurrentUri(); }
    @Override public boolean isViewingNoteDocument() { return activity.isCurrentNoteDocument(); }
    @Override public boolean isDrawingModeActive() { return activity.isDrawingModeActive(); }
    @Override public boolean isErasingModeActive() { return activity.isErasingModeActive(); }
    @Override public boolean isSelectedAnnotationEditable() { return activity.isSelectedAnnotationEditable(); }
    @Override public boolean isPreparingOptionsMenu() { return activity.isPreparingOptionsMenu(); }
    @Override public MuPDFPageView currentPageView() { return documentViewHostAdapter != null ? documentViewHostAdapter.currentPageViewOrNull() : null; }
    @Override public void invalidateOptionsMenu() { activity.invalidateOptionsMenuSafely(); }
}
