package org.opendroidpdf.app.reader.gesture;

import android.view.MotionEvent;

/**
 * Centralizes minor gesture state bookkeeping (tapDisabled + selection reset)
 * so MuPDFReaderView can shed boilerplate.
 */
final class GestureStateHelper {
    interface Host {
        void onLongPressCancel();
        void resetSelectionDragState();
    }

    private final Host host;
    private boolean tapDisabled = false;

    GestureStateHelper(Host host) {
        this.host = host;
    }

    void onActionDown() {
        tapDisabled = false;
    }

    void onActionUp(MotionEvent event) {
        host.resetSelectionDragState();
        host.onLongPressCancel();
    }

    boolean isTapDisabled() {
        return tapDisabled;
    }

    void disableTapDuringScale() {
        tapDisabled = true;
        host.onLongPressCancel();
    }
}
