package org.opendroidpdf.app.drawing;

import android.graphics.PointF;
import android.os.SystemClock;
import android.util.Log;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.PointFMath;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.core.MuPdfController;

import androidx.annotation.Nullable;

import java.util.List;

final class InkExistingInkEraser {
    private final InkController.Host host;
    private final MuPdfController muPdfController;
    @Nullable private final SidecarAnnotationSession sidecarSession;
    private final InkCommitOps commitOps;
    private final String tag;
    private final Runnable updateUndoCache;

    @Nullable private SidecarInkStroke sidecarEditingStroke = null;

    // When true, the current erase gesture is editing an existing ink annotation
    // (loaded into DrawingController) and should auto-commit on erase end.
    private boolean erasingExistingInkAnnotation = false;
    private long lastEraseInkHitAttemptUptimeMs = 0L;

    InkExistingInkEraser(InkController.Host host,
                         MuPdfController muPdfController,
                         @Nullable SidecarAnnotationSession sidecarSession,
                         InkCommitOps commitOps,
                         String tag,
                         Runnable updateUndoCache) {
        this.host = host;
        this.muPdfController = muPdfController;
        this.sidecarSession = sidecarSession;
        this.commitOps = commitOps;
        this.tag = tag;
        this.updateUndoCache = updateUndoCache;
    }

    boolean isEditingExistingInk() {
        return erasingExistingInkAnnotation;
    }

    void resetEraserSession() {
        erasingExistingInkAnnotation = false;
        lastEraseInkHitAttemptUptimeMs = 0L;
        sidecarEditingStroke = null;
    }

    void clearEditingStrokeOnly() {
        sidecarEditingStroke = null;
    }

    @Nullable
    SidecarInkStroke sidecarEditingStrokeOrNull() {
        return sidecarEditingStroke;
    }

    void setSidecarEditingStroke(@Nullable SidecarInkStroke sidecarEditingStroke) {
        this.sidecarEditingStroke = sidecarEditingStroke;
    }

    /**
     * Called by the PageView eraser gesture pipeline (from {@link org.opendroidpdf.MuPDFPageView})
     * to opportunistically switch into "edit committed ink" mode when the user erases over an
     * existing ink annotation.
     *
     * This keeps the "who owns ink geometry right now?" state machine here instead of in PageView.
     */
    void onStartEraseGesture(float viewX, float viewY, float scale, int viewLeft, int viewTop) {
        lastEraseInkHitAttemptUptimeMs = 0L;

        // If we have pending ink, commit it first so the eraser can operate on *any* committed ink
        // annotation (across pen size/color changes) rather than being blocked by overlay state.
        //
        // If commit fails, we fall back to erasing pending strokes only.
        if (!erasingExistingInkAnnotation && host.drawingController().getDrawingSize() > 0) {
            InkCommitOps.Result committed = commitOps.saveDraw(null, sidecarEditingStroke);
            sidecarEditingStroke = committed.sidecarEditingStrokeAfter;
            if (!committed.committed && BuildConfig.DEBUG) {
                Log.w(tag, "startErase: pending ink commit failed; will erase pending strokes only");
            }
        }

        maybeBeginErasingExistingInkAt(viewX, viewY, scale, viewLeft, viewTop);
    }

    void onContinueEraseGesture(float viewX, float viewY, float scale, int viewLeft, int viewTop) {
        if (erasingExistingInkAnnotation) return;
        if (host.drawingController().getDrawingSize() != 0) return;

        long now = SystemClock.uptimeMillis();
        if (now - lastEraseInkHitAttemptUptimeMs >= 80L) {
            lastEraseInkHitAttemptUptimeMs = now;
            maybeBeginErasingExistingInkAt(viewX, viewY, scale, viewLeft, viewTop);
        }
    }

    void onFinishEraseGesture() {
        if (!erasingExistingInkAnnotation) return;
        try {
            // If anything remains after erasing, commit it immediately so the user sees the result.
            if (host.drawingController().getDrawingSize() > 0) {
                InkCommitOps.Result committed = commitOps.saveDraw(null, sidecarEditingStroke);
                sidecarEditingStroke = committed.sidecarEditingStrokeAfter;
            } else {
                host.drawingController().cancelDraw();
                // Sidecar: deleting an entire committed stroke should still be undoable.
                if (sidecarSession != null && sidecarEditingStroke != null) {
                    sidecarSession.recordUndoInkReplaced(host.pageNumber(), sidecarEditingStroke, java.util.Collections.emptyList());
                    sidecarEditingStroke = null;
                    updateUndoCache.run();
                }
            }
        } catch (Throwable ignore) {
            // Avoid crashing during erase; leaving the doc in a consistent state is best-effort.
        } finally {
            erasingExistingInkAnnotation = false;
        }
    }

