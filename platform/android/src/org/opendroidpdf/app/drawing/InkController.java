package org.opendroidpdf.app.drawing;

import android.os.SystemClock;
import android.graphics.PointF;
import android.util.Log;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.DrawingController;
import org.opendroidpdf.PointFMath;
import org.opendroidpdf.app.annotation.InkUndoController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.BuildConfig;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns ink stroke lifecycle: start/append/end erases/draws, commit to MuPDF, and undo bookkeeping.
 * Keeps MuPDFPageView free of ink state and toolbar wiring.
 */
public class InkController {
    private static final String TAG = "InkController";
    private static final boolean LOG_UNDO = BuildConfig.DEBUG;

    public interface Host {
        DrawingController drawingController();
        void requestReaderErasingMode();
        int pageNumber();
        void requestFullRedraw();
        void loadAnnotations();
        void discardRenderedPage();
        void redraw(boolean updateHq);
        void invalidateOverlay();
        float currentInkThickness();
        int currentInkColor();
        float currentEraserThickness();
    }

    private final Host host;
    private final MuPdfController muPdfController;
    private final InkUndoController inkUndoController;
    @Nullable private final SidecarAnnotationSession sidecarSession;
    @Nullable private SidecarInkStroke sidecarEditingStroke = null;

    private boolean activeInkGesture = false;
    private float activeInkThickness = 0f;

    private boolean activeEraseGesture = false;
    private float activeEraserThickness = 0f;

    private long lastUndoMutationUptimeMs = 0L;

    // When true, the current erase gesture is editing an existing ink annotation
    // (loaded into DrawingController) and should auto-commit on erase end.
    private boolean erasingExistingInkAnnotation = false;
    private long lastEraseInkHitAttemptUptimeMs = 0L;

    public InkController(Host host,
                         MuPdfController muPdfController,
                         @Nullable SidecarAnnotationSession sidecarSession) {
        this.host = host;
        this.muPdfController = muPdfController;
        this.inkUndoController = new InkUndoController(new UndoHost(), muPdfController, TAG, LOG_UNDO);
        this.sidecarSession = sidecarSession;
    }

    public InkUndoController undo() { return inkUndoController; }

    /** Best-effort timestamp used to order ink-vs-text undo choices on a page. */
    public long lastUndoMutationUptimeMs() { return lastUndoMutationUptimeMs; }

    public boolean isEditingExistingInk() {
        return erasingExistingInkAnnotation;
    }

    public void resetEraserSession() {
        erasingExistingInkAnnotation = false;
        lastEraseInkHitAttemptUptimeMs = 0L;
        sidecarEditingStroke = null;
    }

    /**
     * Refreshes toolbar undo enablement from the current ink/pending state.
     * This is the single place in the app that mutates the "ink" undo/redo portion of {@link org.opendroidpdf.app.toolbar.ToolbarStateCache}.
     */
    public void refreshUndoState() {
        updateUndoCache();
    }

    public void startStroke(float x, float y, float thickness) {
        host.drawingController().startDraw(x, y, thickness);
    }

    public void appendStroke(float x, float y, float thickness) {
        host.drawingController().continueDraw(x, y, thickness);
    }

    public void finishStroke(float thickness) {
        host.drawingController().finishDraw(thickness);
    }

    public void onStartDrawGesture(float x, float y) {
        activeInkGesture = true;
        activeInkThickness = host.currentInkThickness();
        startStroke(x, y, activeInkThickness);
        updateUndoCache();
    }

    public void onContinueDrawGesture(float x, float y) {
        if (!activeInkGesture) {
            activeInkGesture = true;
            activeInkThickness = host.currentInkThickness();
        }
        appendStroke(x, y, activeInkThickness);
    }

    public void onFinishDrawGesture() {
        if (!activeInkGesture) {
            activeInkGesture = true;
            activeInkThickness = host.currentInkThickness();
        }
        finishStroke(activeInkThickness);
        activeInkGesture = false;
        activeInkThickness = 0f;
        updateUndoCache();
    }

    public void onCancelDrawGesture() {
        host.drawingController().cancelDraw();
        activeInkGesture = false;
        activeInkThickness = 0f;
        updateUndoCache();
    }

