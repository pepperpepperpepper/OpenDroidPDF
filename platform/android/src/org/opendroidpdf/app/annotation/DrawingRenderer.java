package org.opendroidpdf.app.annotation;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import java.util.ArrayList;
import java.util.Iterator;

import org.opendroidpdf.DrawingController;

/**
 * Renders in-progress ink drawing paths from DrawingController onto a Canvas.
 * Extracted from PageView.OverlayView to shrink PageView and keep drawing
 * concerns isolated.
 */
public final class DrawingRenderer {
    private final Paint drawingPaint = new Paint();
    private final Path drawingPath = new Path();

    public DrawingRenderer() {
        drawingPaint.setAntiAlias(true);
        drawingPaint.setDither(true);
        drawingPaint.setStrokeJoin(Paint.Join.ROUND);
        drawingPaint.setStrokeCap(Paint.Cap.ROUND);
        drawingPaint.setStyle(Paint.Style.STROKE);
    }

    public void draw(Canvas canvas,
                     float scale,
                     DrawingController controller,
                     float inkThickness,
                     int inkColor) {
        ArrayList<ArrayList<PointF>> drawing = controller.getDrawing();
        if (drawing == null) return;

        drawingPaint.setStrokeWidth(inkThickness * scale);
        drawingPaint.setColor(inkColor);

        Iterator<ArrayList<PointF>> it = drawing.iterator();
        while (it.hasNext()) {
            ArrayList<PointF> arc = it.next();
            if (arc.size() < 2) continue;
            Iterator<PointF> iit = arc.iterator();
            if (iit.hasNext()) {
                PointF p = iit.next();
                float x1 = p.x * scale;
                float y1 = p.y * scale;
                drawingPath.moveTo(x1, y1);
                while (iit.hasNext()) {
                    p = iit.next();
                    float x2 = p.x * scale;
                    float y2 = p.y * scale;
                    drawingPath.lineTo(x2, y2);
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 11 && canvas.isHardwareAccelerated()
                    && android.os.Build.VERSION.SDK_INT < 16) {
                canvas.drawPath(drawingPath, drawingPaint);
            } else if (!canvas.quickReject(drawingPath, Canvas.EdgeType.AA)) {
                canvas.drawPath(drawingPath, drawingPaint);
            }
            drawingPath.reset();
        }
    }
}

