package org.opendroidpdf.app.drawing;

import android.graphics.PointF;
import android.util.Log;

import org.opendroidpdf.DrawingController;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.annotation.InkUndoController;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.MuPDFCore;

/**
 * Owns ink stroke lifecycle: start/append/end erases/draws, commit to MuPDF, and undo bookkeeping.
 * Keeps MuPDFPageView free of ink state and toolbar wiring.
 */
public class InkController {
    private static final String TAG = "InkController";
    private static final boolean LOG_UNDO = true;

    public interface Host {
        DrawingController drawingController();
        MuPDFReaderView parentReader();
        int pageNumber();
        void requestFullRedraw();
        void loadAnnotations();
        void discardRenderedPage();
        void redraw(boolean updateHq);
    }

    private final Host host;
    private final MuPdfController muPdfController;
    private final InkUndoController inkUndoController;

    public InkController(Host host, MuPdfController muPdfController) {
        this.host = host;
        this.muPdfController = muPdfController;
        this.inkUndoController = new InkUndoController(new UndoHost(), muPdfController, TAG, LOG_UNDO);
    }

    public InkUndoController undo() { return inkUndoController; }

    public void startStroke(float x, float y, float thickness) {
        host.drawingController().startDraw(x, y, thickness);
    }

    public void appendStroke(float x, float y, float thickness) {
        host.drawingController().continueDraw(x, y, thickness);
    }

    public void finishStroke(float thickness) {
        host.drawingController().finishDraw(thickness);
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

    public boolean saveDraw() {
        PointF[][] path = host.drawingController().getDraw();
        if (path == null) return false;

        if (LOG_UNDO) {
            Log.d(TAG, "[undo] saveDraw begin page=" + host.pageNumber()
                    + " pendingPoints=" + countPoints(path));
        }

        host.drawingController().cancelDraw();

        try {
            muPdfController.addInkAnnotation(host.pageNumber(), path);
            muPdfController.markDocumentDirty();
            // Tiny render to update appearance streams before export.
            try {
                android.graphics.Bitmap onePx = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
                MuPDFCore.Cookie cookie = muPdfController.newRenderCookie();
                muPdfController.drawPage(onePx, host.pageNumber(), 1, 1, 0, 0, 1, 1, cookie);
                cookie.destroy();
            } catch (Throwable ignoreInner) {}
            host.loadAnnotations();
            inkUndoController.recordCommittedInkForUndo(path);
            org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
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
            org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
            return;
        }
        if (inkUndoController.undoLast()) {
            org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
        }
    }

    public boolean canUndo() {
        return host.drawingController().canUndo() || inkUndoController.hasUndo();
    }

    public void clear() {
        inkUndoController.clear();
        org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
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

    private final class UndoHost implements InkUndoController.Host {
        @Override public int pageNumber() { return host.pageNumber(); }
        @Override public void onInkStackMutated() {
            host.requestFullRedraw();
            host.loadAnnotations();
            host.discardRenderedPage();
            host.redraw(false);
            org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
        }
    }
}
