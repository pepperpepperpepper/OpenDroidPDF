package org.opendroidpdf.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;

/**
 * Renders the live eraser indicator (inner/outer circles) on the overlay.
 */
public final class EraserRenderer {
    public void draw(Canvas canvas,
                     float scale,
                     PointF eraserPoint,
                     float eraserThickness,
                     Paint innerPaint,
                     Paint outerPaint) {
        if (eraserPoint == null) return;
        float r = eraserThickness * scale;
        float cx = eraserPoint.x * scale;
        float cy = eraserPoint.y * scale;
        canvas.drawCircle(cx, cy, r, innerPaint);
        canvas.drawCircle(cx, cy, r, outerPaint);
    }
}

