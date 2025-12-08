package org.opendroidpdf.app.annotation;

import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.core.MuPdfController;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Encapsulates the committed-ink undo stack so {@link org.opendroidpdf.MuPDFPageView}
 * can delegate push/undo logic instead of owning stack state directly.
 */
public class InkUndoController {

    public interface Host {
        int pageNumber();
        void onInkStackMutated();
    }

    /**
     * Minimal backend surface so tests can supply a fake without a real MuPdfController.
     */
    public interface Backend {
        Annotation[] annotations(int page);
        void deleteAnnotation(int page, int index);
        void markDocumentDirty();
    }

    private final Host host;
    private final Backend backend;
    private final boolean log;
    private final String tag;
    private final ArrayDeque<InkUndoItem> stack = new ArrayDeque<>();

    public InkUndoController(Host host, MuPdfController controller, String logTag, boolean logUndo) {
        this(host, wrap(controller), logTag, logUndo);
    }

    /**
     * Test-friendly constructor that accepts a simple backend so we can
     * exercise stack behavior without a full MuPdfController.
     */
    public InkUndoController(Host host, Backend backend, String logTag, boolean logUndo) {
        this.host = host;
        this.backend = backend;
        this.tag = logTag;
        this.log = logUndo;
    }

    private static Backend wrap(final MuPdfController controller) {
        return new Backend() {
            @Override
            public Annotation[] annotations(int page) {
                return controller.annotations(page);
            }

            @Override
            public void deleteAnnotation(int page, int index) {
                controller.deleteAnnotation(page, index);
            }

            @Override
            public void markDocumentDirty() {
                controller.markDocumentDirty();
            }
        };
    }

    public void recordCommittedInkForUndo(PointF[][] arcs) {
        pushSnapshot(arcs);
    }

    public boolean undoLast() {
        InkUndoItem item = stack.peek();
        if (log) {
            logInkUndoItem("[undo] attempt", item);
        }
        if (item == null) {
            return false;
        }
        try {
            Annotation[] annotations = backend.annotations(host.pageNumber());
            if (annotations != null && item.annotationIndex < annotations.length) {
                Annotation annot = annotations[item.annotationIndex];
                if (item.matches(annot)) {
                    backend.deleteAnnotation(host.pageNumber(), item.annotationIndex);
                    backend.markDocumentDirty();
                    host.onInkStackMutated();
                    stack.pop();
                    if (log) {
                        Log.d(tag, "[undo] success via primary index; new stack size=" + stack.size());
                    }
                    return true;
                }
            }

            if (annotations != null) {
                for (int i = annotations.length - 1; i >= 0; i--) {
                    Annotation annot = annotations[i];
                    if (item.matches(annot)) {
                        backend.deleteAnnotation(host.pageNumber(), i);
                        backend.markDocumentDirty();
                        host.onInkStackMutated();
                        stack.pop();
                        if (log) {
                            Log.d(tag, "[undo] success via scan idx=" + i + "; new stack size=" + stack.size());
                        }
                        return true;
                    }
                }
            }
            if (log) {
                Log.d(tag, "[undo] no matching annotation found");
            }
        } catch (Throwable t) {
            if (log) {
                Log.e(tag, "[undo] undoCommittedInk exception", t);
            }
        }
        return false;
    }

    public boolean hasUndo() {
        return !stack.isEmpty();
    }

    public int stackSize() {
        return stack.size();
    }

    public void clear() {
        stack.clear();
    }

