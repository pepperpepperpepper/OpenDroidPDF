package org.opendroidpdf.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Draws a lightweight, in-overlay preview of ink strokes while the user is
 * directly manipulating (move/resize) an ink annotation selection box.
 */
final class InkDragPreviewRenderer {
    private final Paint strokePaint = new Paint();
    private final Path path = new Path();

    InkDragPreviewRenderer() {
        strokePaint.setAntiAlias(true);
        strokePaint.setDither(true);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(0xCC000000); // slightly translucent black
    }

    void draw(@NonNull Canvas canvas, float scale, @Nullable InkDragPreviewOverlay overlay) {
        if (overlay == null) return;
        if (scale <= 0f) return;
        if (overlay.arcsDoc == null || overlay.arcsDoc.length == 0) return;

        RectF start = overlay.startBoundsDoc;
        RectF cur = overlay.currentBoundsDoc;
        if (start == null || cur == null) return;

        float sw = start.width();
        float sh = start.height();
        float dw = cur.width();
        float dh = cur.height();
        if (sw <= 0f || sh <= 0f || dw <= 0f || dh <= 0f) return;

        float sx = dw / sw;
        float sy = dh / sh;

        int color = overlay.color;
        if ((color >>> 24) == 0) color = 0xCC000000;
        strokePaint.setColor(color);

        float thicknessDoc = overlay.thicknessDoc;
        if (!Float.isFinite(thicknessDoc) || thicknessDoc <= 0f) thicknessDoc = 2.5f;
        strokePaint.setStrokeWidth(Math.max(1f, thicknessDoc * scale));

        for (PointF[] stroke : overlay.arcsDoc) {
            if (stroke == null || stroke.length < 2) continue;
            path.reset();

            boolean started = false;
            for (PointF p : stroke) {
                if (p == null) continue;
                float xDoc = cur.left + ((p.x - start.left) * sx);
                float yDoc = cur.top + ((p.y - start.top) * sy);
                if (!Float.isFinite(xDoc) || !Float.isFinite(yDoc)) continue;
                float x = xDoc * scale;
                float y = yDoc * scale;
                if (!started) {
                    path.moveTo(x, y);
                    started = true;
                } else {
                    path.lineTo(x, y);
                }
            }
            if (started && !canvas.quickReject(path, Canvas.EdgeType.AA)) {
                canvas.drawPath(path, strokePaint);
            }
        }
        path.reset();
    }
}

