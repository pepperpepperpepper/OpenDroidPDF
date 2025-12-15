package org.opendroidpdf.app.services;

/**
 * Small surface for drawing/ink-related actions that other UI components
 * (e.g., pen settings) can rely on without reaching into views directly.
 */
public interface DrawingService {
    /** Commit any pending ink so thickness/color changes don’t mutate old strokes. */
    void finalizePendingInkBeforePenSettingChange();
}
