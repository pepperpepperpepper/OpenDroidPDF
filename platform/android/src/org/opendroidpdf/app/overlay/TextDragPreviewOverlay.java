package org.opendroidpdf.app.overlay;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lightweight overlay state for showing an in-progress move/resize preview of a text annotation.
 *
 * <p>Units:</p>
 * <ul>
 *     <li>{@link #fontSizeDoc} is in document units (MuPDF base DPI pixel space), matching how
 *     FreeText font sizes are converted for the inline editor.</li>
 * </ul>
 */
public final class TextDragPreviewOverlay {
    @NonNull public final String text;
    public final int textColor;
    public final float fontSizeDoc;
    public final int fontFamily;
    public final int styleFlags;
    /** 0=left, 1=center, 2=right (mirrors the MuPDF FreeText alignment value). */
    public final int alignment;

    public TextDragPreviewOverlay(
            @NonNull String text,
            int textColor,
            float fontSizeDoc,
            int fontFamily,
            int styleFlags,
            int alignment) {
        this.text = text;
        this.textColor = textColor;
        this.fontSizeDoc = fontSizeDoc;
        this.fontFamily = fontFamily;
        this.styleFlags = styleFlags;
        this.alignment = alignment;
    }

    @Nullable
    public String trimmedTextOrNull() {
        String t = text != null ? text.trim() : null;
        return (t == null || t.isEmpty()) ? null : t;
    }
}

