package org.opendroidpdf.app.drawing;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DrawingGestureRouterTest {

    @Test
    public void tapProducesBeginAppendEnd() {
        DrawingGestureRouter r = new DrawingGestureRouter(5f);
        r.setMode(DrawingGestureRouter.Mode.DRAW);
        r.process(new DrawingGestureRouter.Event(DrawingGestureRouter.EventType.DOWN, 10, 10));
        List<DrawingGestureRouter.Output> outs = r.process(new DrawingGestureRouter.Event(DrawingGestureRouter.EventType.UP, 10, 10));
        assertEquals(3, outs.size());
        assertEquals(DrawingGestureRouter.OutputType.BEGIN_STROKE, outs.get(0).type);
        assertEquals(DrawingGestureRouter.OutputType.APPEND_POINT, outs.get(1).type);
        assertEquals(DrawingGestureRouter.OutputType.END_STROKE, outs.get(2).type);
    }

    @Test
    public void moveBeyondSlopBeginsOnFirstMove() {
        DrawingGestureRouter r = new DrawingGestureRouter(3f);
        r.setMode(DrawingGestureRouter.Mode.DRAW);
        r.process(new DrawingGestureRouter.Event(DrawingGestureRouter.EventType.DOWN, 0, 0));
        // move small: no output
        assertTrue(r.process(new DrawingGestureRouter.Event(DrawingGestureRouter.EventType.MOVE, 1, 1)).isEmpty());
        // exceed slop: begin + append
        List<DrawingGestureRouter.Output> outs = r.process(new DrawingGestureRouter.Event(DrawingGestureRouter.EventType.MOVE, 4, 0));
        assertEquals(2, outs.size());
        assertEquals(DrawingGestureRouter.OutputType.BEGIN_STROKE, outs.get(0).type);
        assertEquals(DrawingGestureRouter.OutputType.APPEND_POINT, outs.get(1).type);
        // up ends stroke
        outs = r.process(new DrawingGestureRouter.Event(DrawingGestureRouter.EventType.UP, 4, 0));
        assertEquals(1, outs.size());
        assertEquals(DrawingGestureRouter.OutputType.END_STROKE, outs.get(0).type);
    }

    @Test
    public void eraseModeUsesEraseSignals() {
        DrawingGestureRouter r = new DrawingGestureRouter(0f);
        r.setMode(DrawingGestureRouter.Mode.ERASE);
        List<DrawingGestureRouter.Output> outs;
        r.process(new DrawingGestureRouter.Event(DrawingGestureRouter.EventType.DOWN, 2, 2));
        outs = r.process(new DrawingGestureRouter.Event(DrawingGestureRouter.EventType.MOVE, 3, 3));
        boolean hasBeginErase = false;
        for (DrawingGestureRouter.Output o : outs) {
            if (o.type == DrawingGestureRouter.OutputType.BEGIN_ERASE) {
                hasBeginErase = true;
                break;
            }
        }
        assertTrue(hasBeginErase);
        outs = r.process(new DrawingGestureRouter.Event(DrawingGestureRouter.EventType.UP, 3, 3));
        assertEquals(DrawingGestureRouter.OutputType.END_ERASE, outs.get(0).type);
    }
}
