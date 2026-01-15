package org.opendroidpdf.app.reader.gesture;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.Annotation;

/**
 * Handles long-press scheduling and dispatch for MuPDFReaderView.
 */
class LongPressHandler {
    interface Host {
        MuPDFPageView currentPageView();
        ReaderMode currentMode();
        void requestMode(ReaderMode mode);
        void onNumberOfStrokesChanged(int strokes);
        View rootView();
    }

    private final Context context;
    private final CoroutineScope scope;
    private final Host host;
    private Job longPressJob;
    private Job selectionRetryJob;
    private MotionEvent startEvent;
    private boolean startUseStylus;
    private boolean startOnSelectedTextAnnotation;

    LongPressHandler(Context context, CoroutineScope scope, Host host) {
        this.context = context;
        this.scope = scope;
        this.host = host;
    }

    void onDown(MotionEvent e, boolean useStylus) {
        MuPDFPageView cv = host.currentPageView();
        if (cv == null) return;
        ReaderMode mode = host.currentMode();
        if (!isLongPressMode(mode)) return;
        if (cv.hitsLeftMarker(e.getX(), e.getY()) || cv.hitsRightMarker(e.getX(), e.getY())) return;

        // If a text annotation is currently selected and the user presses inside its bounds,
        // treat the gesture as "annotation interaction" rather than "select underlying text".
        // Keep the selection stable and do not enter text selection mode.
        if (mode == ReaderMode.VIEWING || mode == ReaderMode.SELECTING) {
            try {
                float scale = cv.getScale();
                if (scale > 0f) {
                    float docX = (e.getX() - cv.getLeft()) / scale;
                    float docY = (e.getY() - cv.getTop()) / scale;
                    Annotation selected = cv.textAnnotationDelegate().selectedEmbeddedAnnotationOrNull();
                    if (selected != null && (selected.type == Annotation.Type.FREETEXT || selected.type == Annotation.Type.TEXT)) {
                        if (selected.contains(docX, docY)) {
                            startEvent = e;
                            startUseStylus = useStylus;
                            startOnSelectedTextAnnotation = true;
                            scheduleLongPress(useStylus);
                            return;
                        }
                    }
                    org.opendroidpdf.app.selection.SidecarSelectionController.Selection sel = cv.selectedSidecarSelectionOrNull();
                    if (sel != null
                            && sel.kind == org.opendroidpdf.app.selection.SidecarSelectionController.Kind.NOTE
                            && sel.bounds != null
                            && sel.bounds.contains(docX, docY)) {
                        startEvent = e;
                        startUseStylus = useStylus;
                        startOnSelectedTextAnnotation = true;
                        scheduleLongPress(useStylus);
                        return;
                    }
                }
            } catch (Throwable ignore) {
            }
        }

        // New interaction: cancel any pending async selection retries from a prior long-press.
        AppCoroutines.cancel(selectionRetryJob);
        selectionRetryJob = null;

        startEvent = e;
        startUseStylus = useStylus;
        startOnSelectedTextAnnotation = false;
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
        // Cancel only the pending job; keep startEvent/startUseStylus for the new press.
        AppCoroutines.cancel(longPressJob);
        longPressJob = null;
        long delay = ViewConfiguration.getLongPressTimeout() * (useStylus ? 2 : 1);
        longPressJob = AppCoroutines.launchMainDelayed(scope, delay, this::handleLongPress);
    }

    private void handleLongPress() {
        MuPDFPageView cv = host.currentPageView();
        if (cv == null || startEvent == null) return;

        if (startOnSelectedTextAnnotation) {
            cancel();
            return;
        }

        ReaderMode mode = host.currentMode();
        if (mode == ReaderMode.DRAWING && startUseStylus && cv.getDrawingSize() == 1) {
            cv.undoDraw();
            host.onNumberOfStrokesChanged(cv.getDrawingSize());
            cv.saveDraw();
            host.requestMode(ReaderMode.VIEWING);
        } else if (mode == ReaderMode.VIEWING || mode == ReaderMode.SELECTING) {
            selectText(cv);
        }
        cancel();
    }

    private void selectText(MuPDFPageView cv) {
        final MuPDFPageView target = cv;
        int[] locationOnScreen = new int[] {0, 0};
        host.rootView().getLocationOnScreen(locationOnScreen);
        cv.deselectAnnotation();
        cv.deselectText();
        // Use a small-but-not-tiny box to improve hit rate (especially on reflow docs).
        final float x0 = startEvent.getX();
        final float y0 = startEvent.getRawY() - locationOnScreen[1];
        final float x1 = x0 + 12;
        final float y1 = y0 + 12;
        cv.selectText(
                x0,
                y0,
                x1,
                y1);

        // Text extraction runs async; on fresh loads the first selection attempt can race
        // and incorrectly fail. Retry for a short window so long-press selection is reliable.
        boolean selectedNow = cv.hasTextSelected();
        if (selectedNow) {
            host.requestMode(ReaderMode.SELECTING);
            return;
        }

        AppCoroutines.cancel(selectionRetryJob);
        selectionRetryJob = AppCoroutines.launchMainDelayed(scope, 120, new Runnable() {
            int attempts = 0;

            @Override public void run() {
                // Give up if the page changed or mode changed out of selection-compatible states.
                MuPDFPageView current = host.currentPageView();
                ReaderMode m = host.currentMode();
                if (current != target || (m != ReaderMode.VIEWING && m != ReaderMode.SELECTING)) {
                    selectionRetryJob = null;
                    return;
                }

                if (target.hasTextSelected()) {
                    host.requestMode(ReaderMode.SELECTING);
                    selectionRetryJob = null;
                    return;
                }

                attempts++;
                if (attempts >= 8) {
                    target.deselectText();
                    host.requestMode(ReaderMode.VIEWING);
                    selectionRetryJob = null;
                    return;
                }

                // Reschedule.
                selectionRetryJob = AppCoroutines.launchMainDelayed(scope, 120, this);
            }
        });
    }

    private boolean isLongPressMode(ReaderMode mode) {
        return mode == ReaderMode.VIEWING || mode == ReaderMode.SELECTING || mode == ReaderMode.DRAWING;
    }

    private void cancel() {
        AppCoroutines.cancel(longPressJob);
        longPressJob = null;
        startEvent = null;
        startUseStylus = false;
        startOnSelectedTextAnnotation = false;
    }
}