    private void maybeBeginErasingExistingInkAt(final float x, final float y, float scale, int viewLeft, int viewTop) {
        if (erasingExistingInkAnnotation) return;
        if (host.drawingController().getDrawingSize() != 0) return;

        if (scale == 0f) return;

        SidecarAnnotationSession sidecar = sidecarSession;
        if (sidecar != null) {
            maybeBeginErasingSidecarInkAt(sidecar, x, y, scale, viewLeft, viewTop);
            return;
        }

        final Annotation[] annotations;
        try {
            annotations = muPdfController.annotations(host.pageNumber());
        } catch (Throwable ignore) {
            return; // Best-effort: fall back to erasing pending strokes only.
        }
        if (annotations == null || annotations.length == 0) return;

        // Convert touch point to page doc coordinates (same space as annotation bounds/arcs).
        final float docRelX = (x - viewLeft) / scale;
        final float docRelY = (y - viewTop) / scale;

        // Prefer arc-based hit-testing: some ink annotations can have oversized/incorrect Rects
        // (especially across thickness/color changes), which makes rect-based Hit.InkAnnotation
        // selection latch to the wrong annotation and "only erase the last stroke".
        final float hitRadiusDoc = approxHitRadiusDoc(scale);
        final int inkIndex = findInkAnnotationHitIndex(annotations, docRelX, docRelY, hitRadiusDoc);
        if (inkIndex < 0) {
            if (BuildConfig.DEBUG) {
                logInkHitDebug(annotations, docRelX, docRelY, hitRadiusDoc);
            }
            return;
        }

        final Annotation target = annotations[inkIndex];
        if (target == null) return;

        try {
            if (BuildConfig.DEBUG) {
                Log.d(tag, "begin erase ink idx=" + inkIndex
                        + " obj=" + target.objectNumber
                        + " totalAnnots=" + annotations.length
                        + " rect=[" + target.left + "," + target.top + "][" + target.right + "," + target.bottom + "]"
                        + " arcs=" + (target.arcs != null ? target.arcs.length : -1)
                        + " rDoc=" + hitRadiusDoc);
            }

            // Load the ink arcs into the drawing controller so the eraser modifies stroke geometry.
            if (target.arcs != null && target.arcs.length > 0) {
                host.drawingController().setDraw(target.arcs);
            } else {
                // If we can't edit geometry, fall back to deleting the whole annotation.
                host.drawingController().setDraw(null);
            }

            // Delete the original ink annotation immediately so the underlying render doesn't "fight"
            // the in-progress overlay erase.
            try {
                if (target.objectNumber >= 0) {
                    muPdfController.deleteAnnotationByObjectNumber(host.pageNumber(), target.objectNumber);
                } else {
                    muPdfController.deleteAnnotation(host.pageNumber(), inkIndex);
                }
            } catch (Throwable t) {
                if (BuildConfig.DEBUG) Log.w(tag, "begin erase: deleteAnnotation failed idx=" + inkIndex, t);
                // If we fail to delete, don't enter the "editing existing ink" flow.
                host.drawingController().setDraw(null);
                return;
            }

            // Force a redraw without the deleted annotation; the overlay continues to render the
            // editable ink so the user sees a stable stroke while erasing.
            host.requestFullRedraw();
            host.discardRenderedPage();
            host.loadAnnotations();
            host.redraw(false);

            try { host.requestReaderErasingMode(); } catch (Throwable ignore) {}
            erasingExistingInkAnnotation = host.drawingController().getDrawingSize() > 0;
        } catch (Throwable t) {
            if (BuildConfig.DEBUG) Log.w(tag, "maybeBeginErasingExistingInkAt failed", t);
            // Best-effort: fall back to erasing pending strokes only.
        }
    }

    private static float approxHitRadiusDoc(float scale) {
        // Choose a ~screen-space radius then convert to doc units.
        final float desiredPx = 36f;
        final float safeScale = Math.max(0.1f, Math.abs(scale));
        return desiredPx / safeScale;
    }

    private static int findInkAnnotationHitIndex(Annotation[] annotations, float docRelX, float docRelY, float radiusDoc) {
        if (annotations == null || annotations.length == 0) return -1;
        final float r = Math.max(1f, radiusDoc);
        final PointF p = new PointF(docRelX, docRelY);

        int bestIndex = -1;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < annotations.length; i++) {
            Annotation a = annotations[i];
            if (a == null) continue;
            if (a.type != Annotation.Type.INK) continue;

            // Prefer arc geometry when present; fall back to bounds.
            if (a.arcs != null && a.arcs.length > 0) {
                float dist = distanceToInkArcs(a.arcs, p);
                if (dist <= r && dist < bestDist) {
                    bestDist = dist;
                    bestIndex = i;
                }
            } else if (a.contains(docRelX, docRelY)) {
                // With no arcs, we can't compute proximity; accept rect hit.
                return i;
            }
        }

