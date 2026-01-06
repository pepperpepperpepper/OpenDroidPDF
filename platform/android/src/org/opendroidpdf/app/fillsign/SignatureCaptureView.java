package org.opendroidpdf.app.fillsign;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Simple in-app drawing surface to capture signature/initials as normalized strokes. */
public final class SignatureCaptureView extends View {
    private final Paint paint = new Paint();
    private final Path path = new Path();
    private final ArrayList<ArrayList<PointF>> strokes = new ArrayList<>();
    @Nullable private ArrayList<PointF> activeStroke;

    public SignatureCaptureView(Context context) { this(context, null); }

    public SignatureCaptureView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF000000);
        paint.setStrokeWidth(dp(context, 3f));
        setBackgroundColor(0xFFFFFFFF);
    }

    public void clear() {
        strokes.clear();
        activeStroke = null;
        invalidate();
    }

    public boolean hasInk() {
        for (ArrayList<PointF> s : strokes) {
            if (s != null && s.size() >= 2) return true;
        }
        return false;
    }

    @Nullable
    public SignatureTemplate buildTemplate() {
        RectF bounds = computeBounds();
        if (bounds == null) return null;
        float w = bounds.width();
        float h = bounds.height();
        if (w <= 1f || h <= 1f) return null;

        float aspectRatio = w / h;
        List<List<PointF>> normalized = new ArrayList<>();
        for (ArrayList<PointF> stroke : strokes) {
            if (stroke == null || stroke.size() < 2) continue;
            ArrayList<PointF> pts = new ArrayList<>(stroke.size());
            for (PointF p : stroke) {
                if (p == null) continue;
                float x = (p.x - bounds.left) / w;
                float y = (p.y - bounds.top) / h;
                if (!Float.isFinite(x) || !Float.isFinite(y)) continue;
                pts.add(new PointF(x, y));
            }
            if (pts.size() >= 2) normalized.add(pts);
        }
        if (normalized.isEmpty()) return null;
        return new SignatureTemplate(aspectRatio, normalized);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (ArrayList<PointF> stroke : strokes) {
            if (stroke == null || stroke.size() < 2) continue;
            path.reset();
            PointF first = stroke.get(0);
            path.moveTo(first.x, first.y);
            for (int i = 1; i < stroke.size(); i++) {
                PointF p = stroke.get(i);
                if (p == null) continue;
                path.lineTo(p.x, p.y);
            }
            canvas.drawPath(path, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) return false;
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                activeStroke = new ArrayList<>();
                strokes.add(activeStroke);
                addPoint(event.getX(), event.getY());
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int historySize = event.getHistorySize();
                for (int h = 0; h < historySize; h++) {
                    addPoint(event.getHistoricalX(h), event.getHistoricalY(h));
                }
                addPoint(event.getX(), event.getY());
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                addPoint(event.getX(), event.getY());
                activeStroke = null;
                invalidate();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void addPoint(float x, float y) {
        ArrayList<PointF> stroke = activeStroke;
        if (stroke == null) return;
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;
        stroke.add(new PointF(x, y));
        invalidate();
    }

    @Nullable
    private RectF computeBounds() {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        int count = 0;
        for (ArrayList<PointF> stroke : strokes) {
            if (stroke == null) continue;
            for (PointF p : stroke) {
                if (p == null) continue;
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
                count++;
            }
        }
        if (count < 2) return null;
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) return null;
        return new RectF(minX, minY, maxX, maxY);
    }

    private static float dp(@NonNull Context ctx, float dp) {
        return dp * ctx.getResources().getDisplayMetrics().density;
    }
}

