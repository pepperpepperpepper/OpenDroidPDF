package org.opendroidpdf.app.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.view.View;

import org.opendroidpdf.SearchResult;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.app.annotation.DrawingRenderer;
import org.opendroidpdf.app.preferences.EditorPreferences;
import org.opendroidpdf.DrawingController;
import org.opendroidpdf.LinkInfo;
import android.graphics.RectF;
import org.opendroidpdf.app.fillsign.FillSignPlacementOverlay;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import androidx.annotation.Nullable;

/**
 * Standalone overlay view that renders search results, links, selection,
 * in‑progress drawing, and eraser indicator.
 */
public class PageOverlayView extends View {

    public interface Host {
        boolean isBlank();
        int getPageNumber();
        float getScale();
        boolean isLinkHighlightingEnabled();
        LinkInfo[] getLinks();
        SearchResult getSearchResult();
        TextWord[][] getText();
        RectF getSelectBox();
        RectF getItemSelectBox();
        @Nullable RectF[] getWidgetAreas();
        boolean showWidgetAreas();
        float getDocRelXmin();
        float getDocRelXmax();
        int getViewWidth();
        int getViewHeight();
        int viewLeft();
        int viewTop();
        RectF getLeftMarkerRect();
        RectF getRightMarkerRect();
        boolean showItemSelectionHandles();
        boolean showItemResizeHandles();
        @Nullable String getItemDragPreviewText();
        @Nullable FillSignPlacementOverlay getFillSignPlacementOverlay();
    }

    private final Host host;
    private final DrawingController drawingController;
    private final EditorPreferences editorPrefs;
    @Nullable private SidecarAnnotationProvider sidecarAnnotations;

    private final OverlayPaints paints = new OverlayPaints();
    private final DrawingRenderer drawingRenderer = new DrawingRenderer();
    private final SidecarAnnotationRenderer sidecarRenderer = new SidecarAnnotationRenderer();
    private final FillSignPlacementRenderer fillSignPlacementRenderer = new FillSignPlacementRenderer();
    private final SearchRenderer searchRenderer = new SearchRenderer();
    private final LinksRenderer linksRenderer = new LinksRenderer();
    private final WidgetAreasRenderer widgetAreasRenderer = new WidgetAreasRenderer();
    private final SelectionRenderer selectionRenderer = new SelectionRenderer();
    private final ItemSelectionRenderer itemSelectionRenderer = new ItemSelectionRenderer();
    private final TextDragPreviewRenderer textDragPreviewRenderer = new TextDragPreviewRenderer();
    private final EraserRenderer eraserRenderer = new EraserRenderer();

    public PageOverlayView(Context context,
                           Host host,
                           DrawingController drawingController,
                           EditorPreferences editorPrefs,
                           @Nullable SidecarAnnotationProvider sidecarAnnotations) {
        super(context);
        this.host = host;
        this.drawingController = drawingController;
        this.editorPrefs = editorPrefs;
        this.sidecarAnnotations = sidecarAnnotations;
    }

    public void setSidecarAnnotations(@Nullable SidecarAnnotationProvider provider) {
        this.sidecarAnnotations = provider;
        invalidate();
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        if (host == null) return;

        // Move the canvas so that it covers the visible region
        canvas.translate(host.viewLeft(), host.viewTop());

        final float scale = host.getScale();

        if (!host.isBlank() && host.getSearchResult() != null) {
            searchRenderer.draw(canvas, scale, host.getSearchResult(), paints.searchResultPaint, paints.highlightedSearchResultPaint);
        }

        if (!host.isBlank() && host.getLinks() != null && host.isLinkHighlightingEnabled()) {
            linksRenderer.draw(canvas, scale, host.getLinks(), paints.linksPaint);
        }

        if (!host.isBlank() && host.showWidgetAreas()) {
            RectF[] areas = host.getWidgetAreas();
            if (areas != null && areas.length > 0) {
                widgetAreasRenderer.draw(canvas, scale, areas, paints.widgetAreasPaint);
            }
        }

        if (!host.isBlank() && host.getSelectBox() != null && host.getText() != null) {
            selectionRenderer.draw(canvas,
                    getResources(),
                    scale,
                    host.getText(),
                    host.getSelectBox(),
                    true, // smart selection resolved in text renderer; host bounds provided
                    host.getDocRelXmin(),
                    host.getDocRelXmax(),
                    paints.selectBoxPaint,
                    paints.selectMarkerPaint,
                    paints.selectOverlayPaint,
                    host.getLeftMarkerRect(),
                    host.getRightMarkerRect(),
                    host.getViewWidth(),
                    host.getViewHeight());
        }

        if (!host.isBlank()) {
            if (sidecarAnnotations != null) {
                sidecarRenderer.draw(canvas, scale, host.getPageNumber(), sidecarAnnotations);
            }
            drawDrawing(canvas, scale);

            FillSignPlacementOverlay fillSignOverlay = host.getFillSignPlacementOverlay();
            if (fillSignOverlay != null) {
                fillSignPlacementRenderer.draw(canvas, scale, fillSignOverlay, paints.itemSelectBoxPaint);
            }

            // While dragging/resizing a text annotation, draw a lightweight preview of the text
            // at the current selection box position so it feels like the annotation is moving.
            textDragPreviewRenderer.draw(
                    canvas,
                    getResources(),
                    scale,
                    host.getItemSelectBox(),
                    host.getItemDragPreviewText());

            // Always draw selection on top so handles remain visible even on busy pages.
            itemSelectionRenderer.draw(
                    canvas,
                    getResources(),
                    scale,
                    host.getItemSelectBox(),
                    host.showItemSelectionHandles(),
                    host.showItemResizeHandles(),
                    paints.itemSelectBoxPaint);

            PointF eraserPoint = drawingController.getEraser();
            if (eraserPoint != null) {
                final float eraser = editorPrefs.getEraserThickness();
                eraserRenderer.draw(canvas, scale, eraserPoint, eraser, paints.eraserInnerPaint, paints.eraserOuterPaint);
            }
        }
    }

    public void drawDrawing(Canvas canvas, float scale) {
        final float thickness = editorPrefs.getInkThickness();
        final int color = editorPrefs.getInkColorHex();
        drawingRenderer.draw(canvas, scale, drawingController, thickness, color);
    }
}
