package org.opendroidpdf.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Draws the rectangle around the currently selected annotation/item.
 */
public final class ItemSelectionRenderer {
    public void draw(Canvas canvas, float scale, RectF itemBox, Paint paint) {
        if (itemBox == null) return;
        canvas.drawRect(itemBox.left * scale,
                        itemBox.top * scale,
                        itemBox.right * scale,
                        itemBox.bottom * scale,
                        paint);
    }
}

