package org.opendroidpdf.app.selection;

import androidx.annotation.NonNull;

/**
 * Coordinates selection-driven actions between embedded PDF annotations and the sidecar overlay.
 *
 * <p>Kept separate from {@code MuPDFPageView} so the view doesn't need to branch on "embedded vs sidecar"
 * for delete/edit/deselect behavior.</p>
 */
public final class PageSelectionCoordinator {

    public interface Host {
        void refreshUndoState();
    }

    private final SidecarSelectionController sidecarSelectionController;
    private final SelectionActionRouter selectionActionRouter;
    private final Host host;

    public PageSelectionCoordinator(@NonNull SidecarSelectionController sidecarSelectionController,
                                    @NonNull SelectionActionRouter selectionActionRouter,
                                    @NonNull Host host) {
        this.sidecarSelectionController = sidecarSelectionController;
        this.selectionActionRouter = selectionActionRouter;
        this.host = host;
    }

    public void deleteSelectedAnnotation() {
        if (sidecarSelectionController.deleteSelected()) {
            host.refreshUndoState();
            return;
        }
        selectionActionRouter.deleteSelectedAnnotation();
    }

    public void editSelectedAnnotation() {
        if (sidecarSelectionController.editSelected()) return;
        selectionActionRouter.editSelectedAnnotation();
    }

    public boolean selectedAnnotationIsEditable() {
        if (sidecarSelectionController.hasSelection()) {
            return sidecarSelectionController.isSelectionEditable();
        }
        return selectionActionRouter.selectedAnnotationIsEditable();
    }

    public void deselectAnnotation() {
        sidecarSelectionController.clearSelection();
        selectionActionRouter.deselectAnnotation();
    }
}
