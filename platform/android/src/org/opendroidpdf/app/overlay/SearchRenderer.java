package org.opendroidpdf.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.opendroidpdf.SearchResult;

public final class SearchRenderer {
    public void draw(Canvas canvas, float scale, SearchResult result, Paint boxPaint, Paint highlightPaint) {
        if (result == null) return;
        RectF[] boxes = result.getSearchBoxes();
        if (boxes != null) {
            for (RectF rect : boxes) {
                canvas.drawRect(rect.left * scale, rect.top * scale,
                        rect.right * scale, rect.bottom * scale, boxPaint);
            }
        }
        RectF focus = result.getFocusedSearchBox();
        if (focus != null) {
            highlightPaint.setStrokeWidth(2 * scale);
            canvas.drawRect(focus.left * scale, focus.top * scale,
                    focus.right * scale, focus.bottom * scale, highlightPaint);
        }
    }
}

