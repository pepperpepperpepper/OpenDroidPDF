package org.opendroidpdf.app.preferences;

import org.opendroidpdf.app.reader.PagingAxis;
import org.opendroidpdf.app.reader.ScrollMode;
import org.opendroidpdf.app.reader.FlingMomentum;

/** Immutable snapshot of viewer/navigation preferences. */
public final class ViewerPrefsSnapshot {
    public final boolean useStylus;
    public final boolean fitWidth;
    public final ScrollMode scrollMode;
    public final PagingAxis pagingAxis;
    public final FlingMomentum flingMomentum;
    public final boolean nightMode;

    public ViewerPrefsSnapshot(boolean useStylus,
                               boolean fitWidth,
                               ScrollMode scrollMode,
                               PagingAxis pagingAxis,
                               FlingMomentum flingMomentum,
                               boolean nightMode) {
        this.useStylus = useStylus;
        this.fitWidth = fitWidth;
        this.scrollMode = scrollMode != null ? scrollMode : ScrollMode.CONTINUOUS;
        this.pagingAxis = pagingAxis != null ? pagingAxis : PagingAxis.VERTICAL;
        this.flingMomentum = flingMomentum != null ? flingMomentum : FlingMomentum.NORMAL;
        this.nightMode = nightMode;
    }
}
