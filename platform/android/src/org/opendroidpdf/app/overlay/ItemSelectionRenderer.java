package org.opendroidpdf.app.overlay;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Draws the rectangle around the currently selected annotation/item.
 */
public final class ItemSelectionRenderer {
    public void draw(Canvas canvas, Resources res, float scale, RectF itemBox, boolean showMoveHandle, boolean showResizeHandles, Paint paint) {
        if (canvas == null || itemBox == null || paint == null) return;
        if (scale <= 0f) return;

        float left = itemBox.left * scale;
        float top = itemBox.top * scale;
        float right = itemBox.right * scale;
        float bottom = itemBox.bottom * scale;

        canvas.drawRect(left, top, right, bottom, paint);

        if ((!showMoveHandle && !showResizeHandles) || res == null) return;
        final float cornerHalf = ItemSelectionHandles.cornerHandleHalfPx(res);
        final float moveHalf = ItemSelectionHandles.moveHandleHalfPx(res);

        Paint handleFill = new Paint(paint);
        handleFill.setStyle(Paint.Style.FILL);
        handleFill.setStrokeWidth(0f);
        handleFill.setAntiAlias(true);

        if (showResizeHandles) {
            // Corners.
            canvas.drawRect(left - cornerHalf, top - cornerHalf, left + cornerHalf, top + cornerHalf, handleFill);
            canvas.drawRect(right - cornerHalf, top - cornerHalf, right + cornerHalf, top + cornerHalf, handleFill);
            canvas.drawRect(left - cornerHalf, bottom - cornerHalf, left + cornerHalf, bottom + cornerHalf, handleFill);
            canvas.drawRect(right - cornerHalf, bottom - cornerHalf, right + cornerHalf, bottom + cornerHalf, handleFill);
        }

        // When resize handles are shown, hide the move handle to reduce accidental overlap and
        // avoid confusing affordances (move is still possible by dragging inside the box).
        if (showMoveHandle && !showResizeHandles) {
            // Move handle (top-center). Drag inside the box (or this handle) to move.
            final float cx = (left + right) * 0.5f;
            final float cy = top;
            canvas.drawCircle(cx, cy, moveHalf, handleFill);

            // "Drag grip" glyph: 3 horizontal bars. Intentionally not a "+" to avoid ambiguity.
            Paint glyph = new Paint(paint);
            glyph.setColor(0xFFFFFFFF);
            glyph.setStyle(Paint.Style.STROKE);
            glyph.setAntiAlias(true);

            final float barHalf = moveHalf * 0.42f;
            final float gap = moveHalf * 0.28f;
            final float stroke = Math.max(1.2f, moveHalf * 0.14f);
            glyph.setStrokeWidth(stroke);
            glyph.setStrokeCap(Paint.Cap.ROUND);
            for (int row = -1; row <= 1; row++) {
                float y = cy + row * gap;
                canvas.drawLine(cx - barHalf, y, cx + barHalf, y, glyph);
            }
        }
    }
}
