package org.opendroidpdf.app.reader.gesture;

import android.graphics.RectF;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.Hit;

/**
 * Centralizes annotation hit-testing and selection side effects so MuPDFPageView
 * doesn't carry the hit bookkeeping logic.
 */
public class AnnotationHitHelper {
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager;
    private int lastHitAnnotation = 0;
    private int lastTappedTextAnnotation = -1;

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
            if (applySelection) lastTappedTextAnnotation = -1;
            return Hit.Nothing;
        }

        boolean hit = false;
        int targetIndex = -1;

        for (int i = 0; i < annotations.length; i++) {
            int j = (i + lastHitAnnotation + rotateOffset) % annotations.length;
            Annotation candidate = annotations[j];
            if (candidate != null && candidate.contains(docRelX, docRelY)) {
                hit = true;
                targetIndex = j;
                if (applySelection) lastHitAnnotation = j;
                break;
            }
        }

        if (!hit) {
            if (applySelection && host != null) host.deselectAnnotation();
            if (applySelection) lastTappedTextAnnotation = -1;
            return Hit.Nothing;
        }

        Annotation annotation = annotations[targetIndex];
        if (annotation == null) {
            if (applySelection && host != null) host.deselectAnnotation();
            if (applySelection) lastTappedTextAnnotation = -1;
            return Hit.Nothing;
        }
        Hit result = mapTypeToHit(annotation.type);

        if (applySelection && host != null) {
            host.selectAnnotation(targetIndex, annotation);
            if (annotation.type == Annotation.Type.TEXT || annotation.type == Annotation.Type.FREETEXT) {
                // Keep tap-to-select as the default so users can move/delete without triggering
                // an editor dialog. A second tap on the same text annotation requests editing.
                boolean isSecondTap = targetIndex == lastTappedTextAnnotation;
                lastTappedTextAnnotation = targetIndex;
                if (isSecondTap) {
                    host.onTextAnnotationTapped(annotation);
                }
            } else {
                lastTappedTextAnnotation = -1;
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
