package org.opendroidpdf.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.Nullable;

public final class ReadAloudHighlightRenderer {
    public void draw(Canvas canvas, float scale, @Nullable RectF[] boxes, Paint paint) {
        if (boxes == null || boxes.length == 0) return;
        for (RectF rect : boxes) {
            if (rect == null) continue;
            canvas.drawRect(
                    rect.left * scale,
                    rect.top * scale,
                    rect.right * scale,
                    rect.bottom * scale,
                    paint);
        }
    }
}

