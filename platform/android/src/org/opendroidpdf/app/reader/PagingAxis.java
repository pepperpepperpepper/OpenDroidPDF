package org.opendroidpdf.app.reader;

/**
 * Page-to-page navigation axis for ReaderView.
 *
 * <p>This does not change in-page pan/zoom (which remains 2D); it only affects how
 * neighboring pages are laid out and which fling direction triggers page switches.</p>
 */
public enum PagingAxis {
    HORIZONTAL("horizontal"),
    VERTICAL("vertical");

    public final String prefValue;

    PagingAxis(String prefValue) {
        this.prefValue = prefValue;
    }

    public static PagingAxis fromPrefValue(String value) {
        if (VERTICAL.prefValue.equals(value)) return VERTICAL;
        return HORIZONTAL;
    }
}

