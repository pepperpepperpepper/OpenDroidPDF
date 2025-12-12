package org.opendroidpdf.app.reader;

import android.graphics.Bitmap;

/**
 * Alternates between two reusable HQ bitmaps sized to the container.
 */
public final class HqBitmapPool {
    private Bitmap bm1;
    private Bitmap bm2;

    public Bitmap next(Bitmap currentBitmap, boolean update, int width, int height) {
        if (currentBitmap == null || (currentBitmap == bm2 && !update) || (currentBitmap == bm1 && update)) {
            bm1 = ensureSize(bm1, width, height);
            return bm1;
        } else {
            bm2 = ensureSize(bm2, width, height);
            return bm2;
        }
    }

    private Bitmap ensureSize(Bitmap b, int width, int height) {
        if (b == null || b.getWidth() != width || b.getHeight() != height) {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        return b;
    }
}
