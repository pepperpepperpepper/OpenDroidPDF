package org.opendroidpdf;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import kotlinx.coroutines.CoroutineScope;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.SearchResult;
import org.opendroidpdf.SearchResultsController;
import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.app.annotation.AnnotationModeStore;
import org.opendroidpdf.app.reader.gesture.ReaderGestureController;
import org.opendroidpdf.app.reader.gesture.ReaderMode;

import android.widget.Adapter;

abstract public class MuPDFReaderView extends ReaderView {
    enum Mode {Viewing, Selecting, Drawing, Erasing, AddingTextAnnot, Searching}
    private final Context mContext;
    private boolean mLinksEnabled = true;
    private Mode mMode = Mode.Viewing;
    private int tapPageMargin;
    private final ReaderGestureController gestureController;
    private AnnotationModeStore annotationModeStore;
    
        // To be overridden by the host activity:
    abstract protected void onMoveToChild(int pageNumber);
    abstract protected void onTapMainDocArea();
    abstract protected void onTapTopLeftMargin();
    abstract protected void onBottomRightMargin();
    abstract protected void onDocMotion();
    abstract protected void onHit(Hit item);
    abstract protected void onNumberOfStrokesChanged(int numberOfStrokes);
    abstract protected void addTextAnnotFromUserInput(Annotation annot);

    /**
     * Public hook to allow services/controllers outside this package to notify stroke-count changes
     * without exposing protected callbacks directly.
     */
    public void notifyStrokeCountChanged(int numberOfStrokes) {
        onNumberOfStrokesChanged(numberOfStrokes);
    }

    // Gesture helpers
    private final CoroutineScope gestureScope = AppCoroutines.newMainScope();
    private final SearchResultsController searchResults;
    
    public void setLinksEnabled(boolean b) {
        mLinksEnabled = b;
        resetupChildren();
    }

    public boolean linksEnabled() {
        return mLinksEnabled;
    }