    public void startErase(float x, float y, float thickness) {
        host.drawingController().startErase(x, y, thickness);
    }

    public void appendErase(float x, float y, float thickness) {
        host.drawingController().continueErase(x, y, thickness);
    }

    public void finishErase(float x, float y, float thickness) {
        host.drawingController().finishErase(x, y, thickness);
    }

    public void beginEraseGesture(float viewX, float viewY, float scale, int viewLeft, int viewTop) {
        try {
            onStartEraseGesture(viewX, viewY, scale, viewLeft, viewTop);
        } catch (Throwable ignore) {
        }
        activeEraseGesture = true;
        activeEraserThickness = host.currentEraserThickness();
        startErase(viewX, viewY, activeEraserThickness);
        updateUndoCache();
    }

    public void continueEraseGesture(float viewX, float viewY, float scale, int viewLeft, int viewTop) {
        try {
            onContinueEraseGesture(viewX, viewY, scale, viewLeft, viewTop);
        } catch (Throwable ignore) {
        }
        if (!activeEraseGesture) {
            activeEraseGesture = true;
            activeEraserThickness = host.currentEraserThickness();
        }
        appendErase(viewX, viewY, activeEraserThickness);
    }

    public void finishEraseGesture(float viewX, float viewY) {
        if (!activeEraseGesture) {
            activeEraseGesture = true;
            activeEraserThickness = host.currentEraserThickness();
        }
        finishErase(viewX, viewY, activeEraserThickness);
        activeEraseGesture = false;
        activeEraserThickness = 0f;
        onFinishEraseGesture();
        updateUndoCache();
    }

    /**
     * Called by the PageView eraser gesture pipeline (from {@link org.opendroidpdf.MuPDFPageView})
     * to opportunistically switch into "edit committed ink" mode when the user erases over an
     * existing ink annotation.
     *
     * This keeps the "who owns ink geometry right now?" state machine here instead of in PageView.
     */
    public void onStartEraseGesture(float viewX, float viewY, float scale, int viewLeft, int viewTop) {
        lastEraseInkHitAttemptUptimeMs = 0L;

        // If we have pending ink, commit it first so the eraser can operate on *any* committed ink
        // annotation (across pen size/color changes) rather than being blocked by overlay state.
        //
        // If commit fails, we fall back to erasing pending strokes only.
        if (!erasingExistingInkAnnotation && host.drawingController().getDrawingSize() > 0) {
            boolean committed = saveDraw();
            if (!committed && BuildConfig.DEBUG) {
                Log.w(TAG, "startErase: pending ink commit failed; will erase pending strokes only");
            }
        }

        maybeBeginErasingExistingInkAt(viewX, viewY, scale, viewLeft, viewTop);
    }

    public void onContinueEraseGesture(float viewX, float viewY, float scale, int viewLeft, int viewTop) {
        if (erasingExistingInkAnnotation) return;
        if (host.drawingController().getDrawingSize() != 0) return;

        long now = SystemClock.uptimeMillis();
        if (now - lastEraseInkHitAttemptUptimeMs >= 80L) {
            lastEraseInkHitAttemptUptimeMs = now;
            maybeBeginErasingExistingInkAt(viewX, viewY, scale, viewLeft, viewTop);
        }
    }

    public void onFinishEraseGesture() {
        if (!erasingExistingInkAnnotation) return;
        try {
            // If anything remains after erasing, commit it immediately so the user sees the result.
            if (host.drawingController().getDrawingSize() > 0) {
                saveDraw();
            } else {
                host.drawingController().cancelDraw();
                // Sidecar: deleting an entire committed stroke should still be undoable.
                if (sidecarSession != null && sidecarEditingStroke != null) {
                    sidecarSession.recordUndoInkReplaced(host.pageNumber(), sidecarEditingStroke, java.util.Collections.emptyList());
                    sidecarEditingStroke = null;
                    updateUndoCache();
                }
            }
        } catch (Throwable ignore) {
            // Avoid crashing during erase; leaving the doc in a consistent state is best-effort.
        } finally {
            erasingExistingInkAnnotation = false;
        }
    }

