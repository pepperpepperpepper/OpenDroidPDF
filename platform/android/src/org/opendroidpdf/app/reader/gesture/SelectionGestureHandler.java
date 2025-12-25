package org.opendroidpdf.app.reader.gesture;

import android.view.MotionEvent;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;

/**
 * Handles text-selection marker drag gestures to keep MuPDFReaderView smaller.
 */
final class SelectionGestureHandler {

    interface Host {
        MuPDFPageView currentPageView();
        ReaderMode mode();
    }

    private final Host host;
    private boolean scrollStartedAtLeftMarker = false;
    private boolean scrollStartedAtRightMarker = false;

    SelectionGestureHandler(Host host) {
        this.host = host;
    }

    /**
     * @return true if the gesture was consumed by selection handling.
     */
    boolean onScroll(MotionEvent e1, MotionEvent e2) {
        if (host.mode() != ReaderMode.SELECTING) return false;
        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return false;

        if (pageView.hitsLeftMarker(e1.getX(), e1.getY()) || scrollStartedAtLeftMarker) {
            scrollStartedAtLeftMarker = true;
            pageView.moveLeftMarker(e2.getX(), e2.getY());
            return true;
        } else if (pageView.hitsRightMarker(e1.getX(), e1.getY()) || scrollStartedAtRightMarker) {
            scrollStartedAtRightMarker = true;
            pageView.moveRightMarker(e2.getX(), e2.getY());
            return true;
        }
        return false;
    }

    void reset() {
        scrollStartedAtLeftMarker = false;
        scrollStartedAtRightMarker = false;
    }
}
