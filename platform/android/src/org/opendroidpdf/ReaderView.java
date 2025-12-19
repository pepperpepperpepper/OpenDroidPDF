package org.opendroidpdf;

import java.lang.Math;

import java.util.LinkedList;
import java.util.NoSuchElementException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.Scroller;
import android.widget.Toast;
import android.widget.ImageView;
import android.content.SharedPreferences;

import android.util.Log;

abstract public class ReaderView extends AdapterView<Adapter> implements GestureDetector.OnGestureListener, ScaleGestureDetector.OnScaleGestureListener, Runnable
{
    // Removed unused legacy movement constants (routing handled by ReaderMotion/GestureRouter)

    private static final int  FLING_MARGIN      = 100;
    private static final int  GAP               = 20;

    private static final float MIN_SCALE        = 1.0f;
    private static final float MAX_SCALE        = 10.0f;
    private static final float REFLOW_SCALE_FACTOR = 0.5f;

        // Set in onSharedPreferenceChanged()
    protected boolean mUseStylus = false;
    protected boolean mFitWidth = false;

    private final org.opendroidpdf.app.reader.HqBitmapPool hqBitmapPool = new org.opendroidpdf.app.reader.HqBitmapPool();

    private Adapter           mAdapter;
    private int               mCurrent = INVALID_POSITION;    // Adapter's index for the current view
    private final org.opendroidpdf.app.reader.AdapterState adapterState = new org.opendroidpdf.app.reader.AdapterState();
    // moved into ScrollState: nextScrollWithCenter flag
    private final SparseArray<View> mChildViews = new SparseArray<View>(3); // Shadows the children of the AdapterView but with more sensible indexing
    private final LinkedList<View> mViewCache = new LinkedList<View>();
    private boolean           mUserInteracting;  // Whether the user is interacting
    private boolean           mScaling;    // Whether the user is currently pinch zooming
    private float             mScale     = 1.0f; //mScale = 1.0 corresponds to "fit to screen"
    // Pending normalized/doc-relative scroll/scale are tracked in ScrollState

        // Scroll amounts recorded from events and then accounted for in onLayout.
    // scroll moved into ScrollState
    // scroller last positions moved into ScrollState
    private final org.opendroidpdf.app.reader.ScrollState scrollState = new org.opendroidpdf.app.reader.ScrollState();
    private final org.opendroidpdf.app.reader.ChildReuseHelper.Host childReuseHost = new org.opendroidpdf.app.reader.ChildReuseHelper.Host() {
        @Override public Adapter adapter() { return mAdapter; }
        @Override public View childAtIndex(int index) { return mChildViews.get(index); }
        @Override public int childKeyAt(int position) { return mChildViews.keyAt(position); }
        @Override public int childCount() { return mChildViews.size(); }
        @Override public void removeViewInLayout(View v) { ReaderView.this.removeViewInLayout(v); }
        @Override public void removeChildKey(int key) { mChildViews.remove(key); }
        @Override public void appendChild(int key, View view) { mChildViews.append(key, view); }
        @Override public java.util.LinkedList<View> viewCache() { return mViewCache; }
        @Override public void onChildSetup(int index, View v) { ReaderView.this.onChildSetup(index, v); }
        @Override public void onScaleChild(View v, float scale) { ReaderView.this.onScaleChild(v, scale); }
        @Override public int currentIndex() { return mCurrent; }
        @Override public View getCached() { return ReaderView.this.getCached(); }
        @Override public void addViewInLayout(View v) {
            LayoutParams params = v.getLayoutParams();
            if (params == null) params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            ReaderView.this.addViewInLayout(v, 0, params, true);
        }
        @Override public float scale() { return mScale; }
        @Override public void onRestoreIfNeeded(int index, View v) {
            if(index == mCurrent && displayedViewInstanceState != null){
                ((PageView)v).onRestoreInstanceState(displayedViewInstanceState);
                displayedViewInstanceState = null;
                onNumberOfStrokesChanged(((PageView)v).getDrawingSize());
            }
        }
    };

