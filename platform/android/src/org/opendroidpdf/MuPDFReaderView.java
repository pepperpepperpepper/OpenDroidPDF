package org.opendroidpdf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.lang.Math;

import kotlinx.coroutines.CoroutineScope;
import org.opendroidpdf.app.AppCoroutines;

abstract public class MuPDFReaderView extends ReaderView {
    enum Mode {Viewing, Selecting, Drawing, Erasing, AddingTextAnnot, Searching}
    private final Context mContext;
    private boolean mLinksEnabled = true;
    private Mode mMode = Mode.Viewing;
    private boolean tapDisabled = false;
    private int tapPageMargin;
    private final TapGestureRouter tapRouter;
    
        //To be overwritten in OpenDroidPDFActivity:
    abstract protected void onMoveToChild(int pageNumber);
    abstract protected void onTapMainDocArea();
    abstract protected void onTapTopLeftMargin();
    abstract protected void onBottomRightMargin();
    abstract protected void onDocMotion();
    abstract protected void onHit(Hit item);
    abstract protected void onNumberOfStrokesChanged(int numberOfStrokes);
    abstract protected void addTextAnnotFromUserInput(Annotation annot);

    // Gesture helpers
    private final CoroutineScope gestureScope = AppCoroutines.newMainScope();
    private final StylusGestureHelper stylusHelper = new StylusGestureHelper(gestureScope);
    private final LongPressHandler longPressHandler;
    private final DrawingGestureHandler drawingGestureHandler;
    private final SearchResultNavigator searchNavigator;
    private final LongPressHandler.Host longPressHost = new LongPressHandler.Host() {
        @Override public MuPDFPageView currentPageView() { return (MuPDFPageView) getSelectedView(); }
        @Override public Mode currentMode() { return mMode; }
        @Override public void setMode(Mode mode) { mMode = mode; }
        @Override public void onNumberOfStrokesChanged(int strokes) { MuPDFReaderView.this.onNumberOfStrokesChanged(strokes); }
        @Override public View rootView() { return MuPDFReaderView.this; }
    };
    
    public void setLinksEnabled(boolean b) {
        mLinksEnabled = b;
        resetupChildren();
    }

    public boolean linksEnabled() {
        return mLinksEnabled;
    }

    public void setMode(Mode m) {
        mMode = m;
    }

    public Mode getMode() {
        return mMode;
    }

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

