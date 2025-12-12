package org.opendroidpdf.app.overlay;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

/**
 * Utility to draw the current in-progress stroke onto a target PagePatchView's bitmap,
 * preserving the previous pixels and updating the ImageView afterwards.
 */
public final class SaveDrawHelper {
    public interface Drawer {
        void draw(Canvas canvas, float scale);
    }

    private SaveDrawHelper() {}

    public static void drawOntoPatch(PagePatchView target,
                                     View hostView,
                                     float scale,
                                     Drawer drawer) {
        if (target == null || drawer == null) return;
        Bitmap bitmap = target.getImageBitmap();
        if (bitmap == null) return;
        Canvas canvas = new Canvas(bitmap);
        // Account for negative offsets so drawing aligns to the view origin
        int tx = Math.min(hostView.getLeft(), 0);
        int ty = Math.min(hostView.getTop(), 0);
        canvas.translate(tx, ty);
        drawer.draw(canvas, scale);
        target.setImageBitmap(bitmap);
    }
}

