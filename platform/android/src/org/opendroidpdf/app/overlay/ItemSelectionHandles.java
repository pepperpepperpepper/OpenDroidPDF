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
    // Make resize (corner) handles a bit smaller than the move handle so accidental
    // resizes are less common when the user intends to drag-to-move.
    private static final float CORNER_HANDLE_HALF_DP = 6f; // 12dp square
    private static final float MOVE_HANDLE_HALF_DP = 8f; // 16dp square/circle
    private static final float MIN_EDGE_DP = 24f;

    public enum Handle {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        MOVE,
        NONE
    }

    private ItemSelectionHandles() {}

    public static float cornerHandleHalfPx(Resources res) {
        return CORNER_HANDLE_HALF_DP * res.getDisplayMetrics().density;
    }

    public static float moveHandleHalfPx(Resources res) {
        return MOVE_HANDLE_HALF_DP * res.getDisplayMetrics().density;
    }

    public static float minEdgePx(Resources res) {
        return MIN_EDGE_DP * res.getDisplayMetrics().density;
    }

    @Nullable
    public static RectF handleRectDoc(Resources res, float scale, RectF itemBoxDoc, Handle handle) {
        if (res == null || itemBoxDoc == null) return null;
        if (scale <= 0f) return null;
        if (handle == null || handle == Handle.NONE) return null;

        float halfPx = (handle == Handle.MOVE ? moveHandleHalfPx(res) : cornerHandleHalfPx(res));
        float halfDoc = halfPx / scale;
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
            case MOVE:
                cx = (itemBoxDoc.left + itemBoxDoc.right) * 0.5f;
                cy = itemBoxDoc.top;
                break;
            default:
                return null;
        }
        return new RectF(cx - halfDoc, cy - halfDoc, cx + halfDoc, cy + halfDoc);
    }

    public static Handle hitTestHandle(Resources res, float scale, RectF itemBoxDoc, float docX, float docY) {
        if (res == null || itemBoxDoc == null) return Handle.NONE;
        if (scale <= 0f) return Handle.NONE;

        final boolean insideBox = itemBoxDoc.contains(docX, docY);

        // Handles can overlap at small box sizes / low zoom. Prefer the *nearest* handle center
        // among those whose hit rect contains the point so the MOVE handle doesn't accidentally
        // become a corner resize (or vice versa).
        final float left = itemBoxDoc.left;
        final float right = itemBoxDoc.right;
        final float top = itemBoxDoc.top;
        final float bottom = itemBoxDoc.bottom;

        Handle best = Handle.NONE;
        float bestDist2 = Float.MAX_VALUE;

        final Handle[] candidates = new Handle[]{
                Handle.TOP_LEFT,
                Handle.TOP_RIGHT,
                Handle.BOTTOM_LEFT,
                Handle.BOTTOM_RIGHT,
                Handle.MOVE,
        };
        for (Handle h : candidates) {
            // Resize handles should be deliberate: if the touch starts inside the box, treat it as
            // a move gesture even if it overlaps a corner handle (Acrobat-ish behavior).
            if (insideBox && h != Handle.MOVE) continue;

            RectF hit = handleRectDoc(res, scale, itemBoxDoc, h);
            if (hit == null || !hit.contains(docX, docY)) continue;

            float cx;
            float cy;
            switch (h) {
                case TOP_LEFT:
                    cx = left;
                    cy = top;
                    break;
                case TOP_RIGHT:
                    cx = right;
                    cy = top;
                    break;
                case BOTTOM_LEFT:
                    cx = left;
                    cy = bottom;
                    break;
                case BOTTOM_RIGHT:
                    cx = right;
                    cy = bottom;
                    break;
                case MOVE:
                    cx = (left + right) * 0.5f;
                    cy = top;
                    break;
                default:
                    continue;
            }
            float dx = docX - cx;
            float dy = docY - cy;
            float dist2 = dx * dx + dy * dy;
            if (dist2 < bestDist2) {
                bestDist2 = dist2;
                best = h;
            }
        }

        return best;
    }

    public static Handle hitTestHandle(Resources res, float scale, RectF itemBoxDoc, float docX, float docY, boolean includeResizeHandles) {
        if (includeResizeHandles) return hitTestHandle(res, scale, itemBoxDoc, docX, docY);
        RectF hit = handleRectDoc(res, scale, itemBoxDoc, Handle.MOVE);
        return hit != null && hit.contains(docX, docY) ? Handle.MOVE : Handle.NONE;
    }
}
