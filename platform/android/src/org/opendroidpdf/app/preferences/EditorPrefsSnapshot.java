package org.opendroidpdf.app.preferences;

/** Immutable snapshot of editor/annotation preferences (excluding pen thickness/color). */
public final class EditorPrefsSnapshot {
    public final float eraserThickness;
    public final boolean smartTextSelectionEnabled;

    public final int highlightColorIndex;
    public final int underlineColorIndex;
    public final int strikeoutColorIndex;
    public final int textAnnotIconColorIndex;

    public EditorPrefsSnapshot(float eraserThickness,
                               boolean smartTextSelectionEnabled,
                               int highlightColorIndex,
                               int underlineColorIndex,
                               int strikeoutColorIndex,
                               int textAnnotIconColorIndex) {
        this.eraserThickness = eraserThickness;
        this.smartTextSelectionEnabled = smartTextSelectionEnabled;
        this.highlightColorIndex = highlightColorIndex;
        this.underlineColorIndex = underlineColorIndex;
        this.strikeoutColorIndex = strikeoutColorIndex;
        this.textAnnotIconColorIndex = textAnnotIconColorIndex;
    }
}

