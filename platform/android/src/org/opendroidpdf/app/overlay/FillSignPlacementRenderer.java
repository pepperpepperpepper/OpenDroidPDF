package org.opendroidpdf.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.fillsign.FillSignPlacementOverlay;

/**
 * Renders an in-progress Fill & Sign placement (signature/initials/stamps) on the page overlay.
 */
final class FillSignPlacementRenderer {
    private final Paint strokePaint = new Paint();
    private final Path path = new Path();

    FillSignPlacementRenderer() {
        strokePaint.setAntiAlias(true);
        strokePaint.setDither(true);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(0xFF000000);
    }

    void draw(@NonNull Canvas canvas,
              float scale,
              @Nullable FillSignPlacementOverlay overlay,
              @NonNull Paint boundsPaint) {
        if (overlay == null) return;
        if (overlay.arcsDoc == null || overlay.arcsDoc.length == 0) return;

        // Draw bounding rect.
        RectF b = overlay.boundsDoc;
        if (b != null) {
            canvas.drawRect(b.left * scale, b.top * scale, b.right * scale, b.bottom * scale, boundsPaint);
        }

        // Render signature strokes.
        float thickness = Math.max(1f, 2.5f * scale);
        strokePaint.setStrokeWidth(thickness);

        for (PointF[] stroke : overlay.arcsDoc) {
            if (stroke == null || stroke.length < 2) continue;
            path.reset();
            PointF p0 = stroke[0];
            if (p0 == null) continue;
            path.moveTo(p0.x * scale, p0.y * scale);
            for (int i = 1; i < stroke.length; i++) {
                PointF p = stroke[i];
                if (p == null) continue;
                path.lineTo(p.x * scale, p.y * scale);
            }
            if (!canvas.quickReject(path, Canvas.EdgeType.AA)) {
                canvas.drawPath(path, strokePaint);
            }
        }
        path.reset();
    }
}

