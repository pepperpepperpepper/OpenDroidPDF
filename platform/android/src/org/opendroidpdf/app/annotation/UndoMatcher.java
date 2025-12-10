package org.opendroidpdf.app.annotation;

import java.util.ArrayDeque;

/**
 * Pure-Java helper for computing and matching undo signatures for committed annotations.
 * Avoids Android dependencies so we can unit-test on the JVM.
 */
public final class UndoMatcher {

    public static final int TYPE_OTHER = 0;
    public static final int TYPE_INK = 1;
    public static final int TYPE_POPUP = 2;

    private UndoMatcher() {}

    public static final class SimpleAnnotation {
        public final int type;
        public final long objectNumber;
        public final float left, top, right, bottom;
        public final float[][][] arcs; // [stroke][point][xy]

        public SimpleAnnotation(int type, long objectNumber, float left, float top, float right, float bottom, float[][][] arcs) {
            this.type = type;
            this.objectNumber = objectNumber;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.arcs = arcs;
        }
    }

    public static final class UndoItem {
        public final int annotationIndex;
        public final long objectNumber;
        public final float left, top, right, bottom;
        public final float[][][] arcsSignature;

        public UndoItem(int annotationIndex, long objectNumber, float left, float top, float right, float bottom, float[][][] arcsSignature) {
            this.annotationIndex = annotationIndex;
            this.objectNumber = objectNumber;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.arcsSignature = cloneArcs(arcsSignature);
        }
    }

    public static UndoItem choosePushSignature(SimpleAnnotation[] annotations, float[][][] committedArcs) {
        if (annotations == null || annotations.length == 0) return null;

        if (committedArcs != null && committedArcs.length > 0) {
            for (int i = annotations.length - 1; i >= 0; i--) {
                SimpleAnnotation a = annotations[i];
                if (a == null || a.type != TYPE_INK) continue;
                if (arcsApproximatelyEqual(committedArcs, a.arcs)) {
                    return new UndoItem(i, a.objectNumber, a.left, a.top, a.right, a.bottom,
                            a.arcs != null ? a.arcs : committedArcs);
                }
            }
        }

        SimpleAnnotation lastInk = null;
        int lastInkIdx = -1;
        SimpleAnnotation lastPopup = null;
        int lastPopupIdx = -1;
        for (int i = annotations.length - 1; i >= 0; i--) {
            SimpleAnnotation a = annotations[i];
            if (a == null) continue;
            if (a.type == TYPE_INK && lastInk == null) {
                lastInk = a; lastInkIdx = i;
            }
            if (a.type == TYPE_POPUP && lastPopup == null) {
                lastPopup = a; lastPopupIdx = i;
            }
            if (lastInk != null && lastPopup != null) break;
        }
        if (lastInk != null) {
            return new UndoItem(lastInkIdx, lastInk.objectNumber, lastInk.left, lastInk.top, lastInk.right, lastInk.bottom,
                    lastInk.arcs != null ? lastInk.arcs : committedArcs);
        }
        if (lastPopup != null) {
            return new UndoItem(lastPopupIdx, lastPopup.objectNumber, lastPopup.left, lastPopup.top, lastPopup.right, lastPopup.bottom,
                    committedArcs);
        }
        return null;
    }

    public static int findMatchingIndex(SimpleAnnotation[] annotations, UndoItem item) {
        if (item == null || annotations == null) return -1;
        if (item.annotationIndex >= 0 && item.annotationIndex < annotations.length) {
            if (matches(annotations[item.annotationIndex], item)) return item.annotationIndex;
        }
        for (int i = annotations.length - 1; i >= 0; i--) {
            if (matches(annotations[i], item)) return i;
        }
        return -1;
    }

    public static boolean matches(SimpleAnnotation a, UndoItem item) {
        if (a == null || item == null) return false;
        if (a.type != TYPE_INK && a.type != TYPE_POPUP) return false;
        if (item.objectNumber >= 0 && a.objectNumber >= 0 && a.objectNumber == item.objectNumber) return true;
        if (a.type == TYPE_POPUP && item.arcsSignature != null) return true;
        if (item.arcsSignature != null && a.arcs != null && arcsApproximatelyEqual(item.arcsSignature, a.arcs)) return true;
        if (Float.isNaN(item.left)) return item.arcsSignature == null;
        final float e = 5e-1f;
        return Math.abs(a.left - item.left) < e && Math.abs(a.top - item.top) < e &&
               Math.abs(a.right - item.right) < e && Math.abs(a.bottom - item.bottom) < e;
    }

    public static float[][][] cloneArcs(float[][][] arcs) {
        if (arcs == null) return null;
        float[][][] out = new float[arcs.length][][];
        for (int i = 0; i < arcs.length; i++) {
            float[][] stroke = arcs[i];
            if (stroke == null) { out[i] = null; continue; }
            float[][] strokeCopy = new float[stroke.length][];
            for (int j = 0; j < stroke.length; j++) {
                float[] pt = stroke[j];
                if (pt == null) { strokeCopy[j] = null; continue; }
                float[] ptCopy = new float[pt.length];
                System.arraycopy(pt, 0, ptCopy, 0, pt.length);
                strokeCopy[j] = ptCopy;
            }
            out[i] = strokeCopy;
        }
        return out;
    }

    public static boolean arcsApproximatelyEqual(float[][][] expected, float[][][] actual) {
        if (expected == null || actual == null) return false;
        if (expected.length != actual.length) return false;
        final float e = 5e-1f;
        for (int i = 0; i < expected.length; i++) {
            float[][] ea = expected[i];
            float[][] aa = actual[i];
            if (ea == null || aa == null) { if (ea != aa) return false; else continue; }
            if (ea.length != aa.length) return false;
            for (int j = 0; j < ea.length; j++) {
                float[] ep = ea[j];
                float[] ap = aa[j];
                if (ep == null || ap == null) { if (ep != ap) return false; else continue; }
                int len = Math.min(ep.length, ap.length);
                if (len < 2) return false;
                if (Math.abs(ep[0] - ap[0]) > e || Math.abs(ep[1] - ap[1]) > e) return false;
            }
        }
        return true;
    }
}