    private void pushSnapshot(PointF[][] committedArcs) {
        if (log) {
            Log.d(tag, "[undo] push start page=" + host.pageNumber()
                    + " stackSize=" + stack.size()
                    + " committedPoints=" + countPoints(committedArcs));
        }
        try {
            Annotation[] annotations = backend.annotations(host.pageNumber());
            if (log) {
                Log.d(tag, "[undo] push annotations=" + (annotations == null ? "null" : annotations.length));
                if (annotations != null) {
                    Log.d(tag, "[undo] push annotation types " + describeAnnotations(annotations));
                }
            }
            if (annotations == null || annotations.length == 0) {
                return;
            }
            if (committedArcs != null && committedArcs.length > 0) {
                for (int i = annotations.length - 1; i >= 0; i--) {
                    Annotation candidate = annotations[i];
                    if (candidate == null || candidate.type != Annotation.Type.INK) {
                        continue;
                    }
                    if (arcsApproximatelyEqual(committedArcs, candidate.arcs)) {
                        InkUndoItem item = new InkUndoItem(i, candidate, committedArcs);
                        stack.push(item);
                        if (log) {
                            logAnnotationGeometry("[undo] push matched INK idx=" + i, candidate, committedArcs);
                            logInkUndoItem("[undo] stack push", item);
                        }
                        return;
                    }
                }
            }
            Annotation fallback = null;
            int fallbackIndex = -1;
            for (int i = annotations.length - 1; i >= 0; i--) {
                Annotation candidate = annotations[i];
                if (candidate == null) {
                    continue;
                }
                if (candidate.type == Annotation.Type.INK) {
                    InkUndoItem item = new InkUndoItem(i, candidate, committedArcs);
                    stack.push(item);
                    if (log) {
                        logAnnotationGeometry("[undo] push fallback INK idx=" + i, candidate, committedArcs);
                        logInkUndoItem("[undo] stack push", item);
                    }
                    return;
                }
                if (candidate.type == Annotation.Type.POPUP && fallback == null) {
                    fallback = candidate;
                    fallbackIndex = i;
                    if (log) {
                        logAnnotationGeometry("[undo] potential POPUP fallback idx=" + i, candidate, committedArcs);
                    }
                } else if (log && candidate.type != Annotation.Type.INK) {
                    logAnnotationGeometry("[undo] potential non-ink candidate idx=" + i, candidate, committedArcs);
                }
            }
            if (fallback != null) {
                InkUndoItem item = new InkUndoItem(fallbackIndex, fallback, committedArcs);
                stack.push(item);
                if (log) {
                    logAnnotationGeometry("[undo] push fallback POPUP idx=" + fallbackIndex, fallback, committedArcs);
                    logInkUndoItem("[undo] stack push", item);
                }
            } else if (log) {
                Log.d(tag, "[undo] push failed to locate candidate");
            }
        } catch (Throwable t) {
            if (log) {
                Log.e(tag, "[undo] push exception", t);
            }
        }
    }

    private static int countPoints(PointF[][] arcs) {
        if (arcs == null) {
            return 0;
        }
        int count = 0;
        for (PointF[] arc : arcs) {
            if (arc == null) {
                continue;
            }
            count += arc.length;
        }
        return count;
    }

    private static PointF[][] cloneArcs(PointF[][] arcs) {
        if (arcs == null) {
            return null;
        }
        PointF[][] copy = new PointF[arcs.length][];
        for (int i = 0; i < arcs.length; i++) {
            PointF[] arc = arcs[i];
            if (arc == null) {
                copy[i] = null;
                continue;
            }
            PointF[] arcCopy = new PointF[arc.length];
            for (int j = 0; j < arc.length; j++) {
                PointF pt = arc[j];
                arcCopy[j] = pt == null ? null : new PointF(pt.x, pt.y);
            }
            copy[i] = arcCopy;
        }
        return copy;
    }

