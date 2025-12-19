package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.app.toolbar.ToolbarStateController;
import org.opendroidpdf.app.toolbar.ToolbarStateCache;

public class ToolbarHostAdapter implements ToolbarStateController.Host {
    public interface Provider {
        boolean hasOpenDocument();
        boolean hasUnsavedChanges();
        boolean hasDocumentView();
        boolean hasLinkTarget();
        boolean isViewingNoteDocument();
        boolean isDrawingModeActive();
        boolean isErasingModeActive();
        boolean isSelectedAnnotationEditable();
        boolean isPreparingOptionsMenu();
        MuPDFPageView currentPageView();
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
    @Override public boolean isViewingNoteDocument() { return provider.isViewingNoteDocument(); }
    @Override public boolean isDrawingModeActive() { return provider.isDrawingModeActive(); }
    @Override public boolean isErasingModeActive() { return provider.isErasingModeActive(); }
    @Override public boolean isSelectedAnnotationEditable() { return provider.isSelectedAnnotationEditable(); }
    @Override public void invalidateOptionsMenu() { provider.invalidateOptionsMenu(); }
}
