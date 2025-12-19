package org.opendroidpdf.app.preferences;

/** Immutable snapshot of viewer/navigation preferences. */
public final class ViewerPrefsSnapshot {
    public final boolean useStylus;
    public final boolean fitWidth;

    public ViewerPrefsSnapshot(boolean useStylus, boolean fitWidth) {
        this.useStylus = useStylus;
        this.fitWidth = fitWidth;
    }
}

