package org.opendroidpdf.app.reader.gesture;

import android.graphics.RectF;
import android.os.SystemClock;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.Hit;

/**
 * Centralizes annotation hit-testing and selection side effects so MuPDFPageView
 * doesn't carry the hit bookkeeping logic.
 */
public class AnnotationHitHelper {
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager;
    private int lastHitAnnotation = 0;
    private long lastTappedTextAnnotationObjectId = -1L;
    private int lastTappedTextAnnotationIndex = -1;
    private long lastTappedTextAtMs = 0L;
    private static final long TEXT_DOUBLE_TAP_WINDOW_MS = 900L;

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
                      boolean applySelection,
                      float hitSlopDoc) {
        if (annotations == null || annotations.length == 0) {
            if (applySelection && host != null) host.deselectAnnotation();
            if (applySelection) {
                lastTappedTextAnnotationObjectId = -1L;
                lastTappedTextAnnotationIndex = -1;
                lastTappedTextAtMs = 0L;
            }
            return Hit.Nothing;
        }

        boolean hit = false;
        int targetIndex = -1;

        for (int i = 0; i < annotations.length; i++) {
            int j = (i + lastHitAnnotation + rotateOffset) % annotations.length;
            Annotation candidate = annotations[j];
            if (candidate != null && hitBounds(candidate, docRelX, docRelY, hitSlopDoc)) {
                hit = true;
                targetIndex = j;
                if (applySelection) lastHitAnnotation = j;
                break;
            }
        }

        if (!hit) {
            if (applySelection && host != null) host.deselectAnnotation();
            if (applySelection) {
                lastTappedTextAnnotationObjectId = -1L;
                lastTappedTextAnnotationIndex = -1;
                lastTappedTextAtMs = 0L;
            }
            return Hit.Nothing;
        }

        Annotation annotation = annotations[targetIndex];
        if (annotation == null) {
            if (applySelection && host != null) host.deselectAnnotation();
            if (applySelection) {
                lastTappedTextAnnotationObjectId = -1L;
                lastTappedTextAnnotationIndex = -1;
                lastTappedTextAtMs = 0L;
            }
            return Hit.Nothing;
        }
        Hit result = mapTypeToHit(annotation.type);

        if (applySelection && host != null) {
            try {
                host.selectAnnotation(targetIndex, annotation);
            } catch (Throwable ignore) {
                try { host.deselectAnnotation(); } catch (Throwable ignore2) {}
                lastTappedTextAnnotationObjectId = -1L;
                lastTappedTextAnnotationIndex = -1;
                lastTappedTextAtMs = 0L;
                return Hit.Nothing;
            }
            if (annotation.type == Annotation.Type.TEXT || annotation.type == Annotation.Type.FREETEXT) {
                // Keep tap-to-select as the default so users can move/delete without triggering
                // an editor dialog. A second tap within a short window requests editing.
                long now = SystemClock.uptimeMillis();
                long objectId = annotation.objectNumber;
                boolean isSecondTap = false;
                if (objectId > 0L) {
                    isSecondTap = objectId == lastTappedTextAnnotationObjectId;
                } else {
                    isSecondTap = targetIndex == lastTappedTextAnnotationIndex;
                }
                isSecondTap = isSecondTap && (now - lastTappedTextAtMs) <= TEXT_DOUBLE_TAP_WINDOW_MS;

                lastTappedTextAnnotationObjectId = objectId > 0L ? objectId : -1L;
                lastTappedTextAnnotationIndex = targetIndex;
                lastTappedTextAtMs = now;
                if (isSecondTap) {
                    try { host.onTextAnnotationTapped(annotation); } catch (Throwable ignore) {}
                }
            } else {
                lastTappedTextAnnotationObjectId = -1L;
                lastTappedTextAnnotationIndex = -1;
                lastTappedTextAtMs = 0L;
            }
        }

        return result;
    }

    private static boolean hitBounds(Annotation candidate, float x, float y, float slopDoc) {
        if (candidate.contains(x, y)) return true;
        if (slopDoc <= 0f) return false;

        // FreeText/Text are often hard to hit precisely (small glyph bounds), so allow a small slop.
        if (candidate.type != Annotation.Type.TEXT && candidate.type != Annotation.Type.FREETEXT) return false;

        return x >= (candidate.left - slopDoc)
                && x <= (candidate.right + slopDoc)
                && y >= (candidate.top - slopDoc)
                && y <= (candidate.bottom + slopDoc);
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