        return bestIndex;
    }

    private static float distanceToInkArcs(PointF[][] arcs, PointF p) {
        if (arcs == null || p == null) return Float.MAX_VALUE;
        float best = Float.MAX_VALUE;
        for (PointF[] arc : arcs) {
            if (arc == null || arc.length == 0) continue;
            PointF prev = null;
            for (PointF pt : arc) {
                if (pt == null) continue;
                float d = PointFMath.distance(pt, p);
                if (d < best) best = d;
                if (prev != null) {
                    float dl = PointFMath.pointToLineDistance(prev, pt, p);
                    if (dl < best) best = dl;
                }
                prev = pt;
            }
        }
        return best;
    }

    private static void logInkHitDebug(Annotation[] annotations, float docRelX, float docRelY, float radiusDoc) {
        if (!BuildConfig.DEBUG) return;
        if (annotations == null) {
            Log.d("InkController", "erase-hit: annotations=null at [" + docRelX + "," + docRelY + "] r=" + radiusDoc);
            return;
        }
        int inkCount = 0;
        for (Annotation a : annotations) if (a != null && a.type == Annotation.Type.INK) inkCount++;
        Log.d("InkController", "erase-hit: no ink hit at [" + docRelX + "," + docRelY + "] r=" + radiusDoc
                + " annots=" + annotations.length + " inkCount=" + inkCount);
        for (int i = 0; i < annotations.length; i++) {
            Annotation a = annotations[i];
            if (a == null) continue;
            if (a.type != Annotation.Type.INK) continue;
            float dist = distanceToInkArcs(a.arcs, new PointF(docRelX, docRelY));
            Log.d("InkController", "erase-hit: ink idx=" + i
                    + " obj=" + a.objectNumber
                    + " rect=[" + a.left + "," + a.top + "][" + a.right + "," + a.bottom + "]"
                    + " arcs=" + (a.arcs != null ? a.arcs.length : -1)
                    + " dist=" + dist);
        }
    }

    private void maybeBeginErasingSidecarInkAt(final SidecarAnnotationSession sidecar,
                                              final float x,
                                              final float y,
                                              float scale,
                                              int viewLeft,
                                              int viewTop) {
        if (erasingExistingInkAnnotation) return;
        if (host.drawingController().getDrawingSize() != 0) return;
        if (scale == 0f) return;

        List<SidecarInkStroke> strokes;
        try {
            strokes = sidecar.inkStrokesForPage(host.pageNumber());
        } catch (Throwable ignore) {
            return;
        }
        if (strokes == null || strokes.isEmpty()) return;

        final float docRelX = (x - viewLeft) / scale;
        final float docRelY = (y - viewTop) / scale;
        final float hitRadiusDoc = approxHitRadiusDoc(scale);

        SidecarInkStroke hit = findSidecarInkHit(strokes, docRelX, docRelY, hitRadiusDoc);
        if (hit == null) {
            return;
        }

        SidecarInkStroke removed = sidecar.removeInkStroke(host.pageNumber(), hit.id);
        if (removed == null) {
            return;
        }

        try {
            host.drawingController().setDraw(new PointF[][] { removed.points });
            sidecarEditingStroke = removed;
            host.invalidateOverlay();
            try { host.requestReaderErasingMode(); } catch (Throwable ignore) {}
            erasingExistingInkAnnotation = host.drawingController().getDrawingSize() > 0;
        } catch (Throwable t) {
            // If anything goes wrong, restore the removed stroke.
            try { sidecar.restoreInkStroke(removed); } catch (Throwable ignore) {}
            host.drawingController().setDraw(null);
            sidecarEditingStroke = null;
            erasingExistingInkAnnotation = false;
        }
    }

    @Nullable
    private static SidecarInkStroke findSidecarInkHit(List<SidecarInkStroke> strokes,
                                                      float docRelX,
                                                      float docRelY,
                                                      float radiusDoc) {
        if (strokes == null || strokes.isEmpty()) return null;
        final float r = Math.max(1f, radiusDoc);
        final PointF p = new PointF(docRelX, docRelY);
        SidecarInkStroke best = null;
        float bestDist = Float.MAX_VALUE;
        for (SidecarInkStroke s : strokes) {
            if (s == null || s.points == null || s.points.length == 0) continue;
            float dist = distanceToInkArcs(new PointF[][] { s.points }, p);
            if (dist <= r && dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }
}