    private static boolean arcsApproximatelyEqual(PointF[][] expected, PointF[][] actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.length != actual.length) {
            return false;
        }
        final float epsilon = 5e-1f;
        for (int i = 0; i < expected.length; i++) {
            PointF[] expArc = expected[i];
            PointF[] actArc = actual[i];
            if (expArc == null || actArc == null) {
                if (expArc != actArc) {
                    return false;
                }
                continue;
            }
            if (expArc.length != actArc.length) {
                return false;
            }
            for (int j = 0; j < expArc.length; j++) {
                PointF expPt = expArc[j];
                PointF actPt = actArc[j];
                if (expPt == null || actPt == null) {
                    if (expPt != actPt) {
                        return false;
                    }
                    continue;
                }
                if (Math.abs(expPt.x - actPt.x) > epsilon || Math.abs(expPt.y - actPt.y) > epsilon) {
                    return false;
                }
            }
        }
        return true;
    }

    private static float computeMaxPointDelta(PointF[][] expected, PointF[][] actual) {
        if (expected == null || actual == null) {
            return Float.NaN;
        }
        if (expected.length != actual.length) {
            return Float.POSITIVE_INFINITY;
        }
        float max = 0f;
        for (int i = 0; i < expected.length; i++) {
            PointF[] expArc = expected[i];
            PointF[] actArc = actual[i];
            if (expArc == null || actArc == null) {
                if (expArc != actArc) {
                    return Float.POSITIVE_INFINITY;
                }
                continue;
            }
            if (expArc.length != actArc.length) {
                return Float.POSITIVE_INFINITY;
            }
            for (int j = 0; j < expArc.length; j++) {
                PointF expPt = expArc[j];
                PointF actPt = actArc[j];
                if (expPt == null || actPt == null) {
                    if (expPt != actPt) {
                        return Float.POSITIVE_INFINITY;
                    }
                    continue;
                }
                float dx = Math.abs(expPt.x - actPt.x);
                float dy = Math.abs(expPt.y - actPt.y);
                if (dx > max) {
                    max = dx;
                }
                if (dy > max) {
                    max = dy;
                }
            }
        }
        return max;
    }

    private void logInkUndoItem(String stage, InkUndoItem item) {
        if (!log) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(stage)
                .append(" page=").append(host.pageNumber())
                .append(" stack=").append(stack.size());
        if (item == null) {
            sb.append(" item=null");
            Log.d(tag, sb.toString());
            return;
        }
        sb.append(" index=").append(item.annotationIndex)
                .append(" obj=").append(item.objectNumber);
        if (item.bounds != null) {
            sb.append(" bounds=").append(String.format(Locale.US, "[%.2f,%.2f,%.2f,%.2f]", item.bounds.left, item.bounds.top, item.bounds.right, item.bounds.bottom));
        } else {
            sb.append(" bounds=null");
        }
        sb.append(" signaturePoints=").append(countPoints(item.arcsSignature));
        Log.d(tag, sb.toString());
    }

    private void logAnnotationGeometry(String stage, Annotation annotation, PointF[][] referenceArcs) {
        if (!log) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(stage)
                .append(" page=").append(host.pageNumber())
                .append(" stack=").append(stack.size());
        if (annotation == null) {
            sb.append(" annotation=null");
            Log.d(tag, sb.toString());
            return;
        }
        sb.append(" type=").append(annotation.type)
                .append(" rawType=").append(annotation.rawType)
                .append(" obj=").append(annotation.objectNumber)
                .append(" bounds=").append(String.format(Locale.US, "[%.2f,%.2f,%.2f,%.2f]", annotation.left, annotation.top, annotation.right, annotation.bottom))
                .append(" points=").append(countPoints(annotation.arcs));
        if (referenceArcs != null && annotation.arcs != null) {
            float maxDelta = computeMaxPointDelta(referenceArcs, annotation.arcs);
            sb.append(" maxDelta=").append(maxDelta);
        }
        Log.d(tag, sb.toString());
    }

    private static String describeAnnotations(Annotation[] annotations) {
        StringBuilder sb = new StringBuilder("[");
        if (annotations != null) {
            for (int i = 0; i < annotations.length; i++) {
                Annotation annot = annotations[i];
                if (annot == null) {
                    sb.append(i).append(":null");
                } else {
                    sb.append(i).append(":").append(annot.type)
                            .append("(raw=").append(annot.rawType)
                            .append(",obj=").append(annot.objectNumber).append(")");
                }
                if (i + 1 < annotations.length) {
                    sb.append(", ");
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }

    private static final class InkUndoItem {
        final int annotationIndex;
        final RectF bounds;
        final PointF[][] arcsSignature;
        final long objectNumber;

        InkUndoItem(int annotationIndex, Annotation annotation, PointF[][] sourceArcs) {
            this.annotationIndex = annotationIndex;
            this.bounds = annotation != null ? new RectF(annotation) : null;
            PointF[][] candidate = (annotation != null && annotation.arcs != null)
                    ? annotation.arcs
                    : sourceArcs;
            this.arcsSignature = cloneArcs(candidate);
            this.objectNumber = annotation != null ? annotation.objectNumber : -1L;
        }

        boolean matches(Annotation annotation) {
            if (annotation == null) {
                return false;
            }
            Annotation.Type type = annotation.type;
            if (type != Annotation.Type.INK && type != Annotation.Type.POPUP) {
                return false;
            }
            if (objectNumber >= 0 && annotation.objectNumber >= 0) {
                if (annotation.objectNumber == objectNumber) {
                    return true;
                }
            }
            if (type == Annotation.Type.POPUP && arcsSignature != null) {
                return true;
            }
            if (arcsSignature != null && annotation.arcs != null
                    && arcsApproximatelyEqual(arcsSignature, annotation.arcs)) {
                return true;
            }
            if (bounds == null) {
                return arcsSignature == null;
            }
            final float epsilon = 5e-1f;
            return Math.abs(annotation.left - bounds.left) < epsilon
                    && Math.abs(annotation.top - bounds.top) < epsilon
                    && Math.abs(annotation.right - bounds.right) < epsilon
                    && Math.abs(annotation.bottom - bounds.bottom) < epsilon;
        }
    }
}
