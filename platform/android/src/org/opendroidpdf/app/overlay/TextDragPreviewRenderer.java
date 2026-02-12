package org.opendroidpdf.app.overlay;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.annotation.TextFontFamily;
import org.opendroidpdf.app.annotation.TextStyleFlags;

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
    @Nullable private Typeface lastTypeface;
    @Nullable private Layout.Alignment lastAlignment;
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
            @Nullable TextDragPreviewOverlay overlay) {
        if (canvas == null || res == null) return;
        if (scale <= 0f) return;
        if (itemBoxDoc == null) return;
        if (overlay == null) return;

        String trimmed = overlay.trimmedTextOrNull();
        if (trimmed == null) return;

        float left = itemBoxDoc.left * scale;
        float top = itemBoxDoc.top * scale;
        float right = itemBoxDoc.right * scale;
        float bottom = itemBoxDoc.bottom * scale;

        float density = res.getDisplayMetrics().density;
        float pad = PADDING_DP * density;

        float innerW = Math.max(1f, (right - left) - 2f * pad);
        float innerH = Math.max(1f, (bottom - top) - 2f * pad);

        float textSizePx;
        if (Float.isFinite(overlay.fontSizeDoc) && overlay.fontSizeDoc > 0f) {
            // Match the FreeText style: fontSizeDoc is stored in document units; scale to view px.
            textSizePx = overlay.fontSizeDoc * scale;
        } else {
            // Fallback heuristic: proportional to the current box height so it scales with zoom.
            textSizePx = innerH * 0.60f;
        }
        textSizePx = Math.max(MIN_TEXT_SIZE_DP * density, textSizePx);

        int argb = overlay.textColor;
        if ((argb >>> 24) == 0) argb = 0xCC000000;
        paint.setColor(argb);
        paint.setTextSize(textSizePx);

        int widthPx = (int) innerW;
        if (widthPx <= 0) return;

        int family = TextFontFamily.normalize(overlay.fontFamily);
        Typeface base = TextFontFamily.typeface(family);
        int tfStyle = TextStyleFlags.typefaceStyle(overlay.styleFlags);
        Typeface tf = Typeface.create(base, tfStyle);
        paint.setTypeface(tf);
        paint.setUnderlineText(TextStyleFlags.isUnderline(overlay.styleFlags));
        paint.setStrikeThruText(TextStyleFlags.isStrikethrough(overlay.styleFlags));

        Layout.Alignment align;
        int a = Math.max(0, Math.min(2, overlay.alignment));
        if (a == 1) align = Layout.Alignment.ALIGN_CENTER;
        else if (a == 2) align = Layout.Alignment.ALIGN_OPPOSITE;
        else align = Layout.Alignment.ALIGN_NORMAL;

        StaticLayout layout = getOrBuildLayout(trimmed, widthPx, textSizePx, tf, align);

        canvas.save();
        canvas.clipRect(left + pad, top + pad, right - pad, bottom - pad);
        canvas.translate(left + pad, top + pad);
        layout.draw(canvas);
        canvas.restore();
    }

    @NonNull
    private StaticLayout getOrBuildLayout(@NonNull String text,
                                          int widthPx,
                                          float textSizePx,
                                          @Nullable Typeface typeface,
                                          @NonNull Layout.Alignment alignment) {
        StaticLayout cached = lastLayout;
        if (cached != null
                && text.equals(lastText)
                && widthPx == lastWidthPx
                && Math.abs(textSizePx - lastTextSizePx) < 0.5f) {
            if ((lastTypeface == null && typeface == null)
                    || (lastTypeface != null && lastTypeface.equals(typeface))) {
                if (alignment == lastAlignment) {
                    return cached;
                }
            }
        }

        StaticLayout layout;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, widthPx)
                    .setAlignment(alignment)
                    .setLineSpacing(0f, 1.0f)
                    .setIncludePad(false)
                    .build();
        } else {
            //noinspection deprecation
            layout = new StaticLayout(text, paint, widthPx, alignment, 1.0f, 0.0f, false);
        }

        lastText = text;
        lastWidthPx = widthPx;
        lastTextSizePx = textSizePx;
        lastTypeface = typeface;
        lastAlignment = alignment;
        lastLayout = layout;
        return layout;
    }
}