        longPressHandler = new LongPressHandler(act, gestureScope, longPressHost);
        drawingGestureHandler = new DrawingGestureHandler(new DrawingHost(), stylusHelper);
        searchNavigator = new SearchResultNavigator(searchHost);
        selectionGestureHandler = new SelectionGestureHandler(new SelectionGestureHandler.Host() {
            @Override public MuPDFPageView currentPageView() { return (MuPDFPageView) getSelectedView(); }
            @Override public Mode mode() { return mMode; }
        });
        tapRouter = new TapGestureRouter(new TapGestureRouter.Host() {
            @Override public MuPDFPageView currentPageView() { return (MuPDFPageView) getSelectedView(); }
            @Override public MuPDFReaderView reader() { return MuPDFReaderView.this; }
            @Override public boolean isTapDisabled() { return tapDisabled; }
            @Override public int tapPageMargin() { return tapPageMargin; }
            @Override public boolean linksEnabled() { return mLinksEnabled; }
            @Override public Mode mode() { return mMode; }
            @Override public void setMode(Mode mode) { mMode = mode; }
            @Override public void onHit(Hit item) { MuPDFReaderView.this.onHit(item); }
            @Override public void onTapMainDocArea() { MuPDFReaderView.this.onTapMainDocArea(); }
            @Override public void onTapTopLeftMargin() { MuPDFReaderView.this.onTapTopLeftMargin(); }
            @Override public void onBottomRightMargin() { MuPDFReaderView.this.onBottomRightMargin(); }
            @Override public void addTextAnnotation(Annotation annot) { MuPDFReaderView.this.addTextAnnotion(annot); }
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

        longPressHandler.onUpOrCancel();

        tapRouter.handleSingleTap(e);
        return super.onSingleTapUp(e);
    }


    protected void addTextAnnotion(Annotation annot) {
        MuPDFView pageView = (MuPDFView)getSelectedView();
        ((MuPDFPageView)pageView).addTextAnnotation(annot);
    }
    
    @Override
    public boolean onDown(MotionEvent e) {
        longPressHandler.onDown(e, mUseStylus);
        return super.onDown(e);
    }

    private final SelectionGestureHandler selectionGestureHandler;
    
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {
        longPressHandler.cancelIfMoved(e1);
        
        switch (mMode) {
            case Viewing:
            case Searching:
                if (!tapDisabled) onDocMotion();
                return super.onScroll(e1, e2, distanceX, distanceY);
            case Selecting:
                if (selectionGestureHandler.onScroll(e1, e2)) return true;
                return super.onScroll(e1, e2, distanceX, distanceY);
            default:
                return true;
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                           float velocityY) {

        longPressHandler.onUpOrCancel();

        if(maySwitchView()) 
            return super.onFling(e1, e2, velocityX, velocityY);
        else
            return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector d) {
        if(stylusHelper.shouldBlockScale(mUseStylus))
            return false;

        longPressHandler.onUpOrCancel();
        
        tapDisabled = true;
        return super.onScaleBegin(d);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final MuPDFPageView pageView = (MuPDFPageView)getSelectedView();
        if (pageView == null) super.onTouchEvent(event);

        switch(event.getAction()) {
            case MotionEvent.ACTION_UP:
                selectionGestureHandler.reset();
                
                longPressHandler.onUpOrCancel();
                break;
        }
        drawingGestureHandler.handle(event, mUseStylus);
                
        if ((event.getAction() & event.getActionMasked()) == MotionEvent.ACTION_DOWN)
        {
            tapDisabled = false;
        }

        return super.onTouchEvent(event);
    }

    public void addSearchResult(SearchResult result) { searchNavigator.add(result); }
    public void clearSearchResults() { searchNavigator.clear(); }
    public boolean hasSearchResults() { return searchNavigator.hasAny(); }
    public void goToNextSearchResult(int direction) { searchNavigator.goToNext(direction); }
    
    
    @Override
    protected void onChildSetup(int i, View v) {
        searchNavigator.applyToView(i, (MuPDFView) v);
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
            setMode(Mode.valueOf(bundle.getString("mMode", mMode.toString())));
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

    private class DrawingHost implements DrawingGestureHandler.Host {
        @Override public MuPDFPageView pageView() { return (MuPDFPageView) getSelectedView(); }
        @Override public Mode mode() { return mMode; }
        @Override public void setMode(Mode mode) { mMode = mode; }
        @Override public void onStrokesChanged(int strokes) { MuPDFReaderView.this.onNumberOfStrokesChanged(strokes); }
        @Override public void deselectAnnotation() {
            MuPDFPageView cv = (MuPDFPageView) getSelectedView();
            if (cv != null) cv.deselectAnnotation();
        }
    }

    private final SearchResultNavigator.Host searchHost = new SearchResultNavigator.Host() {
        @Override public int currentPage() { return getSelectedItemPosition(); }
        @Override public void setDisplayedViewIndex(int page) { MuPDFReaderView.this.setDisplayedViewIndex(page); }
        @Override public void doNextScrollWithCenter() { MuPDFReaderView.this.doNextScrollWithCenter(); }
        @Override public void setDocRelXScroll(float docRelXScroll) { MuPDFReaderView.this.setDocRelXScroll(docRelXScroll); }
        @Override public void setDocRelYScroll(float docRelYScroll) { MuPDFReaderView.this.setDocRelYScroll(docRelYScroll); }
        @Override public void resetupChildren() { MuPDFReaderView.this.resetupChildren(); }
    };
}
