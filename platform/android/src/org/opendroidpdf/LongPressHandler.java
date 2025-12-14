package org.opendroidpdf;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.MuPDFReaderView.Mode;

/**
 * Handles long-press scheduling and dispatch for MuPDFReaderView.
 */
class LongPressHandler {
    interface Host {
        MuPDFPageView currentPageView();
        Mode currentMode();
        void setMode(Mode mode);
        void onNumberOfStrokesChanged(int strokes);
        View rootView();
    }

    private final Context context;
    private final CoroutineScope scope;
    private final Host host;
    private Job longPressJob;
    private MotionEvent startEvent;

    LongPressHandler(Context context, CoroutineScope scope, Host host) {
        this.context = context;
        this.scope = scope;
        this.host = host;
    }

    void onDown(MotionEvent e, boolean useStylus) {
        MuPDFPageView cv = host.currentPageView();
        if (cv == null) return;
        Mode mode = host.currentMode();
        if (!isLongPressMode(mode)) return;
        if (cv.hitsLeftMarker(e.getX(), e.getY()) || cv.hitsRightMarker(e.getX(), e.getY())) return;

        startEvent = e;
        scheduleLongPress(useStylus);
    }

    void cancelIfMoved(MotionEvent e1) {
        if (startEvent == null) return;
        float slop = ViewConfiguration.get(context).getScaledTouchSlop();
        if (Math.abs(startEvent.getX() - e1.getX()) > slop ||
            Math.abs(startEvent.getY() - e1.getY()) > slop) {
            cancel();
        }
    }

    void onUpOrCancel() {
        cancel();
    }

    private void scheduleLongPress(boolean useStylus) {
        cancel();
        long delay = ViewConfiguration.getLongPressTimeout() * (useStylus ? 2 : 1);
        longPressJob = AppCoroutines.launchMainDelayed(scope, delay, this::handleLongPress);
    }

    private void handleLongPress() {
        MuPDFPageView cv = host.currentPageView();
        if (cv == null || startEvent == null) return;

        Mode mode = host.currentMode();
        if (mode == Mode.Drawing && ReaderView.mUseStylus && cv.getDrawingSize() == 1) {
            cv.undoDraw();
            host.onNumberOfStrokesChanged(cv.getDrawingSize());
            cv.saveDraw();
            host.setMode(Mode.Viewing);
        } else if (mode == Mode.Viewing || mode == Mode.Selecting) {
            selectText(cv);
        }
        cancel();
    }

    private void selectText(MuPDFPageView cv) {
        int[] locationOnScreen = new int[] {0, 0};
        host.rootView().getLocationOnScreen(locationOnScreen);
        cv.deselectAnnotation();
        cv.deselectText();
        cv.selectText(
                startEvent.getX(),
                startEvent.getRawY() - locationOnScreen[1],
                startEvent.getX() + 1,
                startEvent.getRawY() + 1 - locationOnScreen[1]);
        if (cv.hasTextSelected()) {
            host.setMode(Mode.Selecting);
        } else {
            cv.deselectText();
            host.setMode(Mode.Viewing);
        }
    }

    private boolean isLongPressMode(Mode mode) {
        return mode == Mode.Viewing || mode == Mode.Selecting || mode == Mode.Drawing;
    }

    private void cancel() {
        AppCoroutines.cancel(longPressJob);
        longPressJob = null;
        startEvent = null;
    }
}
