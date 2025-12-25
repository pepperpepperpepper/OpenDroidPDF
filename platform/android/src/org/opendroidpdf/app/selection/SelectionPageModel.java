package org.opendroidpdf.app.selection;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.TextWord;

/**
 * Minimal surface area needed by selection routing.
 *
 * This exists to avoid external classes reading internal MuPDFPageView/PageView fields
 * (like mAnnotations/mPageNumber/mParent) and to make selection ownership explicit.
 */
public interface SelectionPageModel {
    Annotation[] annotations();
    int pageNumber();
    int pageCount();
    /** Encoded MuPDF reflow {@code fz_location} for the current page, or {@code -1} when unsupported. */
    long reflowLocation();

    void requestFullRedrawAfterNextAnnotationLoad();
    void loadAnnotations();
    void discardRenderedPage();
    void redraw(boolean updateHq);
    void setModeDrawing();

    /** Full page text (for anchors/reflow highlight re-derivation). */
    TextWord[][] textLines();

    void processSelectedText(TextProcessor processor);
    void deselectText();
    void setDraw(PointF[][] arcs);
    Context getContext();

    void setSelectionBox(RectF rect);

    /** Refresh toolbar undo enablement after non-ink annotation operations (sidecar highlights/notes). */
    void refreshUndoState();
}
