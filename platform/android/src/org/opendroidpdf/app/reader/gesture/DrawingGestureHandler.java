package org.opendroidpdf.app.reader.gesture;

import android.view.MotionEvent;

import org.opendroidpdf.Hit;
import org.opendroidpdf.MuPDFPageView;

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