    private final org.opendroidpdf.app.reader.LayoutSwitchHelper.Host layoutSwitchHost =
            new org.opendroidpdf.app.reader.LayoutSwitchHelper.Host() {
                @Override public Adapter adapter() { return mAdapter; }
                @Override public org.opendroidpdf.app.reader.AdapterState adapterState() { return adapterState; }
                @Override public int currentIndex() { return mCurrent; }
                @Override public void setCurrentIndex(int idx) { mCurrent = idx; }
                @Override public View currentView() { return getSelectedView(); }
                @Override public View getOrCreateChild(int index) { return ReaderView.this.getOrCreateChild(index, getWidth(), getHeight()); }
                @Override public void onMoveOffChild(int index) { ReaderView.this.onMoveOffChild(index); }
                @Override public void onMoveToChild(int index) { ReaderView.this.onMoveToChild(index); }
                @Override public void onUnsettle(View v) { ReaderView.this.onUnsettle(v); }
                @Override public void postSelf() { ReaderView.this.post(ReaderView.this); }
                @Override public void postSettle(View v) { ReaderView.this.postSettle(v); }
                @Override public void postUnsettle(View v) { ReaderView.this.postUnsettle(v); }
                @Override public Point subScreenSizeOffset(View v) { return ReaderView.this.subScreenSizeOffset(v); }
                @Override public org.opendroidpdf.app.reader.ScrollState scrollState() { return scrollState; }
                @Override public android.widget.Scroller scroller() { return mScroller; }
                @Override public int width() { return getWidth(); }
                @Override public int height() { return getHeight(); }
                @Override public int paddingLeft() { return getPaddingLeft(); }
                @Override public int paddingRight() { return getPaddingRight(); }
                @Override public int paddingTop() { return getPaddingTop(); }
                @Override public int paddingBottom() { return getPaddingBottom(); }
                @Override public boolean isUserInteracting() { return mUserInteracting; }
                @Override public int gap() { return GAP; }
            };

    private final org.opendroidpdf.app.reader.LayoutSwitchHelper.LayoutHost layoutHost =
            new org.opendroidpdf.app.reader.LayoutSwitchHelper.LayoutHost() {
                @Override public int paddingLeft() { return getPaddingLeft(); }
                @Override public int paddingRight() { return getPaddingRight(); }
                @Override public int paddingTop() { return getPaddingTop(); }
                @Override public int paddingBottom() { return getPaddingBottom(); }
                @Override public int width() { return getWidth(); }
                @Override public int height() { return getHeight(); }
                @Override public org.opendroidpdf.app.reader.ScrollState scrollState() { return scrollState; }
                @Override public boolean isUserInteracting() { return mUserInteracting; }
                @Override public android.widget.Scroller scroller() { return mScroller; }
                @Override public void postSettle(View v) { ReaderView.this.postSettle(v); }
                @Override public Point subScreenSizeOffset(View v) { return ReaderView.this.subScreenSizeOffset(v); }
                @Override public View getOrCreateChild(int index) { return ReaderView.this.getOrCreateChild(index, getWidth(), getHeight()); }
                @Override public Adapter adapter() { return mAdapter; }
                @Override public int gap() { return GAP; }
                @Override public void measureChild(View v) {
                    org.opendroidpdf.app.reader.ReaderMeasure.measureChild(
                            v,
                            getWidth(), getHeight(),
                            getPaddingLeft(), getPaddingRight(), getPaddingTop(), getPaddingBottom(),
                            mReflow,
                            mScale);
                }
            };
    
    private boolean           mReflow = false;
    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleGestureDetector;
    private org.opendroidpdf.app.reader.GestureRouter gestureRouter;
    private final Scroller    mScroller;
    private boolean           mScrollDisabled;

    Parcelable displayedViewInstanceState = null; //Set by MuPDFReaderView in onRestoreInstanceState()
    
    static abstract class ViewMapper {
        abstract void applyToView(View view);
    }

    public ReaderView(Context context) {
        super(context);
        mGestureDetector = new GestureDetector(this);
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
        gestureRouter = new org.opendroidpdf.app.reader.GestureRouter(
                context,
                this,
                this,
                new ReaderGestureHost(this));
        mScroller        = new Scroller(context);
        mScroller.forceFinished(true); //Otherwise mScroller.isFinished() is not true which prevents the generation of the Hq area
    }