    public void setMode(Mode m) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MuPDFReaderView", "setMode " + mMode + " -> " + m);
        }
        mMode = m;
    }

    public Mode getMode() {
        return mMode;
    }

    /**
     * Injects the app-owned annotation-mode store so gesture-driven mode switches
     * (e.g., stylus down) route through a single owner (DrawingService) rather than
     * mutating {@link #mMode} directly.
     */
    public void setAnnotationModeStore(AnnotationModeStore store) {
        this.annotationModeStore = store;
    }

    /**
     * Request a mode transition. Annotation-related modes are delegated to the injected
     * {@link AnnotationModeStore} so any required side-effects (e.g., committing pending ink)
     * live in one place. Non-annotation modes fall back to {@link #setMode(Mode)}.
     */
    public void requestMode(Mode desiredMode) {
        AnnotationModeStore store = annotationModeStore;
        if (store != null) {
            switch (desiredMode) {
                case Drawing:
                    store.enterDrawingMode();
                    return;
                case Erasing:
                    store.enterErasingMode();
                    return;
                case AddingTextAnnot:
                    store.enterAddingTextMode();
                    return;
                case Viewing:
                    store.enterViewingMode();
                    return;
                default:
                    break;
            }
        }
        setMode(desiredMode);
    }

    // Public helpers to avoid exposing Mode outside the package.
    public void switchToDrawingMode() { setMode(Mode.Drawing); }
    public void switchToErasingMode() { setMode(Mode.Erasing); }
    public void switchToViewingMode() { setMode(Mode.Viewing); }
    public void switchToAddingTextMode() { setMode(Mode.AddingTextAnnot); }
    public void switchToSearchingMode() { setMode(Mode.Searching); }
    public boolean isDrawingModeActive() { return mMode == Mode.Drawing; }
    public boolean isErasingModeActive() { return mMode == Mode.Erasing; }
    public boolean isAddingTextModeActive() { return mMode == Mode.AddingTextAnnot; }
    public boolean isSearchingModeActive() { return mMode == Mode.Searching; }

    public MuPDFReaderView(Activity act) {
        super(act);
        mContext = act;
            // Get the screen size etc to customise tap margins.
            // We calculate the size of 1 inch of the screen for tapping.
            // On some devices the dpi values returned are wrong, so we
            // sanity check it: we first restrict it so that we are never
            // less than 100 pixels (the smallest Android device screen
            // dimension I've seen is 480 pixels or so). Then we check
            // to ensure we are never more than 1/5 of the screen width.
        DisplayMetrics dm = new DisplayMetrics();
        act.getWindowManager().getDefaultDisplay().getMetrics(dm);
        tapPageMargin = (int)dm.xdpi;
        if (tapPageMargin < 100)
            tapPageMargin = 100;
        if (tapPageMargin > dm.widthPixels/5)
            tapPageMargin = dm.widthPixels/5;
        if (tapPageMargin > dm.heightPixels/5)
            tapPageMargin = dm.heightPixels/5;

        searchResults = new SearchResultsController(new SearchResultsController.Host() {
            @Override public int currentPage() { return getSelectedItemPosition(); }
            @Override public void setDisplayedViewIndex(int page) { MuPDFReaderView.this.setDisplayedViewIndex(page); }
            @Override public void doNextScrollWithCenter() { MuPDFReaderView.this.doNextScrollWithCenter(); }
            @Override public void setDocRelXScroll(float docRelXScroll) { MuPDFReaderView.this.setDocRelXScroll(docRelXScroll); }
            @Override public void setDocRelYScroll(float docRelYScroll) { MuPDFReaderView.this.setDocRelYScroll(docRelYScroll); }
            @Override public void resetupChildren() { MuPDFReaderView.this.resetupChildren(); }
        });
        gestureController = new ReaderGestureController(act, gestureScope, new ReaderGestureController.Host() {
            @Override public ReaderMode mode() {
                switch (getMode()) {
                    case Viewing: return ReaderMode.VIEWING;
                    case Selecting: return ReaderMode.SELECTING;
                    case Drawing: return ReaderMode.DRAWING;
                    case Erasing: return ReaderMode.ERASING;
                    case AddingTextAnnot: return ReaderMode.ADDING_TEXT_ANNOT;
                    case Searching: return ReaderMode.SEARCHING;
                    default: return ReaderMode.VIEWING;
                }
            }
            @Override public void requestMode(ReaderMode mode) {
                if (mode == null) return;
                switch (mode) {
                    case VIEWING: MuPDFReaderView.this.requestMode(Mode.Viewing); break;
                    case SELECTING: MuPDFReaderView.this.requestMode(Mode.Selecting); break;
                    case DRAWING: MuPDFReaderView.this.requestMode(Mode.Drawing); break;
                    case ERASING: MuPDFReaderView.this.requestMode(Mode.Erasing); break;
                    case ADDING_TEXT_ANNOT: MuPDFReaderView.this.requestMode(Mode.AddingTextAnnot); break;
                    case SEARCHING: MuPDFReaderView.this.requestMode(Mode.Searching); break;
                }
            }
            @Override public MuPDFPageView currentPageView() { return (MuPDFPageView) getSelectedView(); }
            @Override public boolean linksEnabled() { return mLinksEnabled; }
            @Override public int tapPageMargin() { return tapPageMargin; }
            @Override public void onDocMotion() { MuPDFReaderView.this.onDocMotion(); }
            @Override public void onHit(Hit item) { MuPDFReaderView.this.onHit(item); }
            @Override public void onTapMainDocArea() { MuPDFReaderView.this.onTapMainDocArea(); }
            @Override public void onTapTopLeftMargin() { MuPDFReaderView.this.onTapTopLeftMargin(); }
            @Override public void onBottomRightMargin() { MuPDFReaderView.this.onBottomRightMargin(); }
            @Override public void addTextAnnotation(Annotation annot) { MuPDFReaderView.this.addTextAnnotion(annot); }
            @Override public void onNumberOfStrokesChanged(int strokes) { MuPDFReaderView.this.onNumberOfStrokesChanged(strokes); }
            @Override public boolean maySwitchView() { return MuPDFReaderView.this.maySwitchView(); }
            @Override public boolean useStylus() { return mUseStylus; }
            @Override public View rootView() { return MuPDFReaderView.this; }
            @Override public boolean superOnDown(MotionEvent e) { return MuPDFReaderView.super.onDown(e); }
            @Override public boolean superOnScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) { return MuPDFReaderView.super.onScroll(e1, e2, dx, dy); }
            @Override public boolean superOnFling(MotionEvent e1, MotionEvent e2, float vx, float vy) { return MuPDFReaderView.super.onFling(e1, e2, vx, vy); }
            @Override public boolean superOnScaleBegin(ScaleGestureDetector d) { return MuPDFReaderView.super.onScaleBegin(d); }
            @Override public boolean superOnTouchEvent(MotionEvent event) { return MuPDFReaderView.super.onTouchEvent(event); }
        });
    }

    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);
        if (!(adapter instanceof MuPDFPageAdapter)) return;
        ((MuPDFPageAdapter) adapter).setModeRequester(mode -> {
            if (mode == null) return;
            switch (mode) {
                case VIEWING: MuPDFReaderView.this.requestMode(Mode.Viewing); break;
                case SELECTING: MuPDFReaderView.this.requestMode(Mode.Selecting); break;
                case DRAWING: MuPDFReaderView.this.requestMode(Mode.Drawing); break;
                case ERASING: MuPDFReaderView.this.requestMode(Mode.Erasing); break;
                case ADDING_TEXT_ANNOT: MuPDFReaderView.this.requestMode(Mode.AddingTextAnnot); break;
                case SEARCHING: MuPDFReaderView.this.requestMode(Mode.Searching); break;
            }
        });
    }

    // Debug-only helpers invoked from DebugActionsController
    public void debugShowTextWidgetDialog() {
        try {
            MuPDFPageView cv = (MuPDFPageView) getSelectedView();
            if (cv != null) cv.debugShowTextWidgetDialog();
        } catch (Throwable ignore) {}
    }

    public void debugShowChoiceWidgetDialog() {
        try {
            MuPDFPageView cv = (MuPDFPageView) getSelectedView();
            if (cv != null) cv.debugShowChoiceWidgetDialog();
        } catch (Throwable ignore) {}
    }

    public boolean onSingleTapUp(MotionEvent e) {
        MuPDFView pageView = (MuPDFView)getSelectedView();
        if (pageView == null ) return super.onSingleTapUp(e);
        gestureController.onSingleTapUp(e);
        return super.onSingleTapUp(e);
    }


    protected void addTextAnnotion(Annotation annot) {
        MuPDFView pageView = (MuPDFView)getSelectedView();
        ((MuPDFPageView)pageView).addTextAnnotation(annot);
    }
    
    @Override
    public boolean onDown(MotionEvent e) {
        return gestureController.onDown(e);
    }

    
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {
        return gestureController.onScroll(e1, e2, distanceX, distanceY);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                           float velocityY) {
        return gestureController.onFling(e1, e2, velocityX, velocityY);
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector d) {
        return gestureController.onScaleBegin(d);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureController.onTouchEvent(event);
    }

    public SearchResultsController searchResults() { return searchResults; }
    public void addSearchResult(SearchResult result) { searchResults.addResult(result); }
    public void clearSearchResults() { searchResults.clear(); }
    public boolean hasSearchResults() { return searchResults.hasResults(); }
    public void goToNextSearchResult(int direction) { searchResults.goToNext(direction); }
    
    
    @Override
    protected void onChildSetup(int i, View v) {
        searchResults.applyToView(i, (MuPDFView) v);
        ((MuPDFView) v).setLinkHighlighting(mLinksEnabled);

        ((MuPDFView) v).setChangeReporter(new Runnable() {
                public void run() {
                    applyToChildren(new ReaderView.ViewMapper() {
                            @Override
                            void applyToView(View view) {
                                ((MuPDFView) view).redraw(true);
                            }
                        });
                }
            });
    }

    @Override
    protected void onMoveOffChild(int i) {
        View v = getView(i);
        if (v != null)
            ((MuPDFView)v).deselectAnnotation();
    }

    @Override
    protected void onSettle(View v) {
            // When the layout has settled ask the page to render in HQ
        ((MuPDFView) v).addHq(false);
    }

    @Override
    protected void onUnsettle(View v) {
            // When something changes making the previous settled view
            // no longer appropriate, tell the page to remove HQ
        ((MuPDFView) v).removeHq();
    }

    @Override
    protected void onScaleChild(View v, Float scale) {
        ((MuPDFView) v).setScale(scale);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("superInstanceState", super.onSaveInstanceState());
            //Save
        bundle.putString("mMode", mMode.toString());
        bundle.putInt("tapPageMargin", tapPageMargin);
        if(getSelectedView() != null) bundle.putParcelable("displayedViewInstanceState", ((PageView)getSelectedView()).onSaveInstanceState());
        
        return bundle;
    }
    
    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
                //Load 
            try {
                Mode restoredMode = Mode.valueOf(bundle.getString("mMode", mMode.toString()));
                // Route restoration through requestMode so any injected mode owners
                // (e.g., AnnotationModeStore/DrawingService) stay in sync with mMode.
                requestMode(restoredMode);
            } catch (Throwable ignore) {
                // Keep best-effort restore behavior; fall back to current mode.
            }
            tapPageMargin = bundle.getInt("tapPageMargin", tapPageMargin);
            if(getSelectedView() != null)
                ((PageView)getSelectedView()).onRestoreInstanceState(bundle.getParcelable("displayedViewInstanceState"));
            else
                displayedViewInstanceState = bundle.getParcelable("displayedViewInstanceState");
            
            state = bundle.getParcelable("superInstanceState");
        }
        super.onRestoreInstanceState(state);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AppCoroutines.cancelScope(gestureScope);
    }

    @Override
    public boolean maySwitchView() {
        return mMode.equals(Mode.Viewing) || mMode.equals(Mode.Searching);
    }

}
