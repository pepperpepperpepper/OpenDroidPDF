package org.opendroidpdf.app.content;

import org.opendroidpdf.app.reader.PageState;

/**
 * Bridges preference application into PageState and triggers overlay invalidation.
 */
public final class PagePrefHost implements PagePreferenceUpdater.Host {
    private final PageState pageState;
    private final Runnable overlayInvalidator;

    public PagePrefHost(PageState pageState, Runnable overlayInvalidator) {
        this.pageState = pageState;
        this.overlayInvalidator = overlayInvalidator;
    }

    @Override public void setInkColor(int color) { pageState.setInkColor(color); }
    @Override public void setEraserColor(int color) { pageState.setEraserColor(color); }
    @Override public void setInkThickness(float px) { pageState.setInkThickness(px); }
    @Override public void setEraserThickness(float px) { pageState.setEraserThickness(px); }
    @Override public void invalidateOverlay() { overlayInvalidator.run(); }
}
