package org.opendroidpdf;

import android.graphics.PointF;
import android.graphics.Rect;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Manages in-progress ink strokes and erase gestures independently of the
 * PageView implementation so the activity/view layer can stay focused on
 * layout/rendering. All coordinates are kept in document space; callers supply
 * the current view scale/offset and thickness values.
 */
public class DrawingController {
    public interface Host {
        /** Current view scale (doc -> screen). */
        float scale();

        /** Left offset of the PageView in screen coords. */
        int viewLeft();

        /** Top offset of the PageView in screen coords. */
        int viewTop();

        /** Overlay view used for invalidation/redraws. */
        View overlayView();

        /** Invalidate the entire overlay. */
        void invalidateAll();
    }

    private final Host host;
    private ArrayList<ArrayList<PointF>> drawing = new ArrayList<>();
    private ArrayDeque<ArrayList<ArrayList<PointF>>> history = new ArrayDeque<>();
    private PointF eraser;

    public DrawingController(Host host) {
        this.host = host;
    }

    public void clear() {
        drawing = null;
        history.clear();
        eraser = null;
    }

    public void startDraw(float x, float y, float inkThickness) {
        saveToHistory();
        ensureDrawingList();
        drawing.add(newArc(docPoint(x, y)));
    }

    public void continueDraw(float x, float y, float inkThickness) {
        if (drawing == null || drawing.isEmpty()) return;

        final float scale = host.scale();
        PointF point = docPoint(x, y);
        ArrayList<PointF> arc = drawing.get(drawing.size() - 1);
        arc.add(point);

        PointF prev = arc.get(arc.size() - 2);
        Rect invalidRect = new Rect();
        invalidRect.union((int) (point.x * scale + host.viewLeft()),
                          (int) (point.y * scale + host.viewTop()));
        invalidRect.union((int) (prev.x * scale + host.viewLeft()),
                          (int) (prev.y * scale + host.viewTop()));
        int inkWidth = (int) (inkThickness * scale) + 1;
        View overlay = host.overlayView();
        if (overlay != null) {
            overlay.invalidate(invalidRect.left - inkWidth,
                               invalidRect.top - inkWidth,
                               invalidRect.right + inkWidth,
                               invalidRect.bottom + inkWidth);
        }
    }

    public void finishDraw(float inkThickness) {
        if (drawing != null && drawing.size() > 0) {
            ArrayList<PointF> arc = drawing.get(drawing.size() - 1);
            // Single-tap strokes: draw a tiny diamond so it shows up.
            if (arc.size() == 1) {
                PointF lastArc = arc.get(0);
                arc.add(new PointF(lastArc.x + 0.5f * inkThickness, lastArc.y));
                arc.add(new PointF(lastArc.x + 0.5f * inkThickness, lastArc.y + 0.5f * inkThickness));
                arc.add(new PointF(lastArc.x, lastArc.y + 0.5f * inkThickness));
                arc.add(lastArc);
                arc.add(new PointF(lastArc.x + 0.5f * inkThickness, lastArc.y));
            }
            if (host.overlayView() != null) {
                host.overlayView().invalidate();
            }
        }
    }

    public void startErase(float x, float y, float eraserThickness) {
        saveToHistory();
        eraser = docPoint(x, y);
        continueErase(x, y, eraserThickness);
    }

