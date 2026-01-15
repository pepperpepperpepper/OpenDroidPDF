package org.opendroidpdf.app.toolbar;

/**
 * Computes {@link MenuState} from simple inputs, keeping logic testable without Android dependencies.
 */
public final class MenuStateEvaluator {

    public static final class Inputs {
        public final boolean hasOpenDocument;
        public final boolean canUndo;
        public final boolean canRedo;
        public final boolean hasUnsavedChanges;
        public final boolean hasLinkTarget;
        public final boolean isPdfDocument;
        public final boolean isEpubDocument;
        public final boolean canSaveToCurrentUri;

        public Inputs(boolean hasOpenDocument,
                      boolean canUndo,
                      boolean canRedo,
                      boolean hasUnsavedChanges,
                      boolean hasLinkTarget,
                      boolean isPdfDocument,
                      boolean isEpubDocument,
                      boolean canSaveToCurrentUri) {
            this.hasOpenDocument = hasOpenDocument;
            this.canUndo = canUndo;
            this.canRedo = canRedo;
            this.hasUnsavedChanges = hasUnsavedChanges;
            this.hasLinkTarget = hasLinkTarget;
            this.isPdfDocument = isPdfDocument;
            this.isEpubDocument = isEpubDocument;
            this.canSaveToCurrentUri = canSaveToCurrentUri;
        }
    }

    private MenuStateEvaluator() {}

    public static MenuState compute(Inputs in) {
        boolean hasDoc = in.hasOpenDocument;
        boolean docActions = hasDoc;
        boolean editorDoc = hasDoc && (in.isPdfDocument || in.isEpubDocument);
        boolean editorToolsEnabled = editorDoc;
        boolean editorToolsVisible = editorDoc;

        boolean undoVisible = editorDoc;
        boolean undoEnabled = editorDoc && in.canUndo;

        boolean redoVisible = editorDoc;
        boolean redoEnabled = editorDoc && in.canRedo;

        // Only PDF currently supports "Save" semantics; EPUB and non-writable PDFs
        // use explicit export flows (share/print) that produce a PDF copy.
        boolean saveEnabled = hasDoc && in.isPdfDocument && in.canSaveToCurrentUri;

        boolean linkBackVisible = hasDoc && in.hasLinkTarget;
        boolean linkBackEnabled = linkBackVisible;

        boolean searchVisible = hasDoc;
        boolean searchEnabled = hasDoc;

        boolean drawVisible = editorDoc;
        boolean drawEnabled = editorDoc;

        boolean addTextVisible = editorDoc;
        boolean addTextEnabled = editorDoc;

        boolean canExportPdf = hasDoc && (in.isPdfDocument || in.isEpubDocument);
        boolean printEnabled = canExportPdf;
        boolean shareEnabled = canExportPdf;

        boolean readingSettingsVisible = hasDoc && in.isEpubDocument;
        boolean readingSettingsEnabled = readingSettingsVisible;

        return new MenuState(
                docActions,
                editorToolsEnabled,
                editorToolsVisible,
                undoVisible,
                undoEnabled,
                redoVisible,
                redoEnabled,
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
                shareEnabled,
                readingSettingsVisible,
                readingSettingsEnabled);
    }
}
