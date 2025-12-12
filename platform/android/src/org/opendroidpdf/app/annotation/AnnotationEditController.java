package org.opendroidpdf.app.annotation;

import android.graphics.PointF;

import org.opendroidpdf.Annotation;

/**
 * Handles re-editing existing annotations (e.g., ink) without
 * baking the logic into MuPDFPageView.
 */
public class AnnotationEditController {
    public interface Host {
        void setDraw(PointF[][] arcs);
        void setModeDrawing();
        void deleteSelectedAnnotation();
    }

    public void editIfSupported(Annotation annot, Host host) {
        if (annot == null || host == null) return;
        switch (annot.type) {
            case INK:
                if (annot.arcs != null) {
                    host.setDraw(annot.arcs);
                    host.setModeDrawing();
                    host.deleteSelectedAnnotation();
                }
                break;
            default:
                // other annotation types may be supported later
                break;
        }
    }
}

