package org.opendroidpdf.app.toolbar;

/**
 * Pure data holder that describes desired menu visibility and enabled state.
 * Computed by {@link MenuStateEvaluator} and applied by {@link ToolbarStateController}.
 */
public final class MenuState {
    public final boolean groupDocumentActionsEnabled;
    public final boolean groupEditorToolsEnabled;
    public final boolean groupEditorToolsVisible;

    public final boolean undoVisible;
    public final boolean undoEnabled;

    public final boolean redoVisible;
    public final boolean redoEnabled;

    public final boolean saveEnabled;

    public final boolean linkBackVisible;
    public final boolean linkBackEnabled;

    public final boolean searchVisible;
    public final boolean searchEnabled;

    public final boolean drawVisible;
    public final boolean drawEnabled;

    public final boolean addTextVisible;
    public final boolean addTextEnabled;

    public final boolean printEnabled;
    public final boolean shareEnabled;

    public final boolean readingSettingsVisible;
    public final boolean readingSettingsEnabled;

    public MenuState(boolean groupDocumentActionsEnabled,
                     boolean groupEditorToolsEnabled,
                     boolean groupEditorToolsVisible,
                     boolean undoVisible,
                     boolean undoEnabled,
                     boolean redoVisible,
                     boolean redoEnabled,
                     boolean saveEnabled,
                     boolean linkBackVisible,
                     boolean linkBackEnabled,
                     boolean searchVisible,
                     boolean searchEnabled,
                     boolean drawVisible,
                     boolean drawEnabled,
                     boolean addTextVisible,
                     boolean addTextEnabled,
                     boolean printEnabled,
                     boolean shareEnabled,
                     boolean readingSettingsVisible,
                     boolean readingSettingsEnabled) {
        this.groupDocumentActionsEnabled = groupDocumentActionsEnabled;
        this.groupEditorToolsEnabled = groupEditorToolsEnabled;
        this.groupEditorToolsVisible = groupEditorToolsVisible;
        this.undoVisible = undoVisible;
        this.undoEnabled = undoEnabled;
        this.redoVisible = redoVisible;
        this.redoEnabled = redoEnabled;
        this.saveEnabled = saveEnabled;
        this.linkBackVisible = linkBackVisible;
        this.linkBackEnabled = linkBackEnabled;
        this.searchVisible = searchVisible;
        this.searchEnabled = searchEnabled;
        this.drawVisible = drawVisible;
        this.drawEnabled = drawEnabled;
        this.addTextVisible = addTextVisible;
        this.addTextEnabled = addTextEnabled;
        this.printEnabled = printEnabled;
        this.shareEnabled = shareEnabled;
        this.readingSettingsVisible = readingSettingsVisible;
        this.readingSettingsEnabled = readingSettingsEnabled;
    }
}
