package org.opendroidpdf;

import android.view.MotionEvent;
import android.view.ViewConfiguration;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;
import org.opendroidpdf.app.AppCoroutines;

/**
 * Tracks stylus activity so MuPDFReaderView can prefer stylus pointers
 * and temporarily block scale gestures right after stylus input.
 */
class StylusGestureHelper {
    private final CoroutineScope scope;
    private Job stylusFlagJob;
    private boolean recentStylusEvent = false;

    StylusGestureHelper(CoroutineScope scope) {
        this.scope = scope;
    }

    /** Return the index of the first stylus pointer, or -1 if none present. */
    int pointerIndexForStylus(MotionEvent event) {
        int pointerIndexOfStylus = -1;
        for (int pointerIndex = 0; pointerIndex < event.getPointerCount(); pointerIndex++) {
            if (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_STYLUS) {
                pointerIndexOfStylus = pointerIndex;
                markRecentStylus();
                break; // first stylus is sufficient
            }
        }
        return pointerIndexOfStylus;
    }

    boolean shouldBlockScale(boolean useStylusMode) {
        return useStylusMode && recentStylusEvent;
    }

    void cancel() {
        AppCoroutines.cancel(stylusFlagJob);
        stylusFlagJob = null;
    }

    private void markRecentStylus() {
        recentStylusEvent = true;
        cancel();
        stylusFlagJob = AppCoroutines.launchMainDelayed(
                scope,
                ViewConfiguration.getLongPressTimeout(),
                () -> recentStylusEvent = false);
    }
}
