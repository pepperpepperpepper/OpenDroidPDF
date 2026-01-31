package org.opendroidpdf.app.reader;

/** Page layout / scroll mode for the document viewer. */
public enum ScrollMode {
    /** Pages are stacked vertically and scroll continuously. */
    CONTINUOUS("continuous"),
    /** Single-page paging behavior (swipe/fling to change pages). */
    PAGED("paged");

    public final String prefValue;

    ScrollMode(String prefValue) {
        this.prefValue = prefValue;
    }

    public static ScrollMode fromPrefValue(String value) {
        if (value == null) return CONTINUOUS;
        for (ScrollMode mode : values()) {
            if (mode.prefValue.equals(value)) return mode;
        }
        return CONTINUOUS;
    }
}
