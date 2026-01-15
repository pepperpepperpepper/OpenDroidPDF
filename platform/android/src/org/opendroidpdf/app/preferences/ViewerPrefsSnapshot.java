package org.opendroidpdf.app.preferences;

import org.opendroidpdf.app.reader.PagingAxis;

/** Immutable snapshot of viewer/navigation preferences. */
public final class ViewerPrefsSnapshot {
    public final boolean useStylus;
    public final boolean fitWidth;
    public final PagingAxis pagingAxis;

    public ViewerPrefsSnapshot(boolean useStylus, boolean fitWidth, PagingAxis pagingAxis) {
        this.useStylus = useStylus;
        this.fitWidth = fitWidth;
        this.pagingAxis = pagingAxis != null ? pagingAxis : PagingAxis.HORIZONTAL;
    }
}
