package org.opendroidpdf.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.Nullable;

/**
 * Renders AcroForm widget bounds on the page overlay to make form fields discoverable.
 */
public final class WidgetAreasRenderer {
    public void draw(Canvas canvas, float scale, @Nullable RectF[] areas, Paint paint) {
        if (canvas == null || paint == null || areas == null || areas.length == 0) return;
        for (RectF r : areas) {
            if (r == null) continue;
            canvas.drawRect(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale, paint);
        }
    }
}