    @Override
        public int getSelectedItemPosition() {
        return mCurrent;
    }

    public void setDisplayedViewIndex(int i) {
        setDisplayedViewIndex(i, true);
    }
    
    public void setDisplayedViewIndex(int i, boolean countsAsNewCurrent) {
        if (0 <= i && i < mAdapter.getCount()) {
            adapterState.requestSetDisplayedIndex(i, countsAsNewCurrent);
            requestLayout();
        }
    }

    public void moveToNext() {
        View v = mChildViews.get(mCurrent+1);
        if (v != null)
            slideViewOntoScreenBridge(v);
    }

    public void moveToPrevious() {
        View v = mChildViews.get(mCurrent-1);
        if (v != null)
            slideViewOntoScreenBridge(v);
    }

	// When advancing down the page, we want to advance by about
	// 90% of a screenful. But we'd be happy to advance by between
	// 80% and 95% if it means we hit the bottom in a whole number
	// of steps.
    // Removed: redundant smartAdvanceAmount; ColumnPager hosts internal logic.

    
    public void smartMoveForwards() {
        org.opendroidpdf.app.reader.SmartMoveHelper.moveForwards(smartMoveHost);
    }

    public void smartMoveBackwards() {
        org.opendroidpdf.app.reader.SmartMoveHelper.moveBackwards(smartMoveHost);
    }

    public void resetupChildren() {
        for (int pos = 0; pos < childCount(); pos++) {
            onChildSetup(childKeyAt(pos), childViewAt(pos));
        }
    }

    public void applyToChildren(ViewMapper mapper) {
        for (int pos = 0; pos < childCount(); pos++) {
            mapper.applyToView(childViewAt(pos));
        }
    }

        //To be overwritten in MuPDFReaderView
    abstract protected void onChildSetup(int i, View v);
    abstract protected void onMoveToChild(int pageNumber);
    abstract protected void onMoveOffChild(int i);
    abstract protected void onSettle(View v);
    abstract protected void onUnsettle(View v);
    abstract protected void onScaleChild(View v, Float scale);
    abstract protected void onNumberOfStrokesChanged(int numberOfStrokes);
    
    public View getView(int i) {
        return mChildViews.get(i); //Can return null while waiting for onLayout()!
    }

    // ChildViews access helpers to encapsulate SparseArray usage
    private int childCount() { return mChildViews.size(); }
    private int childKeyAt(int position) { return mChildViews.keyAt(position); }
    private View childViewAt(int position) { return mChildViews.valueAt(position); }

    // Screen geometry helpers (exclude padding)
    private int screenWidth() { return getWidth() - getPaddingLeft() - getPaddingRight(); }
    private int screenHeight() { return getHeight() - getPaddingTop() - getPaddingBottom(); }
    private int halfWidth() { return getWidth() / 2; }
    private int halfHeight() { return getHeight() / 2; }

    // Scroller helpers
    private int scrollerRemainingX() { return mScroller.getFinalX() - mScroller.getCurrX(); }
    private int scrollerRemainingY() { return mScroller.getFinalY() - mScroller.getCurrY(); }
    private void startScrollAndPost(int dx, int dy, int durationMs) { mScroller.startScroll(0, 0, dx, dy, durationMs); post(this); }

