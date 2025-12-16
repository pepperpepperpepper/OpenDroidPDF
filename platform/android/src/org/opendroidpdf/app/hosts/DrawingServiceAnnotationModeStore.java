package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.annotation.AnnotationModeStore;
import org.opendroidpdf.app.services.DrawingService;

/**
 * Bridges DrawingService’s mode ownership into the UI-facing AnnotationModeStore surface.
 */
public final class DrawingServiceAnnotationModeStore implements AnnotationModeStore {
    private final DrawingService drawingService;

    public DrawingServiceAnnotationModeStore(@NonNull DrawingService drawingService) {
        this.drawingService = drawingService;
    }

    @Override public boolean isDrawingModeActive() { return drawingService.isDrawingModeActive(); }

    @Override public boolean isErasingModeActive() { return drawingService.isErasingModeActive(); }

    @Override public boolean isAddingTextModeActive() { return drawingService.isAddingTextModeActive(); }

    @Override public void enterDrawingMode() { drawingService.switchToDrawingMode(); }

    @Override public void enterErasingMode() { drawingService.switchToErasingMode(); }

    @Override public void enterViewingMode() { drawingService.switchToViewingMode(); }

    @Override public void enterAddingTextMode() { drawingService.switchToAddingTextMode(); }
}
