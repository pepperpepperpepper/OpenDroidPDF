package org.opendroidpdf.app.overlay;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Draws the rectangle around the currently selected annotation/item.
 */
public final class ItemSelectionRenderer {
    public void draw(Canvas canvas, Resources res, float scale, RectF itemBox, boolean showHandles, Paint paint) {
        if (canvas == null || itemBox == null || paint == null) return;
        if (scale <= 0f) return;

        float left = itemBox.left * scale;
        float top = itemBox.top * scale;
        float right = itemBox.right * scale;
        float bottom = itemBox.bottom * scale;

        canvas.drawRect(left, top, right, bottom, paint);

        if (!showHandles || res == null) return;
        final float half = ItemSelectionHandles.handleHalfPx(res);

        Paint handleFill = new Paint(paint);
        handleFill.setStyle(Paint.Style.FILL);
        handleFill.setStrokeWidth(0f);
        handleFill.setAntiAlias(true);

        // Corners.
        canvas.drawRect(left - half, top - half, left + half, top + half, handleFill);
        canvas.drawRect(right - half, top - half, right + half, top + half, handleFill);
        canvas.drawRect(left - half, bottom - half, left + half, bottom + half, handleFill);
        canvas.drawRect(right - half, bottom - half, right + half, bottom + half, handleFill);
    }
}
