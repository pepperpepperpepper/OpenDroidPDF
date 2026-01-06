package org.opendroidpdf.app.fillsign;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;

/** Immutable overlay state used to render an in-progress Fill & Sign placement. */
public final class FillSignPlacementOverlay {
    @NonNull public final RectF boundsDoc;
    public final float rotationRad;
    @NonNull public final PointF[][] arcsDoc;

    public FillSignPlacementOverlay(@NonNull RectF boundsDoc, float rotationRad, @NonNull PointF[][] arcsDoc) {
        this.boundsDoc = new RectF(boundsDoc);
        this.rotationRad = rotationRad;
        this.arcsDoc = arcsDoc;
    }
}

