package org.opendroidpdf.app.annotation;

import android.graphics.PointF;
import android.os.Bundle;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Serializes the in-progress drawing and its undo history into a Bundle
 * and restores it back. Kept separate to shrink PageView.
 */
public final class DrawingStateSerializer {

    private static final String KEY_DRAWING = "mDrawing";
    private static final String KEY_HISTORY = "mDrawingHistory";

    private DrawingStateSerializer() {}

    // Lightweight serializable PointF wrapper
    public static class PointFSerializable extends PointF implements Serializable {
        private static final long serialVersionUID = 1L;
        public PointFSerializable(PointF p) { super(p.x, p.y); }
    }

    public static void putInto(Bundle bundle,
                               ArrayList<ArrayList<PointF>> drawing,
                               ArrayDeque<ArrayList<ArrayList<PointF>>> history) {
        ArrayList<ArrayList<PointFSerializable>> drawingSer = new ArrayList<>();
        if (drawing != null) {
            for (ArrayList<PointF> stroke : drawing) {
                ArrayList<PointFSerializable> strokeSer = new ArrayList<>();
                if (stroke != null) {
                    for (PointF p : stroke) strokeSer.add(new PointFSerializable(p));
                }
                drawingSer.add(strokeSer);
            }
        }

        ArrayDeque<ArrayList<ArrayList<PointFSerializable>>> historySer = new ArrayDeque<>();
        if (history != null) {
            for (ArrayList<ArrayList<PointF>> list : history) {
                ArrayList<ArrayList<PointFSerializable>> listSer = new ArrayList<>();
                if (list != null) {
                    for (ArrayList<PointF> stroke : list) {
                        ArrayList<PointFSerializable> strokeSer = new ArrayList<>();
                        if (stroke != null) {
                            for (PointF p : stroke) strokeSer.add(new PointFSerializable(p));
                        }
                        listSer.add(strokeSer);
                    }
                }
                historySer.add(listSer);
            }
        }

        bundle.putSerializable(KEY_DRAWING, drawingSer);
        bundle.putSerializable(KEY_HISTORY, historySer);
    }

    public static class Restored {
        public final ArrayList<ArrayList<PointF>> drawing;
        public final ArrayDeque<ArrayList<ArrayList<PointF>>> history;
        Restored(ArrayList<ArrayList<PointF>> d, ArrayDeque<ArrayList<ArrayList<PointF>>> h) {
            this.drawing = d; this.history = h;
        }
    }

    @SuppressWarnings("unchecked")
    public static Restored restoreFrom(Bundle bundle) {
        ArrayList<ArrayList<PointF>> drawing = new ArrayList<>();
        ArrayDeque<ArrayList<ArrayList<PointF>>> history = new ArrayDeque<>();

        Object dObj = bundle.getSerializable(KEY_DRAWING);
        if (dObj instanceof ArrayList) {
            ArrayList<ArrayList<PointFSerializable>> drawingSer = (ArrayList<ArrayList<PointFSerializable>>) dObj;
            if (drawingSer != null) {
                for (ArrayList<PointFSerializable> strokeSer : drawingSer) {
                    ArrayList<PointF> stroke = new ArrayList<>();
                    if (strokeSer != null) {
                        for (PointF pSer : strokeSer) stroke.add(new PointF(pSer.x, pSer.y));
                    }
                    drawing.add(stroke);
                }
            }
        }

        Object hObj = bundle.getSerializable(KEY_HISTORY);
        if (hObj instanceof ArrayDeque) {
            ArrayDeque<ArrayList<ArrayList<PointFSerializable>>> historySer =
                    (ArrayDeque<ArrayList<ArrayList<PointFSerializable>>>) hObj;
            if (historySer != null) {
                for (ArrayList<ArrayList<PointFSerializable>> listSer : historySer) {
                    ArrayList<ArrayList<PointF>> list = new ArrayList<>();
                    if (listSer != null) {
                        for (ArrayList<PointFSerializable> strokeSer : listSer) {
                            ArrayList<PointF> stroke = new ArrayList<>();
                            if (strokeSer != null) {
                                for (PointF pSer : strokeSer) stroke.add(new PointF(pSer.x, pSer.y));
                            }
                            list.add(stroke);
                        }
                    }
                    history.add(list);
                }
            }
        }
        return new Restored(drawing, history);
    }
}