    public void continueErase(float x, float y, float eraserThickness) {
        if (eraser == null || drawing == null || drawing.isEmpty()) {
            return;
        }

        eraser.set(docPoint(x, y));
        ArrayList<ArrayList<PointF>> newArcs = new ArrayList<>();

        for (ArrayList<PointF> arc : drawing) {
            Iterator<PointF> iter = arc.iterator();
            PointF pointToAddToArc = null;
            PointF lastPoint = iter.hasNext() ? iter.next() : null;
            boolean newArcHasBeenCreated = false;

            if (lastPoint != null && PointFMath.distance(lastPoint, eraser) <= eraserThickness) {
                iter.remove();
            }

            while (iter.hasNext()) {
                PointF point = iter.next();
                LineSegmentCircleIntersectionResult result =
                        PointFMath.LineSegmentCircleIntersection(lastPoint, point, eraser, eraserThickness);

                if (result.intersects) {
                    iter.remove();
                    if (result.enter != null) {
                        if (newArcHasBeenCreated) {
                            newArcs.get(newArcs.size() - 1).add(newArcs.get(newArcs.size() - 1).size(), result.enter);
                        } else {
                            pointToAddToArc = result.enter;
                        }
                    }
                    if (result.exit != null) {
                        newArcHasBeenCreated = true;
                        newArcs.add(new ArrayList<PointF>());
                        newArcs.get(newArcs.size() - 1).add(result.exit);
                        newArcs.get(newArcs.size() - 1).add(point);
                    }
                } else if (result.inside) {
                    iter.remove();
                } else if (newArcHasBeenCreated) {
                    iter.remove();
                    newArcs.get(newArcs.size() - 1).add(point);
                }
                lastPoint = point;
            }

            if (arc.size() > 0 && pointToAddToArc != null) {
                arc.add(arc.size(), pointToAddToArc);
            }
        }

        if (drawing != null) {
            drawing.addAll(newArcs);
            Iterator<ArrayList<PointF>> iter = drawing.iterator();
            while (iter.hasNext()) {
                if (iter.next().size() < 2) {
                    iter.remove();
                }
            }
        }

        if (host.overlayView() != null) {
            host.overlayView().invalidate();
        }
    }

    public void finishErase(float x, float y, float eraserThickness) {
        continueErase(x, y, eraserThickness);
        eraser = null;
    }

    public void undoDraw() {
        if (history.size() > 0) {
            drawing = history.pop();
            host.invalidateAll();
        }
    }

    public boolean canUndo() {
        return history.size() > 0;
    }

    public void cancelDraw() {
        drawing = null;
        history.clear();
        host.invalidateAll();
    }

    public int getDrawingSize() {
        return drawing == null ? 0 : drawing.size();
    }

    public void setDraw(PointF[][] arcs) {
        if (arcs != null) {
            drawing = new ArrayList<>();
            for (PointF[] arc : arcs) {
                ArrayList<PointF> list = new ArrayList<>();
                for (PointF pointF : arc) {
                    list.add(pointF);
                }
                drawing.add(list);
            }
        } else {
            drawing = null;
        }
        host.invalidateAll();
    }

    public PointF[][] getDraw() {
        if (drawing == null) return null;
        PointF[][] arcs = new PointF[drawing.size()][];
        for (int i = 0; i < drawing.size(); i++) {
            ArrayList<PointF> arc = drawing.get(i);
            arcs[i] = arc.toArray(new PointF[arc.size()]);
        }
        return arcs;
    }

    public ArrayList<ArrayList<PointF>> getDrawing() {
        return drawing;
    }

    public ArrayDeque<ArrayList<ArrayList<PointF>>> getHistory() {
        return history;
    }

    public void restore(ArrayList<ArrayList<PointF>> drawingIn,
                        ArrayDeque<ArrayList<ArrayList<PointF>>> historyIn) {
        drawing = drawingIn;
        history = historyIn != null ? historyIn : new ArrayDeque<ArrayList<ArrayList<PointF>>>();
        host.invalidateAll();
    }

    public PointF getEraser() {
        return eraser;
    }

    private void ensureDrawingList() {
        if (drawing == null) {
            drawing = new ArrayList<>();
        }
    }

    private ArrayList<PointF> newArc(PointF start) {
        ArrayList<PointF> arc = new ArrayList<>();
        arc.add(start);
        return arc;
    }

    private PointF docPoint(float x, float y) {
        final float scale = host.scale();
        final float docRelX = (x - host.viewLeft()) / scale;
        final float docRelY = (y - host.viewTop()) / scale;
        return new PointF(docRelX, docRelY);
    }

    private void saveToHistory() {
        if (drawing != null) {
            ArrayList<ArrayList<PointF>> copy = new ArrayList<>(drawing.size());
            for (ArrayList<PointF> stroke : drawing) {
                copy.add(new ArrayList<PointF>(stroke));
            }
            history.push(copy);
        } else {
            history.push(new ArrayList<ArrayList<PointF>>(0));
        }
    }
}
