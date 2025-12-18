package org.opendroidpdf.app.drawing;

import android.graphics.PointF;
import android.util.Log;

import org.opendroidpdf.DrawingController;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.annotation.InkUndoController;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.BuildConfig;

/**
 * Owns ink stroke lifecycle: start/append/end erases/draws, commit to MuPDF, and undo bookkeeping.
 * Keeps MuPDFPageView free of ink state and toolbar wiring.
 */
public class InkController {
    private static final String TAG = "InkController";
    private static final boolean LOG_UNDO = BuildConfig.DEBUG;

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
        return saveDraw(null);
    }

    public boolean saveDraw(Runnable beforeCancelDraw) {
        PointF[][] path = host.drawingController().getDraw();
        if (path == null) return false;

        final int annotationCountBefore = safeAnnotationCount(host.pageNumber());

        if (LOG_UNDO) {
            Log.d(TAG, "[undo] saveDraw begin page=" + host.pageNumber()
                    + " pendingPoints=" + countPoints(path));
        }
        try {
            muPdfController.addInkAnnotation(host.pageNumber(), path);
        } catch (Throwable t) {
            // Never discard the user's in-progress ink if the native commit fails.
            Log.e(TAG, "[undo] saveDraw failed to commit ink page=" + host.pageNumber()
                    + " pendingPoints=" + countPoints(path), t);
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
            org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
        }
    }
}
