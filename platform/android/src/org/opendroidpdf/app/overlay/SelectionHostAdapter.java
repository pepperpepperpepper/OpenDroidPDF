package org.opendroidpdf.app.overlay;

import android.graphics.RectF;

import org.opendroidpdf.PageView;
import org.opendroidpdf.app.reader.PageState;

/**
 * Bridges SelectionController back into PageView to keep PageView slim.
 */
public final class SelectionHostAdapter implements SelectionController.Host {
    private final PageView pageView;
    private final RectF leftMarkerRect;
    private final RectF rightMarkerRect;
    private final PageState pageState;

    public SelectionHostAdapter(PageView pageView,
                                RectF leftMarkerRect,
                                RectF rightMarkerRect,
                                PageState pageState) {
        this.pageView = pageView;
        this.leftMarkerRect = leftMarkerRect;
        this.rightMarkerRect = rightMarkerRect;
        this.pageState = pageState;
    }

    @Override
    public float scale() { return pageView.getScale(); }

    @Override
    public int viewLeft() { return pageView.getLeft(); }

    @Override
    public int viewTop() { return pageView.getTop(); }

    @Override
    public RectF getSelectBox() { return pageView.getSelectBox(); }

    @Override
    public RectF getLeftMarkerRect() { return leftMarkerRect; }

    @Override
    public RectF getRightMarkerRect() { return rightMarkerRect; }

    @Override
    public void setSelectBox(RectF box) { pageView.setSelectBox(box); }

    @Override
    public void updateDocRelXBounds(float docRelX) { pageState.updateDocRelXBounds(docRelX); }

    @Override
    public void invalidateOverlay() { pageView.invalidateOverlay(); }
}
