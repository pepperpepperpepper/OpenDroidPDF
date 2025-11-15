package com.cgogolin.penandpdf;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Simple preview widget that renders a horizontal stroke using the current pen settings.
 */
public class PenStrokePreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float strokeWidthDocUnits = 1.3f;

    public PenStrokePreviewView(Context context) {
        super(context);
        init(null);
    }

    public PenStrokePreviewView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public PenStrokePreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.BLACK);

        if (attrs != null) {
            // Allow overriding the default color via XML if ever required.
            TypedArray a = getContext().obtainStyledAttributes(attrs, new int[]{android.R.attr.color});
            try {
                int color = a.getColor(0, paint.getColor());
                paint.setColor(color);
            } finally {
                a.recycle();
            }
        }
        updatePaintStrokeWidth();
    }

    /**
     * Sets the stroke width to preview, expressed in document units (same as ink thickness pref).
     */
    public void setStrokeWidthDocUnits(float strokeWidthDocUnits) {
        if (strokeWidthDocUnits <= 0) {
            strokeWidthDocUnits = 0.1f;
        }
        this.strokeWidthDocUnits = strokeWidthDocUnits;
        updatePaintStrokeWidth();
        invalidate();
    }

    public void setStrokeColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    private void updatePaintStrokeWidth() {
        float density = getResources().getDisplayMetrics().density;
        float px = Math.max(1f, strokeWidthDocUnits * density * 1.5f);
        paint.setStrokeWidth(px);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float startX = getPaddingLeft();
        float endX = getWidth() - getPaddingRight();
        float centerY = getHeight() / 2f;
        canvas.drawLine(startX, centerY, endX, centerY, paint);
    }
}
