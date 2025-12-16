package org.opendroidpdf.app.services;

/**
 * Small surface for drawing/ink-related actions that other UI components
 * (e.g., pen settings) can rely on without reaching into views directly.
 */
public interface DrawingService {
    /** Commit any pending ink so thickness/color changes don’t mutate old strokes. */
    void finalizePendingInk();

    /**
     * Optional hook for reacting to pen preference changes without leaking Android types.
     * Implementations may ignore the snapshot if no extra work is needed.
     */
    default void onPenSettingsChanged(org.opendroidpdf.app.preferences.PenPrefsSnapshot snapshot) {
        // no-op by default
    }

    // --- Annotation mode ownership ---
    void switchToDrawingMode();
    void switchToErasingMode();
    void switchToViewingMode();
    void switchToAddingTextMode();

    boolean isDrawingModeActive();
    boolean isErasingModeActive();
    boolean isAddingTextModeActive();

    void cancelAnnotationMode(org.opendroidpdf.app.ui.ActionBarMode currentMode);
    void confirmAnnotationChanges(org.opendroidpdf.app.ui.ActionBarMode currentMode);

    void notifyStrokeCountChanged(int strokeCount);
}
