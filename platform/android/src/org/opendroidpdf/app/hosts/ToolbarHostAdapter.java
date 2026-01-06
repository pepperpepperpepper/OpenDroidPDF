package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.toolbar.ToolbarStateController;
import org.opendroidpdf.app.toolbar.ToolbarStateCache;

public class ToolbarHostAdapter implements ToolbarStateController.Host {
    public interface Provider {
        boolean hasOpenDocument();
        boolean hasUnsavedChanges();
        boolean hasDocumentView();
        boolean hasLinkTarget();
        boolean isPdfDocument();
        boolean isEpubDocument();
        boolean canSaveToCurrentUri();
        boolean isViewingNoteDocument();
        boolean isDrawingModeActive();
        boolean isErasingModeActive();
        boolean isFormFieldHighlightEnabled();
        boolean isSelectedAnnotationEditable();
        boolean isPreparingOptionsMenu();
        void invalidateOptionsMenu();
    }

    private final Provider provider;

    public ToolbarHostAdapter(@NonNull Provider provider) {
        this.provider = provider;
    }

    @Override public boolean hasOpenDocument() { return provider.hasOpenDocument(); }
    @Override public boolean hasDocumentView() { return provider.hasDocumentView(); }
    @Override public boolean isPreparingOptionsMenu() { return provider.isPreparingOptionsMenu(); }
    @Override public boolean canUndo() { return ToolbarStateCache.get().getCanUndo(); }
    @Override public boolean hasUnsavedChanges() { return provider.hasUnsavedChanges(); }
    @Override public boolean hasLinkTarget() { return provider.hasLinkTarget(); }
    @Override public boolean isPdfDocument() { return provider.isPdfDocument(); }
    @Override public boolean isEpubDocument() { return provider.isEpubDocument(); }
    @Override public boolean canSaveToCurrentUri() { return provider.canSaveToCurrentUri(); }
    @Override public boolean isViewingNoteDocument() { return provider.isViewingNoteDocument(); }
    @Override public boolean isDrawingModeActive() { return provider.isDrawingModeActive(); }
    @Override public boolean isErasingModeActive() { return provider.isErasingModeActive(); }
    @Override public boolean isFormFieldHighlightEnabled() { return provider.isFormFieldHighlightEnabled(); }
    @Override public boolean isSelectedAnnotationEditable() { return provider.isSelectedAnnotationEditable(); }
    @Override public void invalidateOptionsMenu() { provider.invalidateOptionsMenu(); }
}
