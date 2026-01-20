package org.opendroidpdf.app.annotation;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.core.MuPdfController;

import java.util.HashSet;
import java.util.Set;

final class EmbeddedFreeTextRepositoryOps {
    // PDF annotation flags (/F) bits for lock controls.
    static final int PDF_ANNOT_FLAG_LOCKED = 1 << (8 - 1);
    static final int PDF_ANNOT_FLAG_LOCKED_CONTENTS = 1 << (10 - 1);

    static final class CreatedFreeText {
        @Nullable final Annotation annotation;
        final long objectId;

        CreatedFreeText(@Nullable Annotation annotation, long objectId) {
            this.annotation = annotation;
            this.objectId = objectId;
        }
    }

    @NonNull
    CreatedFreeText tryAddFreeTextAnnotation(@NonNull MuPdfController controller,
                                             int page,
                                             @NonNull PointF[] rectTwoPoints,
                                             @NonNull RectF desiredBounds,
                                             @NonNull String text) {
        Annotation newAnnot = null;
        long newObjectId = -1L;
        try {
            Annotation[] before = controller.annotations(page);
            Set<Long> beforeIds = new HashSet<>();
            if (before != null) {
                for (Annotation a : before) {
                    if (a == null) continue;
                    if (a.objectNumber > 0L) beforeIds.add(a.objectNumber);
                }
            }

            controller.addTextAnnotation(page, rectTwoPoints, text);

            Annotation[] after = controller.annotations(page);
            newAnnot = findNewFreeText(after, beforeIds, desiredBounds, text);
            if (newAnnot != null) newObjectId = newAnnot.objectNumber;
        } catch (Throwable ignore) {
            newAnnot = null;
            newObjectId = -1L;
        }
        return new CreatedFreeText(newAnnot, newObjectId);
    }

    void tryDeleteAnnotationByObjectNumber(@NonNull MuPdfController controller, int page, long objectId) {
        try {
            controller.deleteAnnotationByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
        }
    }

