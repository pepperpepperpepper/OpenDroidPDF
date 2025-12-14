package org.opendroidpdf;

import android.graphics.RectF;

/**
 * Centralizes annotation hit-testing and selection side effects so MuPDFPageView
 * doesn't carry the hit bookkeeping logic.
 */
public class AnnotationHitHelper {
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager;
    private int lastHitAnnotation = 0;

    public interface Host {
        void deselectAnnotation();
        void selectAnnotation(int index, RectF bounds);
        void onTextAnnotationTapped(Annotation annotation);
    }

    public AnnotationHitHelper(org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager) {
        this.selectionManager = selectionManager;
    }

    /**
     * @param rotateOffset 0 for stable ordering (peek), 1 to rotate (like previous behavior in passClickEvent)
     * @param applySelection whether to apply selection side-effects (selection box + text annot callback)
     */
    public Hit handle(float docRelX,
                      float docRelY,
                      Annotation[] annotations,
                      Host host,
                      int rotateOffset,
                      boolean applySelection) {
        if (annotations == null || annotations.length == 0) {
            if (applySelection && host != null) host.deselectAnnotation();
            return Hit.Nothing;
        }

        boolean hit = false;
        int targetIndex = -1;

        for (int i = 0; i < annotations.length; i++) {
            int j = (i + lastHitAnnotation + rotateOffset) % annotations.length;
            if (annotations[j].contains(docRelX, docRelY)) {
                hit = true;
                targetIndex = j;
                if (applySelection) lastHitAnnotation = j;
                break;
            }
        }

        if (!hit) {
            if (applySelection && host != null) host.deselectAnnotation();
            return Hit.Nothing;
        }

        Annotation annotation = annotations[targetIndex];
        Hit result = mapTypeToHit(annotation.type);

        if (applySelection && host != null) {
            host.selectAnnotation(targetIndex, annotation);
            if (annotation.type == Annotation.Type.TEXT || annotation.type == Annotation.Type.FREETEXT) {
                host.onTextAnnotationTapped(annotation);
            }
        }

        return result;
    }

    private static Hit mapTypeToHit(Annotation.Type type) {
        switch (type) {
            case HIGHLIGHT:
            case UNDERLINE:
            case SQUIGGLY:
            case STRIKEOUT:
                return Hit.Annotation;
            case INK:
                return Hit.InkAnnotation;
            case TEXT:
            case FREETEXT:
                return Hit.TextAnnotation;
            default:
                return Hit.Nothing;
        }
    }
}
