package org.opendroidpdf;

import android.graphics.Bitmap;
import android.graphics.Rect;

// PatchInfo describes a render request/response for a page patch.
public class PatchInfo {
    public final Rect viewArea;
    public final Rect patchArea;
    public final boolean completeRedraw;
    public final Bitmap patchBm;
    public final boolean intersects;
    public final boolean areaChanged;

    public PatchInfo(Rect viewArea, Bitmap patchBm, Rect previousArea, boolean update) {
        this.viewArea = viewArea;
        Rect rect = new Rect(0, 0, patchBm.getWidth(), patchBm.getHeight());
        intersects = rect.intersect(viewArea);
        rect.offset(-viewArea.left, -viewArea.top);
        patchArea = rect;
        areaChanged = previousArea == null || !viewArea.equals(previousArea);
        completeRedraw = areaChanged || !update;
        this.patchBm = patchBm;
    }
}

