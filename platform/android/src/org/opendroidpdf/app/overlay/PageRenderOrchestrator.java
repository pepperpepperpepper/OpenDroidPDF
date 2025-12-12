package org.opendroidpdf.app.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import org.opendroidpdf.PatchInfo;

public final class PageRenderOrchestrator {
    private PageRenderOrchestrator() {}

    public static PagePatchView ensureAndRender(
            Context context,
            ViewGroup parent,
            PagePatchView current,
            Rect viewArea,
            Bitmap bitmap,
            boolean update,
            PagePatchView.Host host,
            View overlayToFront) {
        PatchInfo patchInfo = new PatchInfo(viewArea, bitmap, current == null ? null : current.getArea(), update);
        if (!patchInfo.intersects) return current;
        if (!patchInfo.areaChanged && !update) return current;
        if (current == null) {
            current = new PagePatchView(context, host);
            parent.addView(current);
            if (overlayToFront != null) overlayToFront.bringToFront();
        }
        current.renderInBackground(patchInfo);
        return current;
    }

    /**
     * If an existing hi‑res patch still matches the current container size, lay it out and
     * make it visible; otherwise hide and reset it so a fresh patch can be rendered.
     *
     * Returns true if the patch remains and was laid out; false if it was discarded.
     */
    public static boolean layoutOrDiscardHq(PagePatchView hqView, int containerWidth, int containerHeight) {
        if (hqView == null) return false;
        android.graphics.Rect area = hqView.getArea();
        android.graphics.Rect patch = hqView.getPatchArea();
        if (area == null || patch == null || area.width() != containerWidth || area.height() != containerHeight) {
            hqView.setVisibility(View.GONE);
            hqView.reset();
            return false;
        }
        hqView.layout(patch.left, patch.top, patch.right, patch.bottom);
        hqView.setVisibility(View.VISIBLE);
        return true;
    }
}
