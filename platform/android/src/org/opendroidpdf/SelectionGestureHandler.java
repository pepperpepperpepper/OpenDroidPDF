package org.opendroidpdf;

import android.view.MotionEvent;

/**
 * Handles text-selection marker drag gestures to keep MuPDFReaderView smaller.
 */
final class SelectionGestureHandler {

    interface Host {
        MuPDFPageView currentPageView();
        MuPDFReaderView.Mode mode();
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
        if (host.mode() != MuPDFReaderView.Mode.Selecting) return false;
        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return false;

        if (pageView.hitsLeftMarker(e1.getX(), e1.getY()) || scrollStartedAtLeftMarker) {
            scrollStartedAtLeftMarker = true;
            pageView.moveLeftMarker(e2);
            return true;
        } else if (pageView.hitsRightMarker(e1.getX(), e1.getY()) || scrollStartedAtRightMarker) {
            scrollStartedAtRightMarker = true;
            pageView.moveRightMarker(e2);
            return true;
        }
        return false;
    }

    void reset() {
        scrollStartedAtLeftMarker = false;
        scrollStartedAtRightMarker = false;
    }
}
