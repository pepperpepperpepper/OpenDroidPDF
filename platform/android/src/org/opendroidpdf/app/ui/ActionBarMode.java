package org.opendroidpdf.app.ui;

/**
 * Public enum for the activity's high-level toolbar/action-bar state.
 * Extracted from the activity to reduce its size and surface.
 */
public enum ActionBarMode {
    Main,
    Annot,
    Edit,
    Search,
    Selection,
    Hidden,
    AddingTextAnnot,
    Empty
}
