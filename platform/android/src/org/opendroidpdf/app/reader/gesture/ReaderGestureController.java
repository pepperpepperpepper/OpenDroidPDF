package org.opendroidpdf.app.reader.gesture;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

import kotlinx.coroutines.CoroutineScope;
import org.opendroidpdf.Annotation;
import org.opendroidpdf.Hit;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController;

/**
 * Centralizes gesture routing for MuPDFReaderView so the view only delegates.
 */
public class ReaderGestureController {
    public interface Host {
        ReaderMode mode();
        void requestMode(ReaderMode mode);
        MuPDFPageView currentPageView();
        boolean linksEnabled();
        int tapPageMargin();
        void onDocMotion();
        void onHit(Hit item);
        void onTapMainDocArea();
        void onTapTopLeftMargin();
        void onBottomRightMargin();
        void addTextAnnotation(Annotation annot);
        void onNumberOfStrokesChanged(int strokes);
        boolean maySwitchView();
        boolean useStylus();
        View rootView();
        // super delegates
        boolean superOnDown(MotionEvent e);
        boolean superOnScroll(MotionEvent e1, MotionEvent e2, float dx, float dy);
        boolean superOnFling(MotionEvent e1, MotionEvent e2, float vx, float vy);
        boolean superOnScaleBegin(ScaleGestureDetector d);
        boolean superOnTouchEvent(MotionEvent event);
    }

    private final Host host;
    private final GestureStateHelper gestureState;
    private final StylusGestureHelper stylusHelper;
    private final LongPressHandler longPressHandler;
    private final DrawingGestureHandler drawingGestureHandler;
    private final SelectionGestureHandler selectionGestureHandler;
    private final TapGestureRouter tapRouter;
    private final TextAnnotationManipulationGestureHandler textAnnotGestureHandler;

    public ReaderGestureController(Activity activity,
                                   CoroutineScope gestureScope,
                                   Host host) {
        this.host = host;
        this.longPressHandler = new LongPressHandler(activity, gestureScope, new LongPressHandler.Host() {
            @Override public MuPDFPageView currentPageView() { return host.currentPageView(); }
            @Override public ReaderMode currentMode() { return host.mode(); }
            @Override public void requestMode(ReaderMode mode) { host.requestMode(mode); }
            @Override public void onNumberOfStrokesChanged(int strokes) { host.onNumberOfStrokesChanged(strokes); }
            @Override public View rootView() { return host.rootView(); }
        });
        this.stylusHelper = new StylusGestureHelper(gestureScope);
        this.drawingGestureHandler = new DrawingGestureHandler(new DrawingGestureHandler.Host() {
            @Override public MuPDFPageView pageView() { return host.currentPageView(); }
            @Override public ReaderMode mode() { return host.mode(); }
            @Override public void requestMode(ReaderMode mode) { host.requestMode(mode); }
            @Override public void onStrokesChanged(int strokes) { host.onNumberOfStrokesChanged(strokes); }
            @Override public void deselectAnnotation() {
                MuPDFPageView pv = host.currentPageView();
                if (pv != null) pv.deselectAnnotation();
            }
        }, stylusHelper);
        this.selectionGestureHandler = new SelectionGestureHandler(new SelectionGestureHandler.Host() {
            @Override public MuPDFPageView currentPageView() { return host.currentPageView(); }
            @Override public ReaderMode mode() { return host.mode(); }
        });
        this.tapRouter = new TapGestureRouter(new TapGestureRouter.Host() {
            @Override public MuPDFPageView currentPageView() { return host.currentPageView(); }
            @Override public MuPDFReaderView reader() {
                View root = host.rootView();
                return root instanceof MuPDFReaderView ? (MuPDFReaderView) root : null;
            }
            @Override public boolean isTapDisabled() { return gestureState.isTapDisabled(); }
            @Override public int tapPageMargin() { return host.tapPageMargin(); }
            @Override public boolean linksEnabled() { return host.linksEnabled(); }
            @Override public ReaderMode mode() { return host.mode(); }
            @Override public void requestMode(ReaderMode mode) { host.requestMode(mode); }
            @Override public void onHit(Hit item) { host.onHit(item); }
            @Override public void onTapMainDocArea() { host.onTapMainDocArea(); }
            @Override public void onTapTopLeftMargin() { host.onTapTopLeftMargin(); }
            @Override public void onBottomRightMargin() { host.onBottomRightMargin(); }
            @Override public void addTextAnnotation(Annotation annot) { host.addTextAnnotation(annot); }
        });
        this.textAnnotGestureHandler = new TextAnnotationManipulationGestureHandler(
                activity.getResources(),
                () -> host.currentPageView()
        );
        this.gestureState = new GestureStateHelper(new GestureStateHelper.Host() {
            @Override public void onLongPressCancel() { longPressHandler.onUpOrCancel(); }
            @Override public void resetSelectionDragState() { selectionGestureHandler.reset(); }
        });
    }

