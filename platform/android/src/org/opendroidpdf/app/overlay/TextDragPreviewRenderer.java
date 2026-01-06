package org.opendroidpdf.app.overlay;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Draws a lightweight, in-overlay preview of text content while the user is
 * directly manipulating (move/resize) a text annotation selection box.
 *
 * <p>This is intentionally approximate: it keeps text visible during drag even if the
 * underlying PDF render is only updated after ACTION_UP.</p>
 */
public final class TextDragPreviewRenderer {
    private static final float PADDING_DP = 4f;
    private static final float MIN_TEXT_SIZE_DP = 10f;

    private final TextPaint paint = new TextPaint();

    @Nullable private String lastText;
    private int lastWidthPx = -1;
    private float lastTextSizePx = -1f;
    @Nullable private StaticLayout lastLayout;

    public TextDragPreviewRenderer() {
        paint.setAntiAlias(true);
        paint.setColor(0xCC000000); // slightly translucent black
    }

    public void draw(
            @Nullable Canvas canvas,
            @Nullable Resources res,
            float scale,
            @Nullable RectF itemBoxDoc,
            @Nullable String text) {
        if (canvas == null || res == null) return;
        if (scale <= 0f) return;
        if (itemBoxDoc == null) return;
        if (text == null) return;

        String trimmed = text.trim();
        if (trimmed.isEmpty()) return;

        float left = itemBoxDoc.left * scale;
        float top = itemBoxDoc.top * scale;
        float right = itemBoxDoc.right * scale;
        float bottom = itemBoxDoc.bottom * scale;

        float density = res.getDisplayMetrics().density;
        float pad = PADDING_DP * density;

        float innerW = Math.max(1f, (right - left) - 2f * pad);
        float innerH = Math.max(1f, (bottom - top) - 2f * pad);

        // Heuristic text sizing: pick a size proportional to the current box height so it scales
        // with zoom. This keeps the preview usable without needing native-style extraction.
        float textSizePx = Math.max(MIN_TEXT_SIZE_DP * density, innerH * 0.60f);
        paint.setTextSize(textSizePx);

        int widthPx = (int) innerW;
        if (widthPx <= 0) return;

        StaticLayout layout = getOrBuildLayout(trimmed, widthPx, textSizePx);

        canvas.save();
        canvas.clipRect(left + pad, top + pad, right - pad, bottom - pad);
        canvas.translate(left + pad, top + pad);
        layout.draw(canvas);
        canvas.restore();
    }

    @NonNull
    private StaticLayout getOrBuildLayout(@NonNull String text, int widthPx, float textSizePx) {
        StaticLayout cached = lastLayout;
        if (cached != null
                && text.equals(lastText)
                && widthPx == lastWidthPx
                && Math.abs(textSizePx - lastTextSizePx) < 0.5f) {
            return cached;
        }

        StaticLayout layout;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, widthPx)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.0f)
                    .setIncludePad(false)
                    .build();
        } else {
            //noinspection deprecation
            layout = new StaticLayout(text, paint, widthPx, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }

        lastText = text;
        lastWidthPx = widthPx;
        lastTextSizePx = textSizePx;
        lastLayout = layout;
        return layout;
    }
}
