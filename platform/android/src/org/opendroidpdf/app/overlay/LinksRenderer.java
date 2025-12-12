package org.opendroidpdf.app.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;

import org.opendroidpdf.LinkInfo;

public final class LinksRenderer {
    public void draw(Canvas canvas, float scale, LinkInfo[] links, Paint paint) {
        if (links == null) return;
        for (LinkInfo link : links) {
            canvas.drawRect(link.rect.left * scale,
                    link.rect.top * scale,
                    link.rect.right * scale,
                    link.rect.bottom * scale,
                    paint);
        }
    }
}

