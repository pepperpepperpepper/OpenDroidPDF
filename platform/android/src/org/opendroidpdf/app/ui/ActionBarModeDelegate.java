package org.opendroidpdf.app.ui;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.annotation.AnnotationModeStore;

/**
 * Keeps ActionBarMode transitions and state accessors out of the activity body.
 * Annotation modes are owned by AnnotationModeStore; this delegate only renders them.
 */
public final class ActionBarModeDelegate {
    private AnnotationModeStore annotationModeStore;

    // Only non-annotation modes are cached locally; annotation modes are derived from the store.
    private ActionBarMode uiMode = ActionBarMode.Main;

    public ActionBarModeDelegate() { this.annotationModeStore = null; }
    public ActionBarModeDelegate(@NonNull AnnotationModeStore annotationModeStore) {
        this.annotationModeStore = annotationModeStore;
    }

    public void attachAnnotationModeStore(@NonNull AnnotationModeStore store) {
        this.annotationModeStore = store;
    }

    /**
     * Current ActionBar mode, derived from the annotation store when applicable.
     */
    public ActionBarMode current() {
        if (annotationModeStore != null) {
            if (annotationModeStore.isAddingTextModeActive()) {
                return ActionBarMode.AddingTextAnnot;
            }
            if (annotationModeStore.isDrawingModeActive() || annotationModeStore.isErasingModeActive()) {
                return ActionBarMode.Annot;
            }
        }
        return uiMode;
    }

    /**
     * Set a non-annotation UI mode. Annotation modes are owned by AnnotationModeStore, so we
     * ignore attempts to set them here to avoid duplicating state.
     */
    public void set(@NonNull ActionBarMode newMode) {
        switch (newMode) {
            case Annot:
            case AddingTextAnnot:
                // Annotation modes are driven by AnnotationModeStore/DrawingService.
                return;
            default:
                uiMode = newMode;
        }
    }

    public void setMain() { uiMode = ActionBarMode.Main; }
    public void setSelection() { uiMode = ActionBarMode.Selection; }
    public void setEdit() { uiMode = ActionBarMode.Edit; }
    public void setSearch() { uiMode = ActionBarMode.Search; }
    public void setHidden() { uiMode = ActionBarMode.Hidden; }
    public void setMainIfHidden() { if (uiMode == ActionBarMode.Hidden) uiMode = ActionBarMode.Main; }

    public boolean isEdit() { return uiMode == ActionBarMode.Edit; }
    public boolean isAddingTextAnnot() {
        return annotationModeStore != null && annotationModeStore.isAddingTextModeActive();
    }
    public boolean isSearchOrHidden() {
        ActionBarMode m = current();
        return m == ActionBarMode.Search || m == ActionBarMode.Hidden;
    }
}
