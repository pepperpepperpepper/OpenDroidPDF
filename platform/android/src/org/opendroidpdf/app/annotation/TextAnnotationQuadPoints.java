package org.opendroidpdf.app.annotation;

import android.graphics.PointF;

/**
 * Converts text-annotation bounds into quad points in the correct coordinate space.
 *
 * <p>Sidecar anchors use the overlay/document coordinate space (top-left origin). Embedded PDF
 * annotations use PDF coordinates (bottom-left origin), requiring a Y-axis flip.
 */
public final class TextAnnotationQuadPoints {
    private TextAnnotationQuadPoints() {}

    /**
     * @param sidecar True if the annotation is stored in the sidecar (overlay) coordinate system.
     * @param left Left bound in document units.
     * @param top Top bound in document units.
     * @param right Right bound in document units.
     * @param bottom Bottom bound in document units.
     * @param pageHeightDocUnits Page height in document units (only needed when {@code sidecar=false}).
     */
    public static PointF[] fromBounds(boolean sidecar,
                                     float left,
                                     float top,
                                     float right,
                                     float bottom,
                                     float pageHeightDocUnits) {
        if (sidecar) {
            return new PointF[]{new PointF(left, top), new PointF(right, bottom)};
        }

        // Embedded PDF annotations use PDF coordinates (bottom-left origin).
        return new PointF[]{
                new PointF(left, pageHeightDocUnits - top),
                new PointF(right, pageHeightDocUnits - bottom),
        };
    }
}

