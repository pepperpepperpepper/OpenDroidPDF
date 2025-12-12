package org.opendroidpdf.app.overlay;

import android.graphics.RectF;

/**
 * Encapsulates selection-handle hit testing and movement logic so PageView can shrink.
 */
public final class SelectionController {

    public interface Host {
        float scale();
        int viewLeft();
        int viewTop();
        RectF getSelectBox();
        void setSelectBox(RectF box);
        RectF getLeftMarkerRect();
        RectF getRightMarkerRect();
        void updateDocRelXBounds(float docRelX);
        void invalidateOverlay();
    }

    private final Host host;

    public SelectionController(Host host) {
        this.host = host;
    }

    public boolean hitsLeftMarker(float x, float y) {
        float s = host.scale();
        float docRelX = (x - host.viewLeft())/s;
        float docRelY = (y - host.viewTop())/s;
        RectF left = host.getLeftMarkerRect();
        return left != null && left.contains(docRelX, docRelY);
    }

    public boolean hitsRightMarker(float x, float y) {
        float s = host.scale();
        float docRelX = (x - host.viewLeft())/s;
        float docRelY = (y - host.viewTop())/s;
        RectF right = host.getRightMarkerRect();
        return right != null && right.contains(docRelX, docRelY);
    }

    public void moveLeftMarker(float x, float y) {
        RectF box = host.getSelectBox();
        if (box == null) return;
        float s = host.scale();
        float docRelX = (x - host.viewLeft())/s;
        float docRelY = (y - host.viewTop())/s;

        box.left = docRelX;
        if (docRelY < box.bottom) {
            box.top = docRelY;
        } else {
            box.top = box.bottom;
            box.bottom = docRelY;
        }
        host.updateDocRelXBounds(docRelX);
        host.invalidateOverlay();
    }

    public void moveRightMarker(float x, float y) {
        RectF box = host.getSelectBox();
        if (box == null) return;
        float s = host.scale();
        float docRelX = (x - host.viewLeft())/s;
        float docRelY = (y - host.viewTop())/s;

        box.right = docRelX;
        if (docRelY > box.top) {
            box.bottom = docRelY;
        } else {
            box.bottom = box.top;
            box.top = docRelY;
        }
        host.updateDocRelXBounds(docRelX);
        host.invalidateOverlay();
    }

    /**
     * Create/update the selection box from view-space coordinates, converting
     * to document-relative coords and updating horizontal bounds used by smart
     * text selection.
     */
    public void setSelectionFromViewRect(float x0, float y0, float x1, float y1) {
        float s = host.scale();
        float docRelX0 = (x0 - host.viewLeft())/s;
        float docRelY0 = (y0 - host.viewTop())/s;
        float docRelX1 = (x1 - host.viewLeft())/s;
        float docRelY1 = (y1 - host.viewTop())/s;

        RectF box = host.getSelectBox();
        if (box == null) box = new RectF();

        if (docRelY0 <= docRelY1)
            box.set(docRelX0, docRelY0, docRelX1, docRelY1);
        else
            box.set(docRelX1, docRelY1, docRelX0, docRelY0);

        host.setSelectBox(box);
        host.updateDocRelXBounds(Math.max(docRelX0, docRelX1));
        host.updateDocRelXBounds(Math.min(docRelX0, docRelX1));
        host.invalidateOverlay();
    }
}