    public void setTextAnnotationMultiSelectController(@Nullable TextAnnotationMultiSelectController controller) {
        try { textAnnotGestureHandler.setMultiSelectController(controller); } catch (Throwable ignore) {}
    }

    public void onSingleTapUp(MotionEvent e) {
        longPressHandler.onUpOrCancel();
        tapRouter.handleSingleTap(e);
    }

    public boolean onDown(MotionEvent e) {
        longPressHandler.onDown(e, host.useStylus());
        return host.superOnDown(e);
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        longPressHandler.cancelIfMoved(e1);
        // Direct manipulation of selected text annotations (move/resize) must win over panning.
        // Only consumes when the gesture begins on the selected annotation/handles.
        try {
            if (host.mode() == ReaderMode.VIEWING
                    || host.mode() == ReaderMode.SEARCHING
                    || host.mode() == ReaderMode.ADDING_TEXT_ANNOT) {
                if (textAnnotGestureHandler.onScroll(e1, e2)) return true;
            }
        } catch (Throwable ignore) {
        }
        switch (host.mode()) {
            case VIEWING:
            case SEARCHING:
            case ADDING_TEXT_ANNOT:
                if (!gestureState.isTapDisabled()) host.onDocMotion();
                return host.superOnScroll(e1, e2, distanceX, distanceY);
            case SELECTING:
                if (selectionGestureHandler.onScroll(e1, e2)) return true;
                return host.superOnScroll(e1, e2, distanceX, distanceY);
            default:
                return true;
        }
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        longPressHandler.onUpOrCancel();
        // If the user starts a gesture on a selected text annotation, treat any fling as part of
        // the manipulation and suppress view switching (page changes).
        try {
            if (host.mode() == ReaderMode.VIEWING
                    || host.mode() == ReaderMode.SEARCHING
                    || host.mode() == ReaderMode.ADDING_TEXT_ANNOT) {
                if (textAnnotGestureHandler.shouldConsumeFling(e1)) return true;
                // When a text annotation is selected, prefer stability over accidental page flips.
                // Users can tap away to deselect, or use explicit navigation controls.
                if (textAnnotGestureHandler.hasSelectedTextAnnotation()) return true;
            }
        } catch (Throwable ignore) {
        }
        if (host.maySwitchView()) {
            return host.superOnFling(e1, e2, velocityX, velocityY);
        } else {
            return true;
        }
    }

    public boolean onScaleBegin(ScaleGestureDetector d) {
        if (stylusHelper.shouldBlockScale(host.useStylus())) return false;
        longPressHandler.onUpOrCancel();
        gestureState.disableTapDuringScale();
        return host.superOnScaleBegin(d);
    }

    public boolean onTouchEvent(MotionEvent event) {
        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) host.superOnTouchEvent(event);

        // Commit (or revert) text-annotation move/resize on ACTION_UP/CANCEL.
        try { textAnnotGestureHandler.onTouchEvent(event); } catch (Throwable ignore) {}

        if (event.getAction() == MotionEvent.ACTION_UP) {
            gestureState.onActionUp(event);
        }
        drawingGestureHandler.handle(event, host.useStylus());

        if ((event.getAction() & event.getActionMasked()) == MotionEvent.ACTION_DOWN) {
            gestureState.onActionDown();
        }

        return host.superOnTouchEvent(event);
    }
}
