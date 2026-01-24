package org.opendroidpdf.app.reader.gesture;

import android.view.MotionEvent;

import org.opendroidpdf.DrawingController;
import org.opendroidpdf.Hit;
import org.opendroidpdf.MuPDFPageView;

import java.util.ArrayList;

/**
 * Handles drawing/erasing gestures and stylus-driven mode switching for MuPDFReaderView.
 */
class DrawingGestureHandler {
    interface Host {
        MuPDFPageView pageView();
        ReaderMode mode();
        void requestMode(ReaderMode mode);
        void onStrokesChanged(int strokes);
        void deselectAnnotation();
    }

    private final Host host;
    private final StylusGestureHelper stylusHelper;

    // Once we detect multi-touch during a draw gesture, ignore subsequent single-pointer MOVE
    // events until the pointer is lifted. This prevents pinch-zoom from resuming drawing mid-gesture.
    private boolean ignoreDrawUntilUp = false;

    DrawingGestureHandler(Host host, StylusGestureHelper stylusHelper) {
        this.host = host;
        this.stylusHelper = stylusHelper;
    }

    /**
     * Process drawing/erasing touch handling. Returns true if consumed.
     */
    boolean handle(MotionEvent event, boolean useStylus) {
        MuPDFPageView pageView = host.pageView();
        if (pageView == null) return false;

        if (!useStylus && host.mode() == ReaderMode.DRAWING) {
            final int masked = event.getActionMasked();
            if (ignoreDrawUntilUp) {
                if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL) {
                    ignoreDrawUntilUp = false;
                }
                return false;
            }
            if (event.getPointerCount() > 1) {
                handleDrawingMultiTouch(event, pageView);
                ignoreDrawUntilUp = true;
                return false;
            }
        }

        int pointerIndexToUse = 0;
        if (useStylus) {
            int stylusIndex = stylusHelper.pointerIndexForStylus(event);
            pointerIndexToUse = stylusIndex;
            if (pointerIndexToUse < 0) {
                return false; // no stylus pointer present
            }

            if (host.mode() == ReaderMode.VIEWING &&
                event.getActionIndex() == pointerIndexToUse &&
                event.getAction() == MotionEvent.ACTION_DOWN) {
                Hit item = pageView.clickWouldHit(event);
                if (item != null && Hit.InkAnnotation.equals(item)) {
                    pageView.passClickEvent(event);
                    pageView.editSelectedAnnotation();
                } else {
                    pageView.deselectAnnotation();
                    host.requestMode(ReaderMode.DRAWING);
                }
            }
        }

        if (event.getActionIndex() != pointerIndexToUse && useStylus) {
            return false; // stylus mode but event isn't for stylus pointer
        }

        final float x = event.getX(pointerIndexToUse);
        final float y = event.getY(pointerIndexToUse);

        switch (host.mode()) {
            case DRAWING:
                handleDrawing(event, pageView, x, y, pointerIndexToUse);
                return true;
            case ERASING:
                handleErasing(event, pageView, x, y);
                return true;
            default:
                return false;
        }
    }

    private void handleDrawingMultiTouch(MotionEvent event, MuPDFPageView pageView) {
        // Pinch/zoom should not generate ink. Best-effort: if the user hasn't actually drawn yet
        // (only a single point from the first finger-down), drop that stroke entirely.
        try {
            boolean finished = false;
            DrawingController dc = pageView.getDrawingController();
            if (dc != null) {
                ArrayList<ArrayList<android.graphics.PointF>> strokes = dc.getDrawing();
                if (strokes != null && !strokes.isEmpty()) {
                    ArrayList<android.graphics.PointF> last = strokes.get(strokes.size() - 1);
                    if (last != null && last.size() <= 1) {
                        dc.undoDraw();
                    } else {
                        // If there's already ink movement, keep it but end the stroke now.
                        pageView.finishDraw();
                        finished = true;
                    }
                }
            }
            // Ensure the ink controller isn't left in an "active gesture" state.
            if (!finished) pageView.finishDraw();
            host.onStrokesChanged(pageView.getDrawingSize());
        } catch (Throwable ignore) {
        }
    }

    private void handleDrawing(MotionEvent event, MuPDFPageView pageView, float x, float y, int pointerIndex) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pageView.startDraw(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                final int historySize = event.getHistorySize();
                for (int h = 0; h < historySize; h++) {
                    pageView.continueDraw(event.getHistoricalX(pointerIndex, h), event.getHistoricalY(pointerIndex, h));
                }
                pageView.continueDraw(x, y);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pageView.finishDraw();
                host.onStrokesChanged(pageView.getDrawingSize());
                break;
        }
    }

    private void handleErasing(MotionEvent event, MuPDFPageView pageView, float x, float y) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pageView.startErase(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                pageView.continueErase(x, y);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pageView.finishErase(x, y);
                break;
        }
    }
}
