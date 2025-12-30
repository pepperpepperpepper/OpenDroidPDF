package org.opendroidpdf.app.reader.gesture;

import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewConfiguration;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.Hit;

/**
 * Centralizes annotation hit-testing and selection side effects so MuPDFPageView
 * doesn't carry the hit bookkeeping logic.
 */
public class AnnotationHitHelper {
    private static final String TAG = "AnnotHitHelper";
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager;
    private int lastHitAnnotation = 0;
    private long lastTappedTextAnnotationObjectId = -1L;
    private int lastTappedTextAnnotationIndex = -1;
    private float lastTappedTextXDoc = 0f;
    private float lastTappedTextYDoc = 0f;
    private long lastTappedTextAtMs = 0L;
    private static final long TEXT_DOUBLE_TAP_WINDOW_MS = ViewConfiguration.getDoubleTapTimeout();
    private static final float TEXT_DOUBLE_TAP_SLOP_MULTIPLIER = 4.0f;
    private static final float TEXT_SELECTED_TAP_SLOP_MULTIPLIER = 2.0f;

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
                      long tapDurationMs,
                      Annotation[] annotations,
                      Host host,
                      int rotateOffset,
                      boolean applySelection,
                      float hitSlopDoc) {
        if (annotations == null || annotations.length == 0) {
            if (applySelection && org.opendroidpdf.BuildConfig.DEBUG) {
                Log.d(TAG, "handle: no annotations at (" + docRelX + "," + docRelY + ")");
            }
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
            if (applySelection && host != null) {
                // Robustness: a text annotation selection can trigger a small settle/scroll correction.
                // If the user taps twice quickly, the second tap may land slightly outside the original
                // bounds. Treat it as an edit request when it's close in doc-space (even if the tap misses).
                long now = SystemClock.uptimeMillis();
                boolean withinWindow = lastTappedTextAtMs > 0L && (now - lastTappedTextAtMs) <= TEXT_DOUBLE_TAP_WINDOW_MS;
                float slop = hitSlopDoc > 0f ? (hitSlopDoc * TEXT_SELECTED_TAP_SLOP_MULTIPLIER) : 0f;
                float dx = docRelX - lastTappedTextXDoc;
                float dy = docRelY - lastTappedTextYDoc;
                boolean withinSlop = slop > 0f && (dx * dx + dy * dy) <= (slop * slop);

                Annotation selected = null;
                try {
                    int idx = selectionManager != null ? selectionManager.selectedIndex() : -1;
                    long obj = selectionManager != null ? selectionManager.selectedObjectNumber() : -1L;
                    if (obj > 0L) {
                        for (Annotation a : annotations) {
                            if (a != null && a.objectNumber == obj) { selected = a; break; }
                        }
                    }
                    if (selected == null && idx >= 0 && idx < annotations.length) {
                        selected = annotations[idx];
                    }
                } catch (Throwable ignore) {
                    selected = null;
                }

                boolean selectedIsText = selected != null
                        && (selected.type == Annotation.Type.TEXT || selected.type == Annotation.Type.FREETEXT);
                boolean tappedHandle = selectedIsText && isTextHandleTap(selected, docRelX, docRelY, hitSlopDoc);
                boolean isShortTap = tapDurationMs < ViewConfiguration.getLongPressTimeout();

                if (selectedIsText && !tappedHandle && withinSlop && isShortTap) {
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        Log.d(TAG, "handle: miss but treating as edit"
                                + " at=(" + docRelX + "," + docRelY + ")"
                                + " prev=(" + lastTappedTextXDoc + "," + lastTappedTextYDoc + ")"
                                + " slop=" + slop
                                + " selectedType=" + selected.type
                                + " obj=" + selected.objectNumber
                                + " withinWindow=" + withinWindow
                                + " dtMs=" + tapDurationMs);
                    }
                    try { host.onTextAnnotationTapped(selected); } catch (Throwable ignore) {}
                    return Hit.TextAnnotation;
                }

                if (org.opendroidpdf.BuildConfig.DEBUG) {
                    StringBuilder b = new StringBuilder();
                    b.append("handle: miss at=(").append(docRelX).append(",").append(docRelY)
                            .append(") slopDoc=").append(hitSlopDoc)
                            .append(" annots=").append(annotations != null ? annotations.length : 0)
                            .append(" selIdx=").append(selectionManager != null ? selectionManager.selectedIndex() : -1)
                            .append(" selObj=").append(selectionManager != null ? selectionManager.selectedObjectNumber() : -1L);
                    int logged = 0;
                    if (annotations != null) {
                        for (Annotation a : annotations) {
                            if (a == null) continue;
                            if (a.type != Annotation.Type.FREETEXT && a.type != Annotation.Type.TEXT) continue;
                            b.append(" textAnnot{type=").append(a.type)
                                    .append(" obj=").append(a.objectNumber)
                                    .append(" rect=(").append(a.left).append(",").append(a.top)
                                    .append(" ").append(a.right).append(",").append(a.bottom).append(")}");
                            if (++logged >= 3) break;
                        }
                    }
                    Log.d(TAG, b.toString());
                }

                host.deselectAnnotation();
            }
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
            final long prevSelectedObjectId = selectionManager != null ? selectionManager.selectedObjectNumber() : -1L;
            final int prevSelectedIndex = selectionManager != null ? selectionManager.selectedIndex() : -1;
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

                boolean wasSelected;
                if (objectId > 0L) {
                    wasSelected = objectId == prevSelectedObjectId;
                } else {
                    wasSelected = targetIndex == prevSelectedIndex;
                }

                lastTappedTextAnnotationObjectId = objectId > 0L ? objectId : -1L;
                lastTappedTextAnnotationIndex = targetIndex;
                lastTappedTextXDoc = docRelX;
                lastTappedTextYDoc = docRelY;
                lastTappedTextAtMs = now;
                boolean tappedHandle = isTextHandleTap(annotation, docRelX, docRelY, hitSlopDoc);
                boolean isShortTap = tapDurationMs < ViewConfiguration.getLongPressTimeout();
                if (wasSelected && !tappedHandle && isShortTap) {
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        Log.d(TAG, "handle: tap-on-selected edit type=" + annotation.type
                                + " obj=" + objectId + " idx=" + targetIndex
                                + " at=(" + docRelX + "," + docRelY + ")"
                                + " dtMs=" + tapDurationMs);
                    }
                    try { host.onTextAnnotationTapped(annotation); } catch (Throwable ignore) {}
                } else if (isSecondTap && !tappedHandle) {
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        Log.d(TAG, "handle: second-tap edit type=" + annotation.type
                                + " obj=" + objectId + " idx=" + targetIndex
                                + " at=(" + docRelX + "," + docRelY + ")");
                    }
                    try { host.onTextAnnotationTapped(annotation); } catch (Throwable ignore) {}
                } else if (org.opendroidpdf.BuildConfig.DEBUG) {
                    Log.d(TAG, "handle: selected text annot type=" + annotation.type
                            + " obj=" + objectId + " idx=" + targetIndex
                            + " rect=(" + annotation.left + "," + annotation.top
                            + " " + annotation.right + "," + annotation.bottom + ")"
                            + " at=(" + docRelX + "," + docRelY + ")");
                }
            } else {
                lastTappedTextAnnotationObjectId = -1L;
                lastTappedTextAnnotationIndex = -1;
                lastTappedTextAtMs = 0L;
            }
        }

        return result;
    }

    private static boolean isTextHandleTap(Annotation annotation, float docX, float docY, float hitSlopDoc) {
        if (annotation == null) return false;
        if (annotation.type != Annotation.Type.TEXT && annotation.type != Annotation.Type.FREETEXT) return false;
        if (hitSlopDoc <= 0f) return false;

        final float half = hitSlopDoc;
        final float left = annotation.left;
        final float top = annotation.top;
        final float right = annotation.right;
        final float bottom = annotation.bottom;
        final float midX = (left + right) * 0.5f;

        return withinSquare(docX, docY, left, top, half)
                || withinSquare(docX, docY, right, top, half)
                || withinSquare(docX, docY, left, bottom, half)
                || withinSquare(docX, docY, right, bottom, half)
                || withinSquare(docX, docY, midX, top, half);
    }

    private static boolean withinSquare(float x, float y, float cx, float cy, float half) {
        return x >= (cx - half) && x <= (cx + half) && y >= (cy - half) && y <= (cy + half);
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
