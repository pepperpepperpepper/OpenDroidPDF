package org.opendroidpdf.app.annotation;

import android.content.res.Resources;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class SidecarNoteAutoFit {
    private SidecarNoteAutoFit() {
    }

    /**
     * Computes an updated note bounds for "edit text, grow box" behavior.
     *
     * <p>Sidecar notes store font sizes in doc units already; use a base dpi of 72 so the
     * FreeText fitter's pt->doc conversion becomes a no-op.</p>
     */
    @Nullable
    static RectF computeAutoFitBoundsForTextUpdate(@NonNull Resources res,
                                                   float scale,
                                                   int viewWidthPx,
                                                   int viewHeightPx,
                                                   @NonNull RectF currentBoundsDoc,
                                                   @Nullable String nextText,
                                                   float fontSizeDoc,
                                                   boolean userResized) {
        if (nextText == null || nextText.trim().isEmpty()) return null;
        if (scale <= 0f) return null;

        float pageDocWidth = viewWidthPx / scale;
        float pageDocHeight = viewHeightPx / scale;
        if (pageDocWidth <= 0f || pageDocHeight <= 0f) return null;

        boolean allowWidthGrow = !userResized;
        return FreeTextBoundsFitter.compute(
                res,
                scale,
                pageDocWidth,
                pageDocHeight,
                currentBoundsDoc,
                nextText,
                fontSizeDoc,
                72,
                allowWidthGrow,
                false);
    }
}

