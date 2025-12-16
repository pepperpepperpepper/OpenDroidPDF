package org.opendroidpdf.app.annotation;

/**
 * Small, Android-free holder for annotation mode state. Controllers/UI bind to this
 * instead of talking to DrawingService or views directly, reducing coupling and
 * clarifying ownership of mode changes.
 */
public interface AnnotationModeStore {

    boolean isDrawingModeActive();

    boolean isErasingModeActive();

    /**
     * Whether the UI is currently in "add text annotation" mode.
     */
    boolean isAddingTextModeActive();

    void enterDrawingMode();

    void enterErasingMode();

    void enterViewingMode();

    void enterAddingTextMode();
}
