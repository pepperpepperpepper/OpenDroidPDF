package org.opendroidpdf.app.annotation;

import android.content.res.Resources;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.overlay.ItemSelectionHandles;

/**
 * Heuristic bounds fitter for embedded PDF FreeText annotations.
 *
 * <p>Goal: mimic Acrobat-ish behavior where the FreeText box isn't a huge empty rectangle,
 * and grows to fit content when the user edits text.</p>
 */
public final class FreeTextBoundsFitter {
    private static final float DEFAULT_BASE_DPI = 160f;
    private static final float PDF_POINTS_DPI = 72f;
    private static final float MAX_WIDTH_FRACTION = 0.85f;
    private static final float LINE_SPACING_MULT = 1.15f;

    private FreeTextBoundsFitter() {}

    @Nullable
    public static RectF compute(
            @NonNull Resources res,
            float scale,
            float pageDocWidth,
            float pageDocHeight,
            @NonNull RectF currentBoundsDoc,
            @NonNull String text,
            float fontSizePt,
            int baseDpi,
            boolean allowWidthGrow,
            boolean shrink) {
        if (res == null) return null;
        if (scale <= 0f) return null;
        if (pageDocWidth <= 0f || pageDocHeight <= 0f) return null;
        if (currentBoundsDoc == null) return null;
        if (text == null) return null;

        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;

        float left0 = Math.min(currentBoundsDoc.left, currentBoundsDoc.right);
        float right0 = Math.max(currentBoundsDoc.left, currentBoundsDoc.right);
        float top0 = Math.min(currentBoundsDoc.top, currentBoundsDoc.bottom);
        float bottom0 = Math.max(currentBoundsDoc.top, currentBoundsDoc.bottom);

        float currentW = Math.max(1f, right0 - left0);
        float currentH = Math.max(1f, bottom0 - top0);

        float minEdgeDoc = ItemSelectionHandles.minEdgePx(res) / scale;
        float maxWidthDoc = Math.max(minEdgeDoc, Math.min(pageDocWidth, pageDocWidth * MAX_WIDTH_FRACTION));

        float dpi = baseDpi > 0 ? (float) baseDpi : DEFAULT_BASE_DPI;
        float fontPt = Math.max(6f, fontSizePt);
        float fontDocPx = fontPt * (dpi / PDF_POINTS_DPI);
        float pad = clamp(fontDocPx * 0.25f, 2f, 14f);

        TextPaint paint = new TextPaint();
        paint.setAntiAlias(true);
        paint.setTextSize(fontDocPx);

        float naturalWidth = maxLineWidthDocPx(trimmed, paint);

        float desiredW = currentW;
        float desiredWContent = naturalWidth + 2f * pad;
        if (shrink) {
            desiredW = Math.min(desiredW, desiredWContent);
        } else if (allowWidthGrow && desiredWContent > desiredW) {
            desiredW = Math.min(maxWidthDoc, desiredWContent);
        }
        desiredW = clamp(desiredW, minEdgeDoc, pageDocWidth);

        int innerWpx = (int) Math.max(1f, Math.floor(desiredW - 2f * pad));
        StaticLayout layout = buildLayout(trimmed, paint, innerWpx);
        float desiredH = layout.getHeight() + 2f * pad;
        if (!shrink) desiredH = Math.max(currentH, desiredH);
        desiredH = clamp(desiredH, minEdgeDoc, pageDocHeight);

        float left = clamp(left0, 0f, pageDocWidth);
        float top = clamp(top0, 0f, pageDocHeight);
        float right = left + desiredW;
        float bottom = top + desiredH;

        // Clamp to page bounds by shifting, keeping size stable.
        if (right > pageDocWidth) {
            float overflow = right - pageDocWidth;
            left = Math.max(0f, left - overflow);
            right = left + desiredW;
        }
        if (bottom > pageDocHeight) {
            float overflow = bottom - pageDocHeight;
            top = Math.max(0f, top - overflow);
            bottom = top + desiredH;
        }

        RectF next = new RectF(left, top, right, bottom);
        if (approxEquals(next, left0, top0, right0, bottom0)) return null;
        return next;
    }

    private static float maxLineWidthDocPx(@NonNull String text, @NonNull TextPaint paint) {
        float best = 0f;
        String[] lines = text.split("\\r?\\n");
        if (lines.length == 0) return 0f;
        for (String line : lines) {
            if (line == null) continue;
            String s = line.trim();
            if (s.isEmpty()) continue;
            best = Math.max(best, paint.measureText(s));
        }
        return best;
    }

    @NonNull
    private static StaticLayout buildLayout(@NonNull String text, @NonNull TextPaint paint, int widthPx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, widthPx)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, LINE_SPACING_MULT)
                    .setIncludePad(false)
                    .build();
        }
        //noinspection deprecation
        return new StaticLayout(text, paint, widthPx, Layout.Alignment.ALIGN_NORMAL, LINE_SPACING_MULT, 0.0f, false);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean approxEquals(@NonNull RectF r, float left, float top, float right, float bottom) {
        final float eps = 0.75f;
        return Math.abs(r.left - left) < eps
                && Math.abs(r.top - top) < eps
                && Math.abs(r.right - right) < eps
                && Math.abs(r.bottom - bottom) < eps;
    }
}

