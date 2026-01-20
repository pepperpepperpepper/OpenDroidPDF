package org.opendroidpdf.app.drawing;

import android.graphics.PointF;
import android.util.Log;

import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.app.annotation.InkUndoController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.core.MuPdfController;

import androidx.annotation.Nullable;

import java.util.List;

final class InkCommitOps {
    static final class Result {
        final boolean committed;
        @Nullable final SidecarInkStroke sidecarEditingStrokeAfter;

        Result(boolean committed, @Nullable SidecarInkStroke sidecarEditingStrokeAfter) {
            this.committed = committed;
            this.sidecarEditingStrokeAfter = sidecarEditingStrokeAfter;
        }
    }

    private final InkController.Host host;
    private final MuPdfController muPdfController;
    private final InkUndoController inkUndoController;
    @Nullable private final SidecarAnnotationSession sidecarSession;

    private final String tag;
    private final boolean logUndo;
    private final Runnable updateUndoCache;

    InkCommitOps(InkController.Host host,
                 MuPdfController muPdfController,
                 InkUndoController inkUndoController,
                 @Nullable SidecarAnnotationSession sidecarSession,
                 String tag,
                 boolean logUndo,
                 Runnable updateUndoCache) {
        this.host = host;
        this.muPdfController = muPdfController;
        this.inkUndoController = inkUndoController;
        this.sidecarSession = sidecarSession;
        this.tag = tag;
        this.logUndo = logUndo;
        this.updateUndoCache = updateUndoCache;
    }

    Result saveDraw(@Nullable Runnable beforeCancelDraw, @Nullable SidecarInkStroke sidecarEditingStroke) {
        final SidecarAnnotationSession sidecar = sidecarSession;
        PointF[][] path = host.drawingController().getDraw();
        if (path == null) return new Result(false, sidecarEditingStroke);
        PointF[][] sanitized = sanitizePath(path);
        if (sanitized == null) {
            Log.e(tag, "[undo] saveDraw refusing to commit invalid ink path page=" + host.pageNumber()
                    + " pendingPoints=" + countPoints(path));
            return new Result(false, sidecarEditingStroke);
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
            updateUndoCache.run();
            return new Result(true, sidecarEditingStroke);
        }

        final int annotationCountBefore = safeAnnotationCount(host.pageNumber());

        if (logUndo) {
            Log.d(tag, "[undo] saveDraw begin page=" + host.pageNumber()
                    + " pendingPoints=" + countPoints(sanitized));
        }
        try {
            muPdfController.addInkAnnotation(host.pageNumber(), sanitized);
        } catch (Throwable t) {
            // Never discard the user's in-progress ink if the native commit fails.
            Log.e(tag, "[undo] saveDraw failed to commit ink page=" + host.pageNumber()
                    + " pendingPoints=" + countPoints(sanitized), t);
            return new Result(false, sidecarEditingStroke);
        }

        // Defensive check: JNI ink commit can silently no-op (e.g., non-PDF docs) without throwing.
        // If we cannot observe the annotation list growing, keep the pending stroke instead of
        // clearing it and making ink "disappear" when the user changes settings.
        if (annotationCountBefore >= 0) {
            final int annotationCountAfter = safeAnnotationCount(host.pageNumber());
            if (annotationCountAfter >= 0 && annotationCountAfter <= annotationCountBefore) {
                Log.e(tag, "[undo] saveDraw commit did not add annotation page=" + host.pageNumber()
                        + " before=" + annotationCountBefore + " after=" + annotationCountAfter
                        + " pendingPoints=" + countPoints(path));
                return new Result(false, sidecarEditingStroke);
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
            updateUndoCache.run();
        } catch (Throwable ignore) { }

        if (logUndo) {
            Log.d(tag, "[undo] saveDraw end page=" + host.pageNumber()
                    + " stackSize=" + inkUndoController.stackSize());
        }
        return new Result(true, sidecarEditingStroke);
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
}