    public boolean saveDraw() {
        return saveDraw(null);
    }

    public boolean saveDraw(Runnable beforeCancelDraw) {
        final SidecarAnnotationSession sidecar = sidecarSession;
        PointF[][] path = host.drawingController().getDraw();
        if (path == null) return false;
        PointF[][] sanitized = sanitizePath(path);
        if (sanitized == null) {
            Log.e(TAG, "[undo] saveDraw refusing to commit invalid ink path page=" + host.pageNumber()
                    + " pendingPoints=" + countPoints(path));
            return false;
        }

        if (sidecar != null) {
            // Sidecar-backed commit: persist as overlay strokes (no MuPDF/JNI calls).
            long now = System.currentTimeMillis();
            int color = host.currentInkColor();
            float thickness = host.currentInkThickness();

            SidecarInkStroke original = sidecarEditingStroke;
            List<SidecarInkStroke> inserted = sidecar.addInkFromArcs(host.pageNumber(), sanitized, color, thickness, now);

            // Clear overlay pending strokes; persisted strokes will be re-rendered from the session.
            host.drawingController().cancelDraw();

            if (original != null) {
                sidecar.recordUndoInkReplaced(host.pageNumber(), original, inserted);
                sidecarEditingStroke = null;
            } else if (!inserted.isEmpty()) {
                sidecar.recordUndoInkAdded(host.pageNumber(), inserted);
            }

            host.invalidateOverlay();
            updateUndoCache();
            return true;
        }

        final int annotationCountBefore = safeAnnotationCount(host.pageNumber());

        if (LOG_UNDO) {
            Log.d(TAG, "[undo] saveDraw begin page=" + host.pageNumber()
                    + " pendingPoints=" + countPoints(sanitized));
        }
        try {
            muPdfController.addInkAnnotation(host.pageNumber(), sanitized);
        } catch (Throwable t) {
            // Never discard the user's in-progress ink if the native commit fails.
            Log.e(TAG, "[undo] saveDraw failed to commit ink page=" + host.pageNumber()
                    + " pendingPoints=" + countPoints(sanitized), t);
            return false;
        }

        // Defensive check: JNI ink commit can silently no-op (e.g., non-PDF docs) without throwing.
        // If we cannot observe the annotation list growing, keep the pending stroke instead of
        // clearing it and making ink "disappear" when the user changes settings.
        if (annotationCountBefore >= 0) {
            final int annotationCountAfter = safeAnnotationCount(host.pageNumber());
            if (annotationCountAfter >= 0 && annotationCountAfter <= annotationCountBefore) {
                Log.e(TAG, "[undo] saveDraw commit did not add annotation page=" + host.pageNumber()
                        + " before=" + annotationCountBefore + " after=" + annotationCountAfter
                        + " pendingPoints=" + countPoints(path));
                return false;
            }
        }

        if (beforeCancelDraw != null) {
            try {
                beforeCancelDraw.run();
            } catch (Throwable ignore) {
            }
        }

        host.drawingController().cancelDraw();

        try {
            muPdfController.markDocumentDirty();
            // Tiny render to update appearance streams before export.
            try {
                android.graphics.Bitmap onePx = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
                MuPDFCore.Cookie cookie = muPdfController.newRenderCookie();
                muPdfController.drawPage(onePx, host.pageNumber(), 1, 1, 0, 0, 1, 1, cookie);
                cookie.destroy();
            } catch (Throwable ignoreInner) {}
            // Force a full redraw for freshly committed ink: updatePage() can miss
            // newly created annotation appearance streams on some devices.
            host.requestFullRedraw();
            host.discardRenderedPage();
            host.loadAnnotations();
            inkUndoController.recordCommittedInkForUndo(sanitized);
            updateUndoCache();
        } catch (Throwable ignore) { }

        if (LOG_UNDO) {
            Log.d(TAG, "[undo] saveDraw end page=" + host.pageNumber()
                    + " stackSize=" + inkUndoController.stackSize());
        }
        return true;
    }

