package org.opendroidpdf.app.overlay;

import android.graphics.Paint;

/**
 * Holds configured Paint instances for overlay rendering. Keeps the
 * paint-setup boilerplate out of PageView. Colors and styles mirror the
 * previous inlined values.
 */
public final class OverlayPaints {
    // Colors copied from PageView overlay constants
    private static final int SELECTION_COLOR = 0x8033B5E5;
    private static final int SELECTION_MARKER_COLOR = 0xFF33B5E5;
    private static final int SEARCHRESULTS_COLOR = 0x3033B5E5;
    private static final int HIGHLIGHTED_SEARCHRESULT_COLOR = 0xFF33B5E5;
    private static final int LINK_COLOR = 0xFF33B5E5;
    private static final int BOX_COLOR = 0xFF33B5E5;
    private static final int ERASER_INNER_COLOR = 0xFFFFFFFF;
    private static final int ERASER_OUTER_COLOR = 0xFF000000;

    public final Paint searchResultPaint = new Paint();
    public final Paint highlightedSearchResultPaint = new Paint();
    public final Paint linksPaint = new Paint();
    public final Paint selectBoxPaint = new Paint();
    public final Paint selectMarkerPaint = new Paint();
    public final Paint selectOverlayPaint = new Paint();
    public final Paint itemSelectBoxPaint = new Paint();
    public final Paint drawingPaint = new Paint();
    public final Paint eraserInnerPaint = new Paint();
    public final Paint eraserOuterPaint = new Paint();

    public OverlayPaints() {
        searchResultPaint.setColor(SEARCHRESULTS_COLOR);

        highlightedSearchResultPaint.setColor(HIGHLIGHTED_SEARCHRESULT_COLOR);
        highlightedSearchResultPaint.setStyle(Paint.Style.STROKE);
        highlightedSearchResultPaint.setAntiAlias(true);

        linksPaint.setColor(LINK_COLOR);
        linksPaint.setStyle(Paint.Style.STROKE);
        linksPaint.setStrokeWidth(0);

        selectBoxPaint.setColor(SELECTION_COLOR);
        selectBoxPaint.setStyle(Paint.Style.FILL);
        selectBoxPaint.setStrokeWidth(0);

        selectMarkerPaint.setColor(SELECTION_MARKER_COLOR);
        selectMarkerPaint.setStyle(Paint.Style.FILL);
        selectMarkerPaint.setStrokeWidth(0);

        selectOverlayPaint.setColor(0x30000000); // gray overlay used for selection
        selectOverlayPaint.setStyle(Paint.Style.FILL);

        itemSelectBoxPaint.setColor(BOX_COLOR);
        itemSelectBoxPaint.setStyle(Paint.Style.STROKE);
        itemSelectBoxPaint.setStrokeWidth(3);

        drawingPaint.setAntiAlias(true);
        drawingPaint.setDither(true);
        drawingPaint.setStrokeJoin(Paint.Join.ROUND);
        drawingPaint.setStrokeCap(Paint.Cap.ROUND);
        drawingPaint.setStyle(Paint.Style.STROKE);

        eraserInnerPaint.setAntiAlias(true);
        eraserInnerPaint.setDither(true);
        eraserInnerPaint.setStyle(Paint.Style.FILL);
        eraserInnerPaint.setColor(ERASER_INNER_COLOR);

        eraserOuterPaint.setAntiAlias(true);
        eraserOuterPaint.setDither(true);
        eraserOuterPaint.setStyle(Paint.Style.STROKE);
        eraserOuterPaint.setColor(ERASER_OUTER_COLOR);
    }
}

