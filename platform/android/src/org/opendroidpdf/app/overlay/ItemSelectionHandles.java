package org.opendroidpdf.app.overlay;

import android.content.res.Resources;
import android.graphics.RectF;

import androidx.annotation.Nullable;

/**
 * Shared sizing + hit-testing helpers for the annotation selection box handles.
 *
 * <p>Handles are rendered at a fixed on-screen size (dp) so they remain usable at any zoom level.</p>
 */
public final class ItemSelectionHandles {
    private static final float HANDLE_HALF_DP = 8f; // 16dp square
    private static final float MIN_EDGE_DP = 24f;

    public enum Handle {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        NONE
    }

    private ItemSelectionHandles() {}

    public static float handleHalfPx(Resources res) {
        return HANDLE_HALF_DP * res.getDisplayMetrics().density;
    }

    public static float minEdgePx(Resources res) {
        return MIN_EDGE_DP * res.getDisplayMetrics().density;
    }

    @Nullable
    public static RectF handleRectDoc(Resources res, float scale, RectF itemBoxDoc, Handle handle) {
        if (res == null || itemBoxDoc == null) return null;
        if (scale <= 0f) return null;
        if (handle == null || handle == Handle.NONE) return null;

        float halfDoc = handleHalfPx(res) / scale;
        float cx;
        float cy;
        switch (handle) {
            case TOP_LEFT:
                cx = itemBoxDoc.left;
                cy = itemBoxDoc.top;
                break;
            case TOP_RIGHT:
                cx = itemBoxDoc.right;
                cy = itemBoxDoc.top;
                break;
            case BOTTOM_LEFT:
                cx = itemBoxDoc.left;
                cy = itemBoxDoc.bottom;
                break;
            case BOTTOM_RIGHT:
                cx = itemBoxDoc.right;
                cy = itemBoxDoc.bottom;
                break;
            default:
                return null;
        }
        return new RectF(cx - halfDoc, cy - halfDoc, cx + halfDoc, cy + halfDoc);
    }

    public static Handle hitTestHandle(Resources res, float scale, RectF itemBoxDoc, float docX, float docY) {
        if (res == null || itemBoxDoc == null) return Handle.NONE;
        if (scale <= 0f) return Handle.NONE;

        RectF hit;
        hit = handleRectDoc(res, scale, itemBoxDoc, Handle.TOP_LEFT);
        if (hit != null && hit.contains(docX, docY)) return Handle.TOP_LEFT;
        hit = handleRectDoc(res, scale, itemBoxDoc, Handle.TOP_RIGHT);
        if (hit != null && hit.contains(docX, docY)) return Handle.TOP_RIGHT;
        hit = handleRectDoc(res, scale, itemBoxDoc, Handle.BOTTOM_LEFT);
        if (hit != null && hit.contains(docX, docY)) return Handle.BOTTOM_LEFT;
        hit = handleRectDoc(res, scale, itemBoxDoc, Handle.BOTTOM_RIGHT);
        if (hit != null && hit.contains(docX, docY)) return Handle.BOTTOM_RIGHT;
        return Handle.NONE;
    }
}

