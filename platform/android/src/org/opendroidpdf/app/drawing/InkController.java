package org.opendroidpdf.app.drawing;

import android.os.SystemClock;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.DrawingController;
import org.opendroidpdf.app.annotation.InkUndoController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.core.MuPdfController;

import androidx.annotation.Nullable;

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

    private final InkCommitOps commitOps;
    private final InkExistingInkEraser existingInkEraser;

    private boolean activeInkGesture = false;
    private float activeInkThickness = 0f;

    private boolean activeEraseGesture = false;
    private float activeEraserThickness = 0f;

    private long lastUndoMutationUptimeMs = 0L;

    public InkController(Host host,
                         MuPdfController muPdfController,
                         @Nullable SidecarAnnotationSession sidecarSession) {
        this.host = host;
        this.muPdfController = muPdfController;
        this.inkUndoController = new InkUndoController(new UndoHost(), muPdfController, TAG, LOG_UNDO);
        this.sidecarSession = sidecarSession;
        this.commitOps = new InkCommitOps(host, muPdfController, inkUndoController, sidecarSession, TAG, LOG_UNDO, this::updateUndoCache);
        this.existingInkEraser = new InkExistingInkEraser(host, muPdfController, sidecarSession, commitOps, TAG, this::updateUndoCache);
    }

    public InkUndoController undo() { return inkUndoController; }

    /** Best-effort timestamp used to order ink-vs-text undo choices on a page. */
    public long lastUndoMutationUptimeMs() { return lastUndoMutationUptimeMs; }

    public boolean isEditingExistingInk() {
        return existingInkEraser.isEditingExistingInk();
    }

    public void resetEraserSession() {
        existingInkEraser.resetEraserSession();
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
        existingInkEraser.onStartEraseGesture(viewX, viewY, scale, viewLeft, viewTop);
    }

    public void onContinueEraseGesture(float viewX, float viewY, float scale, int viewLeft, int viewTop) {
        existingInkEraser.onContinueEraseGesture(viewX, viewY, scale, viewLeft, viewTop);
    }

    public void onFinishEraseGesture() {
        existingInkEraser.onFinishEraseGesture();
    }

    public boolean saveDraw() {
        return saveDraw(null);
    }

    public boolean saveDraw(Runnable beforeCancelDraw) {
        InkCommitOps.Result result = commitOps.saveDraw(beforeCancelDraw, existingInkEraser.sidecarEditingStrokeOrNull());
        existingInkEraser.setSidecarEditingStroke(result.sidecarEditingStrokeAfter);
        return result.committed;
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
        existingInkEraser.clearEditingStrokeOnly();
        updateUndoCache();
    }

    public void release() {
        // nothing to release beyond undo controller state
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
}
