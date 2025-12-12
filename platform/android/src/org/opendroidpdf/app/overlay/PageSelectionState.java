package org.opendroidpdf.app.overlay;

import android.graphics.RectF;

import org.opendroidpdf.PageView;
import org.opendroidpdf.app.content.PageStateUpdater;
import org.opendroidpdf.app.reader.PageState;

/**
 * Owns selection state (boxes + marker rects) and routes selection gestures
 * through SelectionController so PageView can stay thin.
 */
public final class PageSelectionState {
    private RectF selectBox;
    private RectF itemSelectBox;
    private final RectF leftMarkerRect = new RectF();
    private final RectF rightMarkerRect = new RectF();
    private final SelectionController controller;
    private final PageState pageState;
    private final Runnable overlayInvalidator;

    public PageSelectionState(PageView pageView,
                              PageState pageState,
                              Runnable overlayInvalidator) {
        this.pageState = pageState;
        this.overlayInvalidator = overlayInvalidator;
        this.controller = new SelectionController(
                new SelectionHostAdapter(pageView, leftMarkerRect, rightMarkerRect, pageState));
    }

    public boolean hitsLeftMarker(float x, float y) { return controller.hitsLeftMarker(x, y); }
    public boolean hitsRightMarker(float x, float y) { return controller.hitsRightMarker(x, y); }
    public void moveLeftMarker(float x, float y) { controller.moveLeftMarker(x, y); }
    public void moveRightMarker(float x, float y) { controller.moveRightMarker(x, y); }

    public void selectFromViewRect(float x0, float y0, float x1, float y1) {
        controller.setSelectionFromViewRect(x0, y0, x1, y1);
    }

    public void deselect() {
        PageStateUpdater.resetSelection(pageState);
        selectBox = null;
        overlayInvalidator.run();
    }

    public boolean hasSelection() { return selectBox != null; }

    public RectF getSelectBox() { return selectBox; }
    public void setSelectBox(RectF box) { this.selectBox = box; }

    public RectF getItemSelectBox() { return itemSelectBox; }
    public void setItemSelectBox(RectF rect) {
        itemSelectBox = rect;
        overlayInvalidator.run();
    }

    public RectF getLeftMarkerRect() { return leftMarkerRect; }
    public RectF getRightMarkerRect() { return rightMarkerRect; }
}
