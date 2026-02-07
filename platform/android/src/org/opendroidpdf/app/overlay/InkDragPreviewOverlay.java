package org.opendroidpdf.app.overlay;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;

/**
 * Lightweight overlay state for showing an in-progress move/resize preview of an ink annotation.
 *
 * <p>Points are stored in document coordinates. The renderer applies an affine transform between
 * {@link #startBoundsDoc} and {@link #currentBoundsDoc} while drawing so callers can update the
 * current bounds without recomputing arcs on every move.</p>
 */
public final class InkDragPreviewOverlay {
    @NonNull public final RectF startBoundsDoc;
    @NonNull public final RectF currentBoundsDoc = new RectF();
    @NonNull public final PointF[][] arcsDoc;
    public final int color;
    public final float thicknessDoc;

    public InkDragPreviewOverlay(@NonNull RectF startBoundsDoc,
                                 @NonNull RectF currentBoundsDoc,
                                 @NonNull PointF[][] arcsDoc,
                                 int color,
                                 float thicknessDoc) {
        this.startBoundsDoc = new RectF(startBoundsDoc);
        this.currentBoundsDoc.set(currentBoundsDoc);
        this.arcsDoc = arcsDoc;
        this.color = color;
        this.thicknessDoc = thicknessDoc;
    }

    public void setCurrentBoundsDoc(@NonNull RectF boundsDoc) {
        currentBoundsDoc.set(boundsDoc);
    }
}

