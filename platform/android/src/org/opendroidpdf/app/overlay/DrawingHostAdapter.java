package org.opendroidpdf.app.overlay;

import android.view.View;

import org.opendroidpdf.PageView;
import org.opendroidpdf.DrawingController;

/**
 * Bridges DrawingController back into PageView without keeping the host logic inline.
 */
public final class DrawingHostAdapter implements DrawingController.Host {
    private final PageView pageView;

    public DrawingHostAdapter(PageView pageView) {
        this.pageView = pageView;
    }

    @Override
    public float scale() {
        return pageView.getScale();
    }

    @Override
    public int viewLeft() {
        return pageView.getLeft();
    }

    @Override
    public int viewTop() {
        return pageView.getTop();
    }

    @Override
    public View overlayView() {
        return pageView.getOverlayView();
    }

    @Override
    public void invalidateAll() {
        pageView.invalidateOverlay();
    }
}