    private final org.opendroidpdf.app.reader.SmartMoveHelper.Host smartMoveHost =
            new org.opendroidpdf.app.reader.SmartMoveHelper.Host() {
                @Override public View currentView() { return getSelectedView(); }
                @Override public View viewAt(int index) { return getView(index); }
                @Override public int currentIndex() { return mCurrent; }
                @Override public int adapterCount() { return mAdapter != null ? mAdapter.getCount() : 0; }
                @Override public int screenWidth() { return ReaderView.this.screenWidth(); }
                @Override public int screenHeight() { return ReaderView.this.screenHeight(); }
                @Override public int paddingLeft() { return getPaddingLeft(); }
                @Override public int paddingTop() { return getPaddingTop(); }
                @Override public int scrollerRemainingX() { return ReaderView.this.scrollerRemainingX(); }
                @Override public int scrollerRemainingY() { return ReaderView.this.scrollerRemainingY(); }
                @Override public org.opendroidpdf.app.reader.ScrollState scrollState() { return scrollState; }
                @Override public android.widget.Scroller scroller() { return mScroller; }
                @Override public void postSelf() { post(ReaderView.this); }
            };
    // Host bridges for GestureRouter (package-private)
    boolean isScalingForHost() { return mScaling; }
    void setScalingForHost(boolean s) { mScaling = s; }
    boolean isScrollDisabledForHost() { return mScrollDisabled; }
    void setScrollDisabledForHost(boolean d) { mScrollDisabled = d; }
    android.graphics.Rect getScrollBoundsForView(View v) { return computeScrollBounds(v); }
    int getFlingMarginConst() { return FLING_MARGIN; }
    void slideViewOntoScreenBridge(View v) {
        android.graphics.Rect bounds = computeScrollBounds(v);
        android.graphics.Point corr = org.opendroidpdf.app.reader.ReaderGeometry.correction(bounds);
        if (corr.x != 0 || corr.y != 0) {
            scrollState.setScrollerLast(0, 0);
            startScrollAndPost(corr.x, corr.y, 400);
        }
    }
    void flingWithinBoundsBridge(int velocityX, int velocityY, android.graphics.Rect bounds) {
        mScroller.fling(0, 0, velocityX, velocityY, bounds.left, bounds.right, bounds.top, bounds.bottom);
        post(this);
    }
    void addScrollFromHost(float dx, float dy) { scrollState.addScroll(dx, dy); }
    void setScrollFromHost(int x, int y) { scrollState.setScroll(x, y); }
    float getScaleForHost() { return mScale; }
    boolean isReflowForHost() { return mReflow; }
    boolean isFitWidthForHost() { return mFitWidth; }
    float getMinScaleForHost() { float f = mReflow ? REFLOW_SCALE_FACTOR : 1.0f; return MIN_SCALE * f; }
    float getMaxScaleForHost() { float f = mReflow ? REFLOW_SCALE_FACTOR : 1.0f; return MAX_SCALE * f; }
    int getPrevFocusXForHost() { return scrollState.getPrevFocusX(); }
    int getPrevFocusYForHost() { return scrollState.getPrevFocusY(); }
    void setPrevFocusForHost(int x, int y) { scrollState.setPrevFocus(x, y); }
    void applyScaleToAllChildrenFromHost() {
        applyToChildren(new ViewMapper() { @Override void applyToView(View view) { onScaleChild(view, mScale); } });
    }
    void stopScrollerFromHost() { mScroller.forceFinished(true); }

    public void run() {
        if (!mScroller.isFinished()) {
            mScroller.computeScrollOffset();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            int curX = scrollState.getX();
            int curY = scrollState.getY();
            curX += x - scrollState.getScrollerLastX();
            curY += y - scrollState.getScrollerLastY();
            scrollState.setScroll(curX, curY);
            scrollState.setScrollerLast(x, y);
            requestLayout();
            if(!mScrollDisabled) post(this);
        }
        else if (!mUserInteracting) {
                // End of an inertial scroll and the user is not interacting.
                // The layout is stable
            View v = getSelectedView();
            if (v != null) postSettle(v);
        }
    }
    
    @Override
        public boolean onDown(MotionEvent arg0) {
        mScroller.forceFinished(true);
        return true;
    }