    void updateAnnotationRectByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, @NonNull RectF boundsDoc) {
        controller.rawRepository().updateAnnotationRectByObjectNumber(
                page,
                objectId,
                boundsDoc.left,
                boundsDoc.top,
                boundsDoc.right,
                boundsDoc.bottom);
    }

    void setFreeTextUserResizedByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, boolean userResized) {
        controller.rawRepository().setFreeTextUserResizedByObjectNumber(page, objectId, userResized);
    }

    void updateFreeTextStyleByObjectNumber(@NonNull MuPdfController controller,
                                           int page,
                                           long objectId,
                                           float fontSizePt,
                                           float textR,
                                           float textG,
                                           float textB) {
        controller.rawRepository().updateFreeTextStyleByObjectNumber(page, objectId, fontSizePt, textR, textG, textB);
    }

    void updateFreeTextBackgroundByObjectNumber(@NonNull MuPdfController controller,
                                                int page,
                                                long objectId,
                                                float r,
                                                float g,
                                                float b,
                                                float opacity) {
        controller.rawRepository().updateFreeTextBackgroundByObjectNumber(page, objectId, r, g, b, opacity);
    }

    void updateFreeTextBorderByObjectNumber(@NonNull MuPdfController controller,
                                            int page,
                                            long objectId,
                                            float r,
                                            float g,
                                            float b,
                                            float widthPt,
                                            boolean dashed,
                                            float radiusPt) {
        controller.rawRepository().updateFreeTextBorderByObjectNumber(page, objectId, r, g, b, widthPt, dashed, radiusPt);
    }

    void updateFreeTextLocksByObjectNumber(@NonNull MuPdfController controller,
                                           int page,
                                           long objectId,
                                           boolean lockPositionSize,
                                           boolean lockContents) {
        controller.rawRepository().updateFreeTextLocksByObjectNumber(page, objectId, lockPositionSize, lockContents);
    }

    void updateFreeTextFontFamilyByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, int fontFamily) {
        controller.rawRepository().updateFreeTextFontFamilyByObjectNumber(page, objectId, fontFamily);
    }

    void updateFreeTextStyleFlagsByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, int styleFlags) {
        controller.rawRepository().updateFreeTextStyleFlagsByObjectNumber(page, objectId, styleFlags);
    }

    void updateFreeTextParagraphByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, float lineHeight, float textIndentPt) {
        controller.rawRepository().updateFreeTextParagraphByObjectNumber(page, objectId, lineHeight, textIndentPt);
    }

    void updateFreeTextAlignmentByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, int alignment) {
        controller.rawRepository().updateFreeTextAlignmentByObjectNumber(page, objectId, alignment);
    }

    void updateFreeTextRotationByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, int rotationDeg) {
        controller.rawRepository().updateFreeTextRotationByObjectNumber(page, objectId, rotationDeg);
    }

    float getFreeTextFontSizeOrDefaultRaw(@NonNull MuPdfController controller, int page, long objectId, float fallbackPt) {
        try {
            return controller.rawRepository().getFreeTextFontSizeByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return fallbackPt;
        }
    }

    float getFreeTextFontSizeOrDefaultValidated(@NonNull MuPdfController controller, int page, long objectId, float fallbackPt) {
        try {
            float pt = controller.rawRepository().getFreeTextFontSizeByObjectNumber(page, objectId);
            if (!Float.isNaN(pt) && !Float.isInfinite(pt) && pt > 0.0f) return pt;
        } catch (Throwable ignore) {
        }
        return fallbackPt;
    }

    int getFreeTextFontFamilyOrDefaultRaw(@NonNull MuPdfController controller, int page, long objectId, int fallbackFamily) {
        try {
            return controller.rawRepository().getFreeTextFontFamilyByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return fallbackFamily;
        }
    }

    int getFreeTextFontFamilyOrDefaultNormalized(@NonNull MuPdfController controller, int page, long objectId, int fallbackFamily) {
        try {
            int family = controller.rawRepository().getFreeTextFontFamilyByObjectNumber(page, objectId);
            return TextFontFamily.normalize(family);
        } catch (Throwable ignore) {
            return fallbackFamily;
        }
    }

    int getFreeTextStyleFlagsOrDefaultRaw(@NonNull MuPdfController controller, int page, long objectId, int fallbackFlags) {
        try {
            return controller.rawRepository().getFreeTextStyleFlagsByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return fallbackFlags;
        }
    }

    int getFreeTextStyleFlagsOrDefaultNormalized(@NonNull MuPdfController controller, int page, long objectId, int fallbackFlags) {
        try {
            int flags = controller.rawRepository().getFreeTextStyleFlagsByObjectNumber(page, objectId);
            return TextStyleFlags.normalize(flags);
        } catch (Throwable ignore) {
            return fallbackFlags;
        }
    }

    @Nullable
    float[] getFreeTextParagraphOrNull(@NonNull MuPdfController controller, int page, long objectId) {
        try {
            return controller.rawRepository().getFreeTextParagraphByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return null;
        }
    }

    int getFreeTextAlignmentOrDefaultRaw(@NonNull MuPdfController controller, int page, long objectId, int fallback) {
        try {
            return controller.rawRepository().getFreeTextAlignmentByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    int getFreeTextAlignmentOrDefaultNormalized(@NonNull MuPdfController controller, int page, long objectId, int fallback) {
        try {
            int q = controller.rawRepository().getFreeTextAlignmentByObjectNumber(page, objectId);
            return Math.max(0, Math.min(2, q));
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    int getFreeTextRotationOrDefaultRaw(@NonNull MuPdfController controller, int page, long objectId, int fallback) {
        try {
            return controller.rawRepository().getFreeTextRotationByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    int getFreeTextRotationOrDefaultNormalized(@NonNull MuPdfController controller, int page, long objectId, int fallback) {
        try {
            int rot = controller.rawRepository().getFreeTextRotationByObjectNumber(page, objectId);
            if (rot < 0 || rot >= 360) {
                rot %= 360;
                if (rot < 0) rot += 360;
            }
            return rot;
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    int getFreeTextFlagsOrDefault(@NonNull MuPdfController controller, int page, long objectId, int fallbackFlags) {
        try {
            return controller.rawRepository().getFreeTextFlagsByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return fallbackFlags;
        }
    }

    boolean getFreeTextUserResizedOrDefault(@NonNull MuPdfController controller, int page, long objectId, boolean fallback) {
        try {
            return controller.rawRepository().getFreeTextUserResizedByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    boolean isFreeTextPositionLockedOrDefault(@NonNull MuPdfController controller, int page, long objectId, boolean fallback) {
        int flags = getFreeTextFlagsOrDefault(controller, page, objectId, -1);
        if (flags < 0) return fallback;
        return (flags & PDF_ANNOT_FLAG_LOCKED) != 0;
    }

    boolean isFreeTextContentsLockedOrDefault(@NonNull MuPdfController controller, int page, long objectId, boolean fallback) {
        int flags = getFreeTextFlagsOrDefault(controller, page, objectId, -1);
        if (flags < 0) return fallback;
        return (flags & PDF_ANNOT_FLAG_LOCKED_CONTENTS) != 0;
    }

    @Nullable
    float[] getFreeTextTextColorOrNull(@NonNull MuPdfController controller, int page, long objectId) {
        try {
            return controller.rawRepository().getFreeTextTextColorByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return null;
        }
    }

    @Nullable
    float[] getFreeTextBackgroundOrNull(@NonNull MuPdfController controller, int page, long objectId) {
        try {
            return controller.rawRepository().getFreeTextBackgroundByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return null;
        }
    }

    @Nullable
    float[] getFreeTextBorderOrNull(@NonNull MuPdfController controller, int page, long objectId) {
        try {
            return controller.rawRepository().getFreeTextBorderByObjectNumber(page, objectId);
        } catch (Throwable ignore) {
            return null;
        }
    }

    int getBaseResolutionDpiOrDefault(@NonNull MuPdfController controller, int fallbackDpi) {
        try {
            return controller.rawRepository().getBaseResolutionDpi();
        } catch (Throwable ignore) {
            return fallbackDpi;
        }
    }

    void tryApplyPayloadStyle(@NonNull MuPdfController controller, int page, long objectId, @NonNull TextAnnotationClipboard.Payload payload) {
        try {
            float r = Color.red(payload.textColorArgb) / 255f;
            float g = Color.green(payload.textColorArgb) / 255f;
            float b = Color.blue(payload.textColorArgb) / 255f;
            controller.rawRepository().updateFreeTextStyleByObjectNumber(page, objectId, payload.fontSizePt, r, g, b);
        } catch (Throwable ignore) {}
        try {
            float r = Color.red(payload.backgroundColorArgb) / 255f;
            float g = Color.green(payload.backgroundColorArgb) / 255f;
            float b = Color.blue(payload.backgroundColorArgb) / 255f;
            controller.rawRepository().updateFreeTextBackgroundByObjectNumber(page, objectId, r, g, b, payload.backgroundOpacity);
        } catch (Throwable ignore) {}
        try {
            float r = Color.red(payload.borderColorArgb) / 255f;
            float g = Color.green(payload.borderColorArgb) / 255f;
            float b = Color.blue(payload.borderColorArgb) / 255f;
            controller.rawRepository().updateFreeTextBorderByObjectNumber(page, objectId, r, g, b, payload.borderWidthPt, payload.borderDashed, payload.borderRadiusPt);
        } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextFontFamilyByObjectNumber(page, objectId, payload.fontFamily); } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextStyleFlagsByObjectNumber(page, objectId, payload.fontStyleFlags); } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextParagraphByObjectNumber(page, objectId, payload.lineHeight, payload.textIndentPt); } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextAlignmentByObjectNumber(page, objectId, payload.alignment); } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextRotationByObjectNumber(page, objectId, payload.rotationDeg); } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextLocksByObjectNumber(page, objectId, payload.lockPositionSize, payload.lockContents); } catch (Throwable ignore) {}
        try { controller.rawRepository().setFreeTextUserResizedByObjectNumber(page, objectId, payload.userResized); } catch (Throwable ignore) {}
        try { controller.markDocumentDirty(); } catch (Throwable ignore) {}
    }

    void tryCopyStyleFromSourceToDest(@NonNull MuPdfController controller, int page, long sourceObjectId, long destObjectId) {
        try {
            int family = controller.rawRepository().getFreeTextFontFamilyByObjectNumber(page, sourceObjectId);
            controller.rawRepository().updateFreeTextFontFamilyByObjectNumber(page, destObjectId, family);
        } catch (Throwable ignore) {}
        try {
            int flags = controller.rawRepository().getFreeTextStyleFlagsByObjectNumber(page, sourceObjectId);
            controller.rawRepository().updateFreeTextStyleFlagsByObjectNumber(page, destObjectId, flags);
        } catch (Throwable ignore) {}
        try {
            float[] p = controller.rawRepository().getFreeTextParagraphByObjectNumber(page, sourceObjectId);
            if (p != null && p.length >= 2) {
                controller.rawRepository().updateFreeTextParagraphByObjectNumber(page, destObjectId, p[0], p[1]);
            }
        } catch (Throwable ignore) {}
        try {
            int q = controller.rawRepository().getFreeTextAlignmentByObjectNumber(page, sourceObjectId);
            controller.rawRepository().updateFreeTextAlignmentByObjectNumber(page, destObjectId, q);
        } catch (Throwable ignore) {}
        try {
            int rot = controller.rawRepository().getFreeTextRotationByObjectNumber(page, sourceObjectId);
            controller.rawRepository().updateFreeTextRotationByObjectNumber(page, destObjectId, rot);
        } catch (Throwable ignore) {}
        try {
            int flags = controller.rawRepository().getFreeTextFlagsByObjectNumber(page, sourceObjectId);
            boolean lockPos = (flags & PDF_ANNOT_FLAG_LOCKED) != 0;
            boolean lockContents = (flags & PDF_ANNOT_FLAG_LOCKED_CONTENTS) != 0;
            controller.rawRepository().updateFreeTextLocksByObjectNumber(page, destObjectId, lockPos, lockContents);
        } catch (Throwable ignore) {}
        try {
            boolean userResized = controller.rawRepository().getFreeTextUserResizedByObjectNumber(page, sourceObjectId);
            controller.rawRepository().setFreeTextUserResizedByObjectNumber(page, destObjectId, userResized);
        } catch (Throwable ignore) {}
        try { controller.markDocumentDirty(); } catch (Throwable ignore) {}
    }

    void tryUpdateAnnotationContentsByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, @NonNull String text) {
        try {
            controller.rawRepository().updateAnnotationContentsByObjectNumber(page, objectId, text);
        } catch (Throwable ignore) {
        }
    }

    void tryUpdateAnnotationRectByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, @NonNull RectF boundsDoc) {
        try {
            updateAnnotationRectByObjectNumber(controller, page, objectId, boundsDoc);
        } catch (Throwable ignore) {
        }
    }

    void trySetFreeTextUserResizedByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, boolean userResized) {
        try {
            setFreeTextUserResizedByObjectNumber(controller, page, objectId, userResized);
        } catch (Throwable ignore) {
        }
    }

    void tryUpdateFreeTextStyleByObjectNumber(@NonNull MuPdfController controller,
                                              int page,
                                              long objectId,
                                              float fontSizePt,
                                              float textR,
                                              float textG,
                                              float textB) {
        try {
            updateFreeTextStyleByObjectNumber(controller, page, objectId, fontSizePt, textR, textG, textB);
        } catch (Throwable ignore) {
        }
    }

    void tryUpdateFreeTextParagraphByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, float lineHeight, float textIndentPt) {
        try { updateFreeTextParagraphByObjectNumber(controller, page, objectId, lineHeight, textIndentPt); } catch (Throwable ignore) {}
    }

    void tryUpdateFreeTextFontFamilyByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, int fontFamily) {
        try { updateFreeTextFontFamilyByObjectNumber(controller, page, objectId, fontFamily); } catch (Throwable ignore) {}
    }

    void tryUpdateFreeTextStyleFlagsByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, int styleFlags) {
        try { updateFreeTextStyleFlagsByObjectNumber(controller, page, objectId, styleFlags); } catch (Throwable ignore) {}
    }

    void tryUpdateFreeTextAlignmentByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, int alignment) {
        try { updateFreeTextAlignmentByObjectNumber(controller, page, objectId, alignment); } catch (Throwable ignore) {}
    }

    void tryUpdateFreeTextRotationByObjectNumber(@NonNull MuPdfController controller, int page, long objectId, int rotationDeg) {
        try { updateFreeTextRotationByObjectNumber(controller, page, objectId, rotationDeg); } catch (Throwable ignore) {}
    }

    void tryUpdateFreeTextBackgroundByObjectNumber(@NonNull MuPdfController controller,
                                                   int page,
                                                   long objectId,
                                                   float r,
                                                   float g,
                                                   float b,
                                                   float opacity) {
        try { updateFreeTextBackgroundByObjectNumber(controller, page, objectId, r, g, b, opacity); } catch (Throwable ignore) {}
    }

    void tryUpdateFreeTextBorderByObjectNumber(@NonNull MuPdfController controller,
                                               int page,
                                               long objectId,
                                               float r,
                                               float g,
                                               float b,
                                               float widthPt,
                                               boolean dashed,
                                               float radiusPt) {
        try { updateFreeTextBorderByObjectNumber(controller, page, objectId, r, g, b, widthPt, dashed, radiusPt); } catch (Throwable ignore) {}
    }

    void tryUpdateFreeTextLocksByObjectNumber(@NonNull MuPdfController controller,
                                              int page,
                                              long objectId,
                                              boolean lockPositionSize,
                                              boolean lockContents) {
        try { updateFreeTextLocksByObjectNumber(controller, page, objectId, lockPositionSize, lockContents); } catch (Throwable ignore) {}
    }

    void tryMarkDocumentDirty(@NonNull MuPdfController controller) {
        try { controller.markDocumentDirty(); } catch (Throwable ignore) {}
    }

    @Nullable
    private static Annotation findNewFreeText(@Nullable Annotation[] after,
                                              @NonNull Set<Long> beforeIds,
                                              @NonNull RectF desiredBounds,
                                              @NonNull String desiredText) {
        if (after == null || after.length == 0) return null;

        Annotation best = null;
        float bestDist = Float.MAX_VALUE;
        float cx = desiredBounds.centerX();
        float cy = desiredBounds.centerY();

        for (Annotation a : after) {
            if (a == null) continue;
            if (a.type != Annotation.Type.FREETEXT) continue;
            if (a.objectNumber <= 0L) continue;
            if (beforeIds.contains(a.objectNumber)) continue;

            float dx = a.centerX() - cx;
            float dy = a.centerY() - cy;
            float dist = (dx * dx) + (dy * dy);

            // Prefer exact text match when available; fall back to proximity.
            if (desiredText != null && !desiredText.isEmpty()) {
                String t = a.text != null ? a.text : "";
                if (!t.equals(desiredText)) {
                    dist += 5_000_000f;
                }
            }

            if (best == null || dist < bestDist) {
                best = a;
                bestDist = dist;
            }
        }
        return best;
    }
}

