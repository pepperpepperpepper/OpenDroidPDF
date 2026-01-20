package org.opendroidpdf.app.annotation;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.core.MuPdfController;

import java.util.HashSet;
import java.util.Set;

final class TextAnnotationPageEmbeddedMarkupOps {
    private final TextAnnotationPageDelegate.Host host;
    private final TextAnnotationUndoController undoController;

    TextAnnotationPageEmbeddedMarkupOps(@NonNull TextAnnotationPageDelegate.Host host,
                                        @NonNull TextAnnotationUndoController undoController) {
        this.host = host;
        this.undoController = undoController;
    }

    boolean addEmbeddedMarkupAnnotationWithUndo(int pageNumber,
                                                @NonNull Annotation.Type type,
                                                @Nullable PointF[] quadPoints,
                                                @Nullable Runnable onComplete) {
        if (type == null) return false;
        if (host.sidecarSessionOrNull() != null) return false;
        if (quadPoints == null || quadPoints.length == 0) return false;

        final MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        final int page = pageNumber;
        final PointF[] quadCopy = copyQuadPoints(quadPoints);
        final RectF boundsDoc = boundsFromQuadPointsOrNull(quadCopy);

        AppCoroutines.launchIo(AppCoroutines.ioScope(), () -> {
            long newObjectId = -1L;
            try {
                Annotation[] before = controller.annotations(page);
                Set<Long> beforeIds = new HashSet<>();
                if (before != null) {
                    for (Annotation a : before) {
                        if (a == null) continue;
                        if (a.objectNumber > 0L) beforeIds.add(a.objectNumber);
                    }
                }

                controller.addMarkupAnnotation(page, quadCopy, type);

                Annotation[] after = controller.annotations(page);
                Annotation created = findNewMarkup(after, beforeIds, type, boundsDoc);
                if (created != null) newObjectId = created.objectNumber;
            } catch (Throwable ignore) {
                newObjectId = -1L;
            }

            final long finalId = newObjectId;
            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                if (finalId > 0L) {
                    undoController.push(new EmbeddedMarkupPresenceOp(page, type, quadCopy, boundsDoc, finalId, true));
                }
                try {
                    host.requestFullRedrawAfterNextAnnotationLoad();
                    host.discardRenderedPage();
                } catch (Throwable ignore) {
                }
                if (onComplete != null) onComplete.run();
            });
        });

        return true;
    }

    @NonNull
    private static PointF[] copyQuadPoints(@NonNull PointF[] quadPoints) {
        PointF[] out = new PointF[quadPoints.length];
        for (int i = 0; i < quadPoints.length; i++) {
            PointF p = quadPoints[i];
            out[i] = p != null ? new PointF(p.x, p.y) : new PointF(0f, 0f);
        }
        return out;
    }

    @Nullable
    private static RectF boundsFromQuadPointsOrNull(@NonNull PointF[] quadPoints) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (PointF p : quadPoints) {
            if (p == null) continue;
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        if (minX == Float.MAX_VALUE || minY == Float.MAX_VALUE) return null;
        return new RectF(minX, minY, maxX, maxY);
    }

    @Nullable
    private static Annotation findNewMarkup(@Nullable Annotation[] after,
                                            @NonNull Set<Long> beforeIds,
                                            @NonNull Annotation.Type type,
                                            @Nullable RectF boundsHint) {
        if (after == null || after.length == 0) return null;

        if (boundsHint == null) {
            for (Annotation a : after) {
                if (a == null) continue;
                if (a.type != type) continue;
                if (a.objectNumber <= 0L) continue;
                if (beforeIds.contains(a.objectNumber)) continue;
                return a;
            }
            return null;
        }

        Annotation best = null;
        float bestDist = Float.MAX_VALUE;
        float cx = boundsHint.centerX();
        float cy = boundsHint.centerY();
        for (Annotation a : after) {
            if (a == null) continue;
            if (a.type != type) continue;
            if (a.objectNumber <= 0L) continue;
            if (beforeIds.contains(a.objectNumber)) continue;

            float dx = a.centerX() - cx;
            float dy = a.centerY() - cy;
            float dist = (dx * dx) + (dy * dy);
            if (best == null || dist < bestDist) {
                best = a;
                bestDist = dist;
            }
        }
        return best;
    }

    private final class EmbeddedMarkupPresenceOp implements TextAnnotationUndoController.Op {
        private final int pageNumber;
        @NonNull private final Annotation.Type type;
        @NonNull private final PointF[] quadPoints;
        @Nullable private final RectF boundsHint;
        private long liveObjectId;
        private boolean present;

        EmbeddedMarkupPresenceOp(int pageNumber,
                                 @NonNull Annotation.Type type,
                                 @NonNull PointF[] quadPoints,
                                 @Nullable RectF boundsHint,
                                 long liveObjectId,
                                 boolean present) {
            this.pageNumber = pageNumber;
            this.type = type;
            this.quadPoints = quadPoints;
            this.boundsHint = boundsHint;
            this.liveObjectId = liveObjectId;
            this.present = present;
        }

        private void toggle() {
            if (host.sidecarSessionOrNull() != null) return;

            MuPdfController controller = host.muPdfControllerOrNull();
            if (controller == null) return;

            if (present) {
                if (liveObjectId > 0L) {
                    try { controller.deleteAnnotationByObjectNumber(pageNumber, liveObjectId); } catch (Throwable ignore) {}
                }
                liveObjectId = -1L;
                present = false;
            } else {
                long newId = -1L;
                try {
                    Annotation[] before = controller.annotations(pageNumber);
                    Set<Long> beforeIds = new HashSet<>();
                    if (before != null) {
                        for (Annotation a : before) {
                            if (a == null) continue;
                            if (a.objectNumber > 0L) beforeIds.add(a.objectNumber);
                        }
                    }

                    controller.addMarkupAnnotation(pageNumber, quadPoints, type);

                    Annotation[] after = controller.annotations(pageNumber);
                    Annotation created = findNewMarkup(after, beforeIds, type, boundsHint);
                    if (created != null) newId = created.objectNumber;
                } catch (Throwable ignore) {
                    newId = -1L;
                }
                liveObjectId = newId;
                present = (newId > 0L);
            }

            try {
                host.requestFullRedrawAfterNextAnnotationLoad();
                host.discardRenderedPage();
            } catch (Throwable ignore) {
            }
            try { host.loadAnnotations(); } catch (Throwable ignore) {}
            try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        }

        @Override public void undo() { toggle(); }
        @Override public void redo() { toggle(); }
    }
}