    public void undoDraw() {
        DrawingController drawing = host.drawingController();
        if (drawing.canUndo()) {
            drawing.undoDraw();
            updateUndoCache();
            return;
        }
        SidecarAnnotationSession sidecar = sidecarSession;
        if (sidecar != null && sidecar.undoLast()) {
            host.invalidateOverlay();
            updateUndoCache();
            return;
        }
        if (inkUndoController.undoLast()) {
            updateUndoCache();
        }
    }

    public void redoDraw() {
        SidecarAnnotationSession sidecar = sidecarSession;
        if (sidecar != null && sidecar.redoLast()) {
            host.invalidateOverlay();
        }
        updateUndoCache();
    }

    public boolean canUndo() {
        SidecarAnnotationSession sidecar = sidecarSession;
        return host.drawingController().canUndo()
                || (sidecar != null && sidecar.hasUndo())
                || inkUndoController.hasUndo();
    }

    public boolean canRedo() {
        SidecarAnnotationSession sidecar = sidecarSession;
        return sidecar != null && sidecar.hasRedo();
    }

    public void clear() {
        inkUndoController.clear();
        sidecarEditingStroke = null;
        updateUndoCache();
    }

    public void release() {
        // nothing to release beyond undo controller state
    }

    private static int countPoints(PointF[][] arcs) {
        if (arcs == null) return 0;
        int count = 0;
        for (PointF[] arc : arcs) {
            if (arc == null) continue;
            count += arc.length;
        }
        return count;
    }

    private static PointF[][] sanitizePath(PointF[][] arcs) {
        if (arcs == null) {
            return null;
        }
        java.util.ArrayList<PointF[]> strokes = new java.util.ArrayList<>();
        for (PointF[] arc : arcs) {
            if (arc == null) {
                continue;
            }
            java.util.ArrayList<PointF> points = new java.util.ArrayList<>(arc.length);
            for (PointF p : arc) {
                if (p == null) {
                    continue;
                }
                if (!isFinite(p.x) || !isFinite(p.y)) {
                    continue;
                }
                points.add(p);
            }
            if (points.size() < 2) {
                continue;
            }
            strokes.add(points.toArray(new PointF[0]));
        }
        if (strokes.isEmpty()) {
            return null;
        }
        return strokes.toArray(new PointF[0][]);
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    private int safeAnnotationCount(int pageNumber) {
        try {
            org.opendroidpdf.Annotation[] annots = muPdfController.annotations(pageNumber);
            return annots != null ? annots.length : 0;
        } catch (Throwable ignore) {
            return -1;
        }
    }

    private final class UndoHost implements InkUndoController.Host {
        @Override public int pageNumber() { return host.pageNumber(); }
        @Override public void onInkStackMutated() {
            host.requestFullRedraw();
            host.loadAnnotations();
            host.discardRenderedPage();
            host.redraw(false);
            updateUndoCache();
        }
    }

    private void updateUndoCache() {
        lastUndoMutationUptimeMs = SystemClock.uptimeMillis();
        org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setInkUndoRedo(canUndo(), canRedo());
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
                Log.d(TAG, "begin erase ink idx=" + inkIndex
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
                if (BuildConfig.DEBUG) Log.w(TAG, "begin erase: deleteAnnotation failed idx=" + inkIndex, t);
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
            if (BuildConfig.DEBUG) Log.w(TAG, "maybeBeginErasingExistingInkAt failed", t);
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
            Log.d(TAG, "erase-hit: annotations=null at [" + docRelX + "," + docRelY + "] r=" + radiusDoc);
            return;
        }
        int inkCount = 0;
        for (Annotation a : annotations) if (a != null && a.type == Annotation.Type.INK) inkCount++;
        Log.d(TAG, "erase-hit: no ink hit at [" + docRelX + "," + docRelY + "] r=" + radiusDoc
                + " annots=" + annotations.length + " inkCount=" + inkCount);
        for (int i = 0; i < annotations.length; i++) {
            Annotation a = annotations[i];
            if (a == null) continue;
            if (a.type != Annotation.Type.INK) continue;
            float dist = distanceToInkArcs(a.arcs, new PointF(docRelX, docRelY));
            Log.d(TAG, "erase-hit: ink idx=" + i
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

    // Sidecar undo is owned by SidecarAnnotationSession so note/highlight/ink share one stack.
}