    @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                               float velocityY) {
        if (gestureRouter != null) {
            return gestureRouter.onFling(e1, e2, velocityX, velocityY);
        }
        // Fallback to default behavior if router missing (should not happen)
        return false;
    }
    
    @Override
        public void onLongPress(MotionEvent e) {
    }

    @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (gestureRouter != null) {
            return gestureRouter.onScroll(e1, e2, distanceX, distanceY);
        }
        // Fallback (should not be used)
        if (!mScrollDisabled) { scrollState.addScroll(-distanceX, -distanceY); requestLayout(); }
        return true;
    }

    @Override
        public void onShowPress(MotionEvent e) {
    }

    @Override
        public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }
    
    @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
        if (gestureRouter != null) return gestureRouter.onScaleBegin(detector);
        // Fallback
        mScaling = true; scrollState.resetScroll(); mScrollDisabled = true; scrollState.setPrevFocus((int)detector.getFocusX(), (int)detector.getFocusY()); return true;
    }

    @Override 
        public boolean onScale(ScaleGestureDetector detector) {
        if (gestureRouter != null) { gestureRouter.onScaleUsing(detector, scrollState.getX(), scrollState.getY()); return true; }
        // Fallback (legacy path)
        float previousScale = mScale; float scale_factor = mReflow ? REFLOW_SCALE_FACTOR : 1.0f; float min_scale = MIN_SCALE * scale_factor; float max_scale = MAX_SCALE * scale_factor; mScale = org.opendroidpdf.app.reader.ZoomController.clampScale(mScale, detector.getScaleFactor(), mReflow, min_scale, max_scale); View v = getSelectedView(); if (mReflow) { if (v != null) onScaleChild(v, mScale); } else if (v != null) { int[] out = org.opendroidpdf.app.reader.ZoomController.computeScrollForScale(v, previousScale, mScale, scrollState.getX(), scrollState.getY(), scrollState.getPrevFocusX(), scrollState.getPrevFocusY(), (int)detector.getFocusX(), (int)detector.getFocusY()); scrollState.setScroll(out[0], out[1]); scrollState.setPrevFocus(out[2], out[3]); requestLayout(); } return true;
    }

    @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        if (gestureRouter != null) { gestureRouter.onScaleEnd(detector); return; }
        // Fallback
        if (mReflow) { applyToChildren(new ViewMapper() { @Override void applyToView(View view) { onScaleChild(view, mScale);} }); }
        snapToFitWidthIfEligible();
        mScaling = false;
    }

    /**
     * Debug helper to run the same snap-to-fit-width logic used at the end of pinch-zoom.
     * No-op in release builds.
     */
    public void debugTriggerSnapToFitWidthIfEligible() {
        if (!org.opendroidpdf.BuildConfig.DEBUG) return;
        snapToFitWidthIfEligible();
    }

    private void snapToFitWidthIfEligible() {
        View cv = getSelectedView();
        float scaleFactor = mReflow ? REFLOW_SCALE_FACTOR : 1.0f;
        float minScale = MIN_SCALE * scaleFactor;
        float maxScale = MAX_SCALE * scaleFactor;
        Float snap = org.opendroidpdf.app.reader.SnapHelper.snapFitWidthIfEligible(
                mFitWidth, mReflow, mScale, minScale, maxScale, this, cv, mScroller, scrollState);
        if (snap != null) {
            mScale = snap;
            requestLayout();
        }
    }

    @Override
	public boolean onTouchEvent(MotionEvent event) {
        if (gestureRouter != null) {
            gestureRouter.onTouchEvent(event);
        } else {
            mScaleGestureDetector.onTouchEvent(event);
            if (!mScaling) mGestureDetector.onTouchEvent(event);
        }

        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            mUserInteracting = true;
        }
        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            mScrollDisabled = false;
            mUserInteracting = false;

            View v = getSelectedView();
            if (v != null) {
                if (mScroller.isFinished()) {
                        // If, at the end of user interaction, there is no
                        // current inertial scroll in operation then animate
                        // the view onto screen if necessary
                    slideViewOntoScreenBridge(v);
                }

                if (mScroller.isFinished()) {
                        // If still there is no inertial scroll in operation
                        // then the layout is stable
                    postSettle(v);
                }
            }
        }

        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int x, y;
        x = View.MeasureSpec.getSize(widthMeasureSpec);
        y = View.MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(x, y);
        
        int n = getChildCount();
        for (int i = 0; i < n; i++) {
            View child = getChildAt(i);
            org.opendroidpdf.app.reader.ReaderMeasure.measureChild(
                    child,
                    getWidth(), getHeight(),
                    getPaddingLeft(), getPaddingRight(), getPaddingTop(), getPaddingBottom(),
                    mReflow,
                    mScale);
        }
    }

    public Bitmap getPatchBm(boolean update) {
            //We must make sure that we return one of two
            //bitmaps in an alternating manner, so that the native code can draw to one
            //while the other is set to the Hq view
            //if update=true the situation changes, then the native code should
            //precisely draw to the bitmap currently shown
        Bitmap currentBitmap = ((PageView)getSelectedView()).getHqImageBitmap();
        return hqBitmapPool.next(currentBitmap, update, getWidth(), getHeight());
    }
    
    
    @Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        
        View cv = org.opendroidpdf.app.reader.LayoutSwitchHelper.handleSwitches(layoutSwitchHost);
        
            // Remove not needed children and hold them for reuse
        removeSuperflousChildren();
        
            //Caculate placement of the current view
        int cvLeft, cvRight, cvTop, cvBottom;
        {
            cv = getOrCreateChild(mCurrent, right-left, bottom-top);
            
                //Set mXScroll, mYScroll and mScale from the values set in setScale() and setScroll()
            if(!mReflow)
            {
                float scale_factor = mReflow ? REFLOW_SCALE_FACTOR : 1.0f;
                float min_scale = MIN_SCALE * scale_factor;
                float max_scale = MAX_SCALE * scale_factor;
                float scale = org.opendroidpdf.app.reader.ReaderGeometry.fillScreenScaleFromViews(this, cv);
                float scaleCorrection = org.opendroidpdf.app.reader.ReaderGeometry.scaleCorrectionFromViews(this, cv, scale);
                
                if (scrollState.consumeHasNewNormalizedScale())
                {
                    mScale = Math.min(Math.max(scrollState.getNewNormalizedScale()*scaleCorrection, min_scale), max_scale);
                }
                if (scrollState.consumeHasNewDocRelX())
                {
                    float normX = org.opendroidpdf.app.reader.NormalizedScroll.normalizedFromDocRelX(
                            scrollState.getNewDocRelX(), ((PageView)cv).getScale(), cv.getMeasuredWidth(), mScale, scale);
                    scrollState.requestNormalizedX(normX);
                }
                if (scrollState.consumeHasNewDocRelY())
                {
                    float normY = org.opendroidpdf.app.reader.NormalizedScroll.normalizedFromDocRelY(
                            scrollState.getNewDocRelY(), ((PageView)cv).getScale(), cv.getMeasuredHeight(), mScale, scale);
                    scrollState.requestNormalizedY(normY);
                }

                if (scrollState.hasNewNormalizedX() || scrollState.hasNewNormalizedY())
                {
                        //Preset to the current values
                    int XScroll = org.opendroidpdf.app.reader.NormalizedScroll.presetPixelsFromNormalized(
                            getNormalizedXScroll(), cv.getMeasuredWidth(), mScale, scale);
                    int YScroll = org.opendroidpdf.app.reader.NormalizedScroll.presetPixelsFromNormalized(
                            getNormalizedYScroll(), cv.getMeasuredHeight(), mScale, scale);
                    
                    if(scrollState.hasNewNormalizedX()){
                        XScroll = org.opendroidpdf.app.reader.NormalizedScroll.targetPixelsFromNormalized(
                                scrollState.getNewNormalizedX(), cv.getMeasuredWidth(), mScale, scale, getPaddingLeft());
                        scrollState.clearNewNormalizedX();
                    }
                    if(scrollState.hasNewNormalizedY()){
                        YScroll = org.opendroidpdf.app.reader.NormalizedScroll.targetPixelsFromNormalized(
                                scrollState.getNewNormalizedY(), cv.getMeasuredHeight(), mScale, scale, getPaddingTop());
                        scrollState.clearNewNormalizedY();
                    }

                    if(scrollState.consumeNextScrollWithCenter())
                    {
                        XScroll+=halfWidth();
                        YScroll+=halfHeight();
                    }

                    mScroller.forceFinished(true);
                    scrollState.setScrollerLast(0, 0);
                    scrollState.setScroll(XScroll - cv.getLeft(), YScroll - cv.getTop());
                }
            }
            
                //Set the positon of the top left corner
            cvLeft = cv.getLeft() + scrollState.getX();
            cvTop  = cv.getTop()  + scrollState.getY();
            
                //Reset scroll amounts
            scrollState.resetScroll();
        }
        //Calculate right and bottom after scaling the child
        onScaleChild(cv, mScale);
        cvRight  = cvLeft + cv.getMeasuredWidth();
        cvBottom = cvTop  + cv.getMeasuredHeight();

        org.opendroidpdf.app.reader.LayoutSwitchHelper.LayoutResult lr =
                org.opendroidpdf.app.reader.LayoutSwitchHelper.layoutCurrentAndNeighbors(layoutHost, cv, mCurrent);
        cvLeft = lr.left; cvTop = lr.top; cvRight = lr.right; cvBottom = lr.bottom;
    }

    
    private void removeAllChildren() {
        int numChildren = mChildViews.size();
        for (int i = 0; i < numChildren; i++) {
            View v = mChildViews.valueAt(i);
            ((MuPDFView) v).releaseResources();
            removeViewInLayout(v);
        }
        mChildViews.clear();
        mViewCache.clear();
    }
    
    
    private void removeSuperflousChildren() {
        org.opendroidpdf.app.reader.ChildReuseHelper.removeSuperfluous(childReuseHost);
    }
    
    @Override
	public Adapter getAdapter() {
        return mAdapter;
    }

    @Override
	public View getSelectedView() {
        return mChildViews.get(mCurrent); //Can return null while waiting for onLayout()!
    }

    @Override
    public void setAdapter(Adapter adapter) {
        mAdapter = adapter;
        removeAllChildren();
        removeAllViewsInLayout();
        // Ensure we start with a valid current index so the recycler does not
        // immediately evict the only visible page (which was causing blank renders).
        if (mAdapter != null && mAdapter.getCount() > 0) {
            mCurrent = 0;
        } else {
            mCurrent = INVALID_POSITION;
        }
        requestLayout();
    }

    @Override
	public void setSelection(int arg0) {
        throw new UnsupportedOperationException(getContext().getString(R.string.not_supported));
    }

    private View getCached() {
        if (mViewCache.size() == 0)
            return null;
        else
            return mViewCache.removeFirst();
    }


    private View getOrCreateChild(int i, int width, int height) {
        View v = org.opendroidpdf.app.reader.ChildReuseHelper.getOrCreateChild(childReuseHost, i);
        org.opendroidpdf.app.reader.ReaderMeasure.measureChild(
                v,
                getWidth(), getHeight(),
                getPaddingLeft(), getPaddingRight(), getPaddingTop(), getPaddingBottom(),
                mReflow,
                mScale);
        return v;
    }

    private void addAndMeasureChild(int i, View v) {
        LayoutParams params = v.getLayoutParams();
        if (params == null) {
            params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        org.opendroidpdf.app.reader.ReaderMeasure.measureChild(
                v,
                getWidth(), getHeight(),
                getPaddingLeft(), getPaddingRight(), getPaddingTop(), getPaddingBottom(),
                mReflow,
                mScale);
        addViewInLayout(v, 0, params, true);
        mChildViews.append(i, v); // Record the view against it's adapter index
    }
    

    private Rect computeScrollBounds(View v) {
        return org.opendroidpdf.app.reader.ReaderGeometry.scrollBounds(
                getWidth(), getHeight(),
                getPaddingLeft(), getPaddingRight(), getPaddingTop(), getPaddingBottom(),
                v.getLeft() + scrollState.getX() - getPaddingLeft(),
                v.getTop() + scrollState.getY() - getPaddingTop(),
                v.getLeft() + v.getMeasuredWidth() + scrollState.getX() + getPaddingRight(),
                v.getTop() + v.getMeasuredHeight() + scrollState.getY() + getPaddingBottom());
    }

    private void postSettle(final View v) {
            // onSettle and onUnsettle are posted so that the calls
            // wont be executed until after the system has performed
            // layout.
        post(new Runnable() {
                public void run () {
                    onSettle(v);
                }
            });
    }

    private void postUnsettle(final View v) {
        post (new Runnable() {
                public void run () {
                    onUnsettle(v);
                }
            });
    }

    // Removed: slideViewOntoScreen (logic moved into slideViewOntoScreenBridge)

    private Point subScreenSizeOffset(View v) {
        return org.opendroidpdf.app.reader.ReaderGeometry.subScreenSizeOffset(
                getWidth(), getHeight(), v.getMeasuredWidth(), v.getMeasuredHeight());
    }

    // Motion helpers moved to org.opendroidpdf.app.reader.ReaderMotion
        
    public float getNormalizedScale() 
    {
        View cv = getSelectedView();
        if (cv != null) {
            return org.opendroidpdf.app.reader.ReaderGeometry.normalizedScale(
                    mScale,
                    getWidth(), getHeight(),
                    getPaddingLeft(), getPaddingRight(), getPaddingTop(), getPaddingBottom(),
                    cv.getMeasuredWidth(), cv.getMeasuredHeight());
        }
        else
            return 1f;
    }
        
    public float getNormalizedXScroll()
    {
        View cv = getSelectedView();
        if (cv != null) {
            return org.opendroidpdf.app.reader.NormalizedScroll.normalizedX(cv.getLeft(), getPaddingLeft(), cv.getMeasuredWidth());
        }
        else return 0;
    }

    public float getNormalizedYScroll()
    {
        View cv = getSelectedView();
        if (cv != null) {
            return org.opendroidpdf.app.reader.NormalizedScroll.normalizedY(cv.getTop(), getPaddingTop(), cv.getMeasuredHeight());
        }
        else return 0;
    }

    public void setNormalizedScale(float normalizedScale)
    {
        scrollState.requestNormalizedScale(normalizedScale);
        requestLayout();
    }

    public void setScale(float scale)
    {
        mScale = scale;
        requestLayout();
    }            
        
    public void setNormalizedScroll(float normalizedXScroll, float normalizedYScroll) 
    {
        setNormalizedXScroll(normalizedXScroll);
        setNormalizedYScroll(normalizedYScroll);
    }

    public void setNormalizedXScroll(float normalizedXScroll)
    {
        scrollState.requestNormalizedX(normalizedXScroll);
        requestLayout();
    }

    public void setNormalizedYScroll(float normalizedYScroll)
    {
        scrollState.requestNormalizedY(normalizedYScroll);
        requestLayout();
    }

    public void setDocRelXScroll(float docRelXScroll)
    {
        scrollState.requestDocRelX(docRelXScroll);
        requestLayout();
    }

    public void setDocRelYScroll(float docRelYScroll)
    {
        scrollState.requestDocRelY(docRelYScroll);
        requestLayout();
    }

    public void doNextScrollWithCenter()
    {
        scrollState.requestNextScrollWithCenter();
    }    

    public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key){
        mUseStylus = sharedPref.getBoolean(SettingsActivity.PREF_USE_STYLUS, false);
        mFitWidth = sharedPref.getBoolean(SettingsActivity.PREF_FIT_WIDTH, false);
    }

        //This method can be overwritten in super classes to prevent view switching while, for example, we are in drawing mode
    public boolean maySwitchView() {
        return true;
    }


    @Override
    public Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(org.opendroidpdf.app.reader.ReaderStateBundle.SUPER, super.onSaveInstanceState());
        org.opendroidpdf.app.reader.ReaderStateBundle.save(bundle,
                mCurrent, scrollState.getX(), scrollState.getY(),
                scrollState.getScrollerLastX(), scrollState.getScrollerLastY(),
                scrollState.getPrevFocusX(), scrollState.getPrevFocusY(),
                mReflow, mScrollDisabled);
        return bundle;
    }
    
    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            org.opendroidpdf.app.reader.ReaderStateBundle.Values vals =
                    org.opendroidpdf.app.reader.ReaderStateBundle.restore(
                            bundle, mCurrent, scrollState.getX(), scrollState.getY(),
                            scrollState.getScrollerLastX(), scrollState.getScrollerLastY(),
                            scrollState.getPrevFocusX(), scrollState.getPrevFocusY(),
                            mReflow, mScrollDisabled);
            mCurrent = vals.current;
            scrollState.setScroll(vals.x, vals.y);
            scrollState.setScrollerLast(vals.scrollerLastX, vals.scrollerLastY);
            scrollState.setPrevFocus(vals.prevFocusX, vals.prevFocusY);
            mReflow = vals.reflow;
            mScrollDisabled = vals.scrollDisabled;

            state = bundle.getParcelable(org.opendroidpdf.app.reader.ReaderStateBundle.SUPER);
        }
        super.onRestoreInstanceState(state);
    }
}
