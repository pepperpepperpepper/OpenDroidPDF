package org.opendroidpdf.app.toolbar;

/**
 * Computes {@link MenuState} from simple inputs, keeping logic testable without Android dependencies.
 */
public final class MenuStateEvaluator {

    public static final class Inputs {
        public final boolean hasOpenDocument;
        public final boolean canUndo;
        public final boolean hasUnsavedChanges;
        public final boolean hasLinkTarget;

        public Inputs(boolean hasOpenDocument, boolean canUndo, boolean hasUnsavedChanges, boolean hasLinkTarget) {
            this.hasOpenDocument = hasOpenDocument;
            this.canUndo = canUndo;
            this.hasUnsavedChanges = hasUnsavedChanges;
            this.hasLinkTarget = hasLinkTarget;
        }
    }

    private MenuStateEvaluator() {}

    public static MenuState compute(Inputs in) {
        boolean hasDoc = in.hasOpenDocument;
        boolean docActions = hasDoc;
        boolean editorToolsEnabled = hasDoc;
        boolean editorToolsVisible = hasDoc;

        boolean undoVisible = hasDoc;
        boolean undoEnabled = hasDoc && in.canUndo;

        // Always allow the Save action when a document is open; the save flow
        // already commits pending ink and will no-op if nothing changed.
        boolean saveEnabled = hasDoc;

        boolean linkBackVisible = hasDoc && in.hasLinkTarget;
        boolean linkBackEnabled = linkBackVisible;

        boolean searchVisible = hasDoc;
        boolean searchEnabled = hasDoc;

        boolean drawVisible = hasDoc;
        boolean drawEnabled = hasDoc;

        boolean addTextVisible = hasDoc;
        boolean addTextEnabled = hasDoc;

        boolean printEnabled = hasDoc;
        boolean shareEnabled = hasDoc;

        return new MenuState(
                docActions,
                editorToolsEnabled,
                editorToolsVisible,
                undoVisible,
                undoEnabled,
                saveEnabled,
                linkBackVisible,
                linkBackEnabled,
                searchVisible,
                searchEnabled,
                drawVisible,
                drawEnabled,
                addTextVisible,
                addTextEnabled,
                printEnabled,
                shareEnabled);
    }
}
