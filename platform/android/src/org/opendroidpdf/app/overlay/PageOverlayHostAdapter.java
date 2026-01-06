package org.opendroidpdf.app.overlay;

import android.graphics.RectF;

import org.opendroidpdf.LinkInfo;
import org.opendroidpdf.SearchResult;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.app.reader.PageState;
import org.opendroidpdf.app.fillsign.FillSignPlacementOverlay;

import androidx.annotation.Nullable;

/**
 * Bridges PageOverlayView.Host back to a lightweight PageView host interface.
 */
public final class PageOverlayHostAdapter implements PageOverlayView.Host {
    public interface Host {
        boolean isBlank();
        float scale();
        boolean isLinkHighlightingEnabled();
        LinkInfo[] links();
        SearchResult searchResult();
        TextWord[][] text();
        RectF selectBox();
        RectF itemSelectBox();
        @Nullable RectF[] widgetAreas();
        boolean showWidgetAreas();
        int viewWidth();
        int viewHeight();
        int viewLeft();
        int viewTop();
        RectF leftMarkerRect();
        RectF rightMarkerRect();
        boolean showItemSelectionHandles();
        boolean showItemResizeHandles();
        @Nullable String itemDragPreviewText();
        @Nullable FillSignPlacementOverlay fillSignPlacementOverlay();
    }

    private final Host host;
    private final PageState pageState;

    public PageOverlayHostAdapter(Host host, PageState pageState) {
        this.host = host;
        this.pageState = pageState;
    }

    @Override public boolean isBlank() { return host.isBlank(); }
    @Override public int getPageNumber() { return pageState.getPageNumber(); }
    @Override public float getScale() { return host.scale(); }
    @Override public boolean isLinkHighlightingEnabled() { return host.isLinkHighlightingEnabled(); }
    @Override public LinkInfo[] getLinks() { return host.links(); }
    @Override public SearchResult getSearchResult() { return host.searchResult(); }
    @Override public TextWord[][] getText() { return host.text(); }
    @Override public RectF getSelectBox() { return host.selectBox(); }
    @Override public RectF getItemSelectBox() { return host.itemSelectBox(); }
    @Nullable @Override public RectF[] getWidgetAreas() { return host.widgetAreas(); }
    @Override public boolean showWidgetAreas() { return host.showWidgetAreas(); }
    @Override public float getDocRelXmin() { return pageState.getDocRelXmin(); }
    @Override public float getDocRelXmax() { return pageState.getDocRelXmax(); }
    @Override public int getViewWidth() { return host.viewWidth(); }
    @Override public int getViewHeight() { return host.viewHeight(); }
    @Override public int viewLeft() { return host.viewLeft(); }
    @Override public int viewTop() { return host.viewTop(); }
    @Override public RectF getLeftMarkerRect() { return host.leftMarkerRect(); }
    @Override public RectF getRightMarkerRect() { return host.rightMarkerRect(); }
    @Override public boolean showItemSelectionHandles() { return host.showItemSelectionHandles(); }
    @Override public boolean showItemResizeHandles() { return host.showItemResizeHandles(); }
    @Nullable @Override public String getItemDragPreviewText() { return host.itemDragPreviewText(); }
    @Nullable @Override public FillSignPlacementOverlay getFillSignPlacementOverlay() { return host.fillSignPlacementOverlay(); }
}
