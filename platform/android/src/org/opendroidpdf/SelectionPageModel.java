package org.opendroidpdf;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

/**
 * Minimal surface area needed by selection routing.
 *
 * This exists to avoid external classes reading internal MuPDFPageView/PageView fields
 * (like mAnnotations/mPageNumber/mParent) and to make selection ownership explicit.
 */
interface SelectionPageModel {
    Annotation[] annotations();
    int pageNumber();

    void requestFullRedrawAfterNextAnnotationLoad();
    void loadAnnotations();
    void discardRenderedPage();
    void redraw(boolean updateHq);
    void setModeDrawing();

    void processSelectedText(TextProcessor processor);
    void deselectText();
    void setDraw(PointF[][] arcs);
    Context getContext();

    void setSelectionBox(RectF rect);
}

