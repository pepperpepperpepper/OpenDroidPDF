package org.opendroidpdf.app.drawing;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java gesture router that turns DOWN/MOVE/UP sequences into
 * higher-level drawing/erasing actions. Keeps thresholds and transitions
 * testable without Android dependencies.
 */
public final class DrawingGestureRouter {

    public enum Mode { DRAW, ERASE }
    public enum EventType { DOWN, MOVE, UP, CANCEL }
    public enum OutputType { BEGIN_STROKE, APPEND_POINT, END_STROKE,
                             BEGIN_ERASE,  APPEND_ERASE,  END_ERASE,
                             CANCEL }

    public static final class Event {
        public final EventType type;
        public final float x, y;
        public Event(EventType type, float x, float y) {
            this.type = type; this.x = x; this.y = y;
        }
    }

    public static final class Output {
        public final OutputType type; public final float x, y;
        public Output(OutputType type, float x, float y) {
            this.type = type; this.x = x; this.y = y;
        }
    }

    private final float slop; // in same coordinate system as events
    private Mode mode = Mode.DRAW;

    private boolean inProgress = false;
    private float downX, downY;
    private boolean exceededSlop = false;

    public DrawingGestureRouter(float slop) { this.slop = Math.max(0f, slop); }

    public void setMode(Mode mode) { this.mode = mode == null ? Mode.DRAW : mode; }
    public Mode getMode() { return mode; }

    public List<Output> process(Event e) {
        ArrayList<Output> out = new ArrayList<>();
        switch (e.type) {
            case DOWN:
                downX = e.x; downY = e.y; exceededSlop = false; inProgress = false;
                break;
            case MOVE: {
                if (!exceededSlop && distance(downX, downY, e.x, e.y) >= slop) {
                    exceededSlop = true;
                    if (!inProgress) {
                        // start at down point on first exceed
                        out.add(new Output(mode == Mode.DRAW ? OutputType.BEGIN_STROKE : OutputType.BEGIN_ERASE, downX, downY));
                        inProgress = true;
                    }
                }
                if (inProgress) {
                    out.add(new Output(mode == Mode.DRAW ? OutputType.APPEND_POINT : OutputType.APPEND_ERASE, e.x, e.y));
                }
                break;
            }
            case UP: {
                if (inProgress) {
                    out.add(new Output(mode == Mode.DRAW ? OutputType.END_STROKE : OutputType.END_ERASE, e.x, e.y));
                    inProgress = false;
                } else {
                    // Treat as a tap: synthesize a tiny begin/append/end at the tap location.
                    if (mode == Mode.DRAW) {
                        out.add(new Output(OutputType.BEGIN_STROKE, downX, downY));
                        out.add(new Output(OutputType.APPEND_POINT, e.x, e.y));
                        out.add(new Output(OutputType.END_STROKE, e.x, e.y));
                    } else {
                        out.add(new Output(OutputType.BEGIN_ERASE, downX, downY));
                        out.add(new Output(OutputType.END_ERASE, e.x, e.y));
                    }
                }
                exceededSlop = false;
                break;
            }
            case CANCEL:
                if (inProgress) {
                    out.add(new Output(OutputType.CANCEL, e.x, e.y));
                }
                inProgress = false; exceededSlop = false;
                break;
        }
        return out;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float)Math.hypot(dx, dy);
    }
}

