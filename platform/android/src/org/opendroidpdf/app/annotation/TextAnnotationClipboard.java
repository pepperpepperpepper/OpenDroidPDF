package org.opendroidpdf.app.annotation;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * In-memory clipboard for text annotations (embedded FreeText and sidecar notes).
 *
 * <p>This is intentionally app-local (not system clipboard) so we can preserve style and geometry.
 * The plain text is still copied to the Android clipboard by callers when appropriate.</p>
 */
public final class TextAnnotationClipboard {
    public enum Kind {
        EMBEDDED_FREETEXT,
        SIDECAR_NOTE,
    }

    public static final class Payload {
        @NonNull public final Kind kind;
        @NonNull public final RectF boundsDoc;
        @NonNull public final String text;

        // Typography / layout
        public final float fontSizePt;
        /** CSS-like {@code line-height} multiplier. */
        public final float lineHeight;
        /** CSS-like {@code text-indent} in pt. */
        public final float textIndentPt;
        public final int fontFamily;
        public final int fontStyleFlags;
        /** 0=left, 1=center, 2=right (PDF FreeText "Q"). */
        public final int alignment;
        /** 0/90/180/270 degrees. */
        public final int rotationDeg;

        // Colors / appearance (ARGB, plus opacity for fills)
        public final int textColorArgb;
        public final int backgroundColorArgb;
        public final float backgroundOpacity;
        public final int borderColorArgb;
        public final float borderWidthPt;
        public final boolean borderDashed;
        public final float borderRadiusPt;

        // Locks / sizing behavior
        public final boolean lockPositionSize;
        public final boolean lockContents;
        public final boolean userResized;

        public Payload(@NonNull Kind kind,
                       @NonNull RectF boundsDoc,
                       @NonNull String text,
                       float fontSizePt,
                       float lineHeight,
                       float textIndentPt,
                       int fontFamily,
                       int fontStyleFlags,
                       int alignment,
                       int rotationDeg,
                       int textColorArgb,
                       int backgroundColorArgb,
                       float backgroundOpacity,
                       int borderColorArgb,
                       float borderWidthPt,
                       boolean borderDashed,
                       float borderRadiusPt,
                       boolean lockPositionSize,
                       boolean lockContents,
                       boolean userResized) {
            this.kind = kind;
            this.boundsDoc = new RectF(boundsDoc);
            this.text = text;
            this.fontSizePt = fontSizePt;
            this.lineHeight = lineHeight;
            this.textIndentPt = textIndentPt;
            this.fontFamily = fontFamily;
            this.fontStyleFlags = fontStyleFlags;
            this.alignment = alignment;
            this.rotationDeg = rotationDeg;
            this.textColorArgb = textColorArgb;
            this.backgroundColorArgb = backgroundColorArgb;
            this.backgroundOpacity = backgroundOpacity;
            this.borderColorArgb = borderColorArgb;
            this.borderWidthPt = borderWidthPt;
            this.borderDashed = borderDashed;
            this.borderRadiusPt = borderRadiusPt;
            this.lockPositionSize = lockPositionSize;
            this.lockContents = lockContents;
            this.userResized = userResized;
        }
    }

    @Nullable private static volatile Payload payload;
    private static volatile int pasteCount = 0;

    private TextAnnotationClipboard() {}

    public static void set(@Nullable Payload next) {
        payload = next;
        pasteCount = 0;
    }

    @Nullable
    public static Payload get() {
        return payload;
    }

    public static boolean hasPayload() {
        return payload != null;
    }

    /**
     * Returns an incrementing 1-based paste index for computing offsets (1, 2, 3, ...).
     * Reset to 0 whenever {@link #set(Payload)} is called.
     */
    public static int nextPasteIndex() {
        int next = pasteCount + 1;
        pasteCount = next;
        return next;
    }
}
