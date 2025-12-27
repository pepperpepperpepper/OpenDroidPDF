package org.opendroidpdf.app.annotation;

import android.graphics.PointF;

/**
 * Converts text-annotation bounds into quad points in the correct coordinate space.
 *
 * <p>Both sidecar and embedded annotation creation APIs expect points in the same page-pixel
 * coordinate space used by the viewer (top-left origin). Native code converts from page-pixel
 * space into PDF coordinates as needed.</p>
 */
public final class TextAnnotationQuadPoints {
    private TextAnnotationQuadPoints() {}

    /**
     * @param sidecar True if the annotation is stored in the sidecar (overlay) coordinate system.
     * @param left Left bound in document units.
     * @param top Top bound in document units.
     * @param right Right bound in document units.
     * @param bottom Bottom bound in document units.
     * @param pageHeightDocUnits Deprecated: kept for call-site compatibility.
     */
    public static PointF[] fromBounds(boolean sidecar,
                                     float left,
                                     float top,
                                     float right,
                                     float bottom,
                                     float pageHeightDocUnits) {
        return new PointF[]{new PointF(left, top), new PointF(right, bottom)};
    }
}
