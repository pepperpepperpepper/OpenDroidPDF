package org.opendroidpdf;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.selection.SidecarSelectionController;

/**
 * Coordinates selection-driven actions between embedded PDF annotations and the sidecar overlay.
 *
 * <p>Kept separate from {@link MuPDFPageView} so the view doesn't need to branch on "embedded vs sidecar"
 * for delete/edit/deselect behavior.</p>
 */
final class PageSelectionCoordinator {

    interface Host {
        void refreshUndoState();
    }

    private final SidecarSelectionController sidecarSelectionController;
    private final SelectionActionRouter selectionActionRouter;
    private final Host host;

    PageSelectionCoordinator(@NonNull SidecarSelectionController sidecarSelectionController,
                             @NonNull SelectionActionRouter selectionActionRouter,
                             @NonNull Host host) {
        this.sidecarSelectionController = sidecarSelectionController;
        this.selectionActionRouter = selectionActionRouter;
        this.host = host;
    }

    void deleteSelectedAnnotation() {
        if (sidecarSelectionController.deleteSelected()) {
            host.refreshUndoState();
            return;
        }
        selectionActionRouter.deleteSelectedAnnotation();
    }

    void editSelectedAnnotation() {
        if (sidecarSelectionController.editSelected()) return;
        selectionActionRouter.editSelectedAnnotation();
    }

    boolean selectedAnnotationIsEditable() {
        if (sidecarSelectionController.hasSelection()) {
            return sidecarSelectionController.isSelectionEditable();
        }
        return selectionActionRouter.selectedAnnotationIsEditable();
    }

    void deselectAnnotation() {
        sidecarSelectionController.clearSelection();
        selectionActionRouter.deselectAnnotation();
    }
}
