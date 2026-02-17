package org.opendroidpdf;

import java.lang.Math;

import java.util.LinkedList;
import java.util.NoSuchElementException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Build;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.Scroller;
import android.widget.Toast;
import android.widget.ImageView;

import android.util.Log;

import org.opendroidpdf.app.preferences.ViewerPrefsSnapshot;
import org.opendroidpdf.app.reader.FlingMomentum;
import org.opendroidpdf.app.reader.PagingAxis;
import org.opendroidpdf.app.reader.ScrollMode;

abstract public class ReaderView extends AdapterView<Adapter> implements GestureDetector.OnGestureListener, ScaleGestureDetector.OnScaleGestureListener, Runnable
{
    // Removed unused legacy movement constants (routing handled by ReaderMotion/GestureRouter)

    private static final int  FLING_MARGIN      = 100;
    private static final int  GAP_PAGED_PX      = 20;
    private static final float GAP_CONTINUOUS_DP = 4f;
    private static final float CONTINUOUS_PAGE_ELEVATION_DP = 2f;
    // In continuous mode, we want flings to travel across many pages (Acrobat-style momentum).
    // The legacy fling bounds were based on the currently-attached child stack (<= 3 pages),
    // which capped flings to ~1-2 page transitions. Use a very large Y bound so the Scroller
    // keeps producing deltas while LayoutSwitchHelper advances the current page.
    private static final int CONTINUOUS_FLING_Y_RANGE_PX = 10_000_000;

    static final float MIN_SCALE        = 1.0f;
    static final float MAX_SCALE        = 10.0f;
    static final float REFLOW_SCALE_FACTOR = 0.5f;

        // Set via applyViewerPrefs()
    protected boolean mUseStylus = false;
    protected boolean mFitWidth = false;
    protected ScrollMode mScrollMode = ScrollMode.CONTINUOUS;
    protected PagingAxis mPagingAxis = PagingAxis.VERTICAL;
    protected boolean mNightMode = false;
    protected FlingMomentum mFlingMomentum = FlingMomentum.NORMAL;
    private float mContinuousFlingVelocityMultiplier = 1.0f;

    private final int mGapContinuousPx;
    private final float mContinuousPageElevationPx;

    private final org.opendroidpdf.app.reader.HqBitmapPool hqBitmapPool = new org.opendroidpdf.app.reader.HqBitmapPool();

    private Adapter           mAdapter;
    private int               mCurrent = INVALID_POSITION;    // Adapter's index for the current view
    private final org.opendroidpdf.app.reader.AdapterState adapterState = new org.opendroidpdf.app.reader.AdapterState();
    // moved into ScrollState: nextScrollWithCenter flag
    private final SparseArray<View> mChildViews = new SparseArray<View>(3); // Shadows the children of the AdapterView but with more sensible indexing
    private final LinkedList<View> mViewCache = new LinkedList<View>();
    boolean           mUserInteracting;  // Whether the user is interacting
    private boolean mUnsettledForTouch;
    // Whether the user is actively scrubbing pages via the page switcher SeekBar(s).
    // Used to temporarily prefer faster raster renders while dragging.
    private volatile boolean mScrubbing = false;
    private boolean           mScaling;    // Whether the user is currently pinch zooming
    float             mScale     = 1.0f; //mScale = 1.0 corresponds to "fit to screen"
    // Pending normalized/doc-relative scroll/scale are tracked in ScrollState

        // Scroll amounts recorded from events and then accounted for in onLayout.
    // scroll moved into ScrollState
    // scroller last positions moved into ScrollState
    final org.opendroidpdf.app.reader.ScrollState scrollState = new org.opendroidpdf.app.reader.ScrollState();

    // Read aloud highlight state (best-effort; driven by host/controller).
    private volatile int readAloudHighlightPage = -1;
    @androidx.annotation.Nullable private volatile RectF[] readAloudHighlightBoxes = null;

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
                @Override public void postSelf() { ReaderView.this.postOnAnimation(ReaderView.this); }
                @Override public void postSettle(View v) { ReaderView.this.postSettle(v); }
                @Override public void postUnsettle(View v) { ReaderView.this.postUnsettle(v); }
                @Override public Point subScreenSizeOffset(View v) { return layoutEngine.subScreenSizeOffset(v); }
                @Override public org.opendroidpdf.app.reader.ScrollState scrollState() { return scrollState; }
                @Override public android.widget.Scroller scroller() { return mScroller; }
                @Override public int width() { return getWidth(); }
                @Override public int height() { return getHeight(); }
                @Override public int paddingLeft() { return getPaddingLeft(); }
                @Override public int paddingRight() { return getPaddingRight(); }
                @Override public int paddingTop() { return getPaddingTop(); }
                @Override public int paddingBottom() { return getPaddingBottom(); }
                @Override public boolean isUserInteracting() { return mUserInteracting; }
                @Override public int gap() { return gapPx(); }
            };

    private final org.opendroidpdf.app.reader.LayoutSwitchHelper.LayoutHost layoutHost =
            new org.opendroidpdf.app.reader.LayoutSwitchHelper.LayoutHost() {
                @Override public int paddingLeft() { return getPaddingLeft(); }
                @Override public int paddingRight() { return getPaddingRight(); }
                @Override public int paddingTop() { return getPaddingTop(); }
                @Override public int paddingBottom() { return getPaddingBottom(); }
                @Override public int width() { return getWidth(); }
                @Override public int height() { return getHeight(); }
                @Override public org.opendroidpdf.app.reader.ScrollMode scrollMode() { return mScrollMode; }
                @Override public org.opendroidpdf.app.reader.ScrollState scrollState() { return scrollState; }
                @Override public boolean isUserInteracting() { return mUserInteracting; }
                @Override public android.widget.Scroller scroller() { return mScroller; }
                @Override public void postSettle(View v) { ReaderView.this.postSettle(v); }
                @Override public Point subScreenSizeOffset(View v) { return layoutEngine.subScreenSizeOffset(v); }
                @Override public View getOrCreateChild(int index) { return ReaderView.this.getOrCreateChild(index, getWidth(), getHeight()); }
                @Override public Adapter adapter() { return mAdapter; }
                @Override public int gap() { return gapPx(); }
                @Override public void measureChild(View v) {
                    org.opendroidpdf.app.reader.ReaderMeasure.measureChild(
                            v,
                            getWidth(), getHeight(),
                            getPaddingLeft(), getPaddingRight(), getPaddingTop(), getPaddingBottom(),
                            mReflow,
                            mScale);
                }
            };

    /** True while the user is dragging a page scrubber (SeekBar) to switch pages rapidly. */
    public boolean isScrubbing() { return mScrubbing; }

    /** Current reader scroll mode (continuous vs single-page/paged). */
    public ScrollMode getScrollMode() { return mScrollMode; }

    /** Mark whether a page scrubber (SeekBar) is actively being dragged by the user. */
    public void setScrubbing(boolean scrubbing) {
        if (mScrubbing == scrubbing) return;
        mScrubbing = scrubbing;
        // Scrubbing mode changes how we lay out neighbors (we skip them while scrubbing) and how
        // pages render (low-res preview vs HQ). Ensure we re-layout when the mode flips.
        requestLayout();
    }
    
    boolean           mReflow = false;
    final org.opendroidpdf.app.reader.GestureRouter gestureRouter;
    final Scroller    mScroller;
    private boolean           mScrollDisabled;

    private final ReaderViewLayoutEngine layoutEngine;
    private final ReaderViewGestureController gestureController;

    Parcelable displayedViewInstanceState = null; //Set by MuPDFReaderView in onRestoreInstanceState()
    
    static abstract class ViewMapper {
        abstract void applyToView(View view);
    }

    public ReaderView(Context context) {
        super(context);
        setBackgroundResource(R.color.window_background);
        mGapContinuousPx = Math.max(2, Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                GAP_CONTINUOUS_DP,
                getResources().getDisplayMetrics())));
        mContinuousPageElevationPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                CONTINUOUS_PAGE_ELEVATION_DP,
                getResources().getDisplayMetrics());
        layoutEngine = new ReaderViewLayoutEngine(this);
        gestureRouter = new org.opendroidpdf.app.reader.GestureRouter(
                context,
                this,
                this,
                new org.opendroidpdf.app.reader.gesture.ReaderViewGestureHost(new org.opendroidpdf.app.reader.gesture.ReaderViewGestureHost.ViewBridge() {
                    @Override public boolean isScaling() { return isScalingForHost(); }
                    @Override public void setScaling(boolean scaling) { setScalingForHost(scaling); }

                    @Override public PagingAxis pagingAxis() { return ReaderView.this.mPagingAxis; }

	                    @Override public boolean isScrollDisabled() { return isScrollDisabledForHost(); }
	                    @Override public void setScrollDisabled(boolean disabled) { setScrollDisabledForHost(disabled); }
	                    @Override public View getSelectedView() { return ReaderView.this.getSelectedView(); }
	                    @Override public int getSelectedItemPosition() { return ReaderView.this.getSelectedItemPosition(); }
	                    @Override public View getViewAt(int index) { return ReaderView.this.getView(index); }
	                    @Override public Rect getScrollBoundsForView(View v) { return ReaderView.this.getScrollBoundsForView(v); }
	                    @Override public int getFlingMargin() { return getFlingMarginConst(); }
	                    @Override public void slideViewOntoScreen(View v) { slideViewOntoScreenBridge(v); }
	                    @Override public void flingWithinBounds(int velocityX, int velocityY, Rect bounds) { flingWithinBoundsBridge(velocityX, velocityY, bounds); }

                    @Override public void addScroll(float dx, float dy) { addScrollFromHost(dx, dy); }
                    @Override public void requestLayout() { ReaderView.this.requestLayout(); }
                    @Override public void setScroll(int x, int y) { setScrollFromHost(x, y); }

                    @Override public float getScale() { return getScaleForHost(); }
                    @Override public void setScale(float scale) { ReaderView.this.setScale(scale); }
                    @Override public boolean isReflow() { return isReflowForHost(); }
                    @Override public boolean isFitWidth() { return isFitWidthForHost(); }
                    @Override public float getMinScale() { return getMinScaleForHost(); }
                    @Override public float getMaxScale() { return getMaxScaleForHost(); }
                    @Override public int getPrevFocusX() { return getPrevFocusXForHost(); }
                    @Override public int getPrevFocusY() { return getPrevFocusYForHost(); }
                    @Override public void setPrevFocus(int x, int y) { setPrevFocusForHost(x, y); }
                    @Override public void applyScaleToAllChildren() { applyScaleToAllChildrenFromHost(); }
                    @Override public void stopScroller() { stopScrollerFromHost(); }
                    @Override public int getContainerWidth() { return ReaderView.this.getWidth(); }
                    @Override public int getContainerHeight() { return ReaderView.this.getHeight(); }
                    @Override public int getPadLeft() { return getPaddingLeft(); }
                    @Override public int getPadRight() { return getPaddingRight(); }
                    @Override public int getPadTop() { return getPaddingTop(); }
                    @Override public int getPadBottom() { return getPaddingBottom(); }
                }));
        mScroller        = new Scroller(context);
        mScroller.forceFinished(true); //Otherwise mScroller.isFinished() is not true which prevents the generation of the Hq area
        gestureController = new ReaderViewGestureController(this);
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

    private void unsettleAllChildren() {
        for (int pos = 0; pos < childCount(); pos++) {
            View v = childViewAt(pos);
            if (v == null) continue;
            try { onUnsettle(v); } catch (Throwable ignore) {}
        }
    }

    public void setReadAloudHighlight(int pageIndex, @androidx.annotation.Nullable RectF[] boxes) {
        readAloudHighlightPage = pageIndex;
        readAloudHighlightBoxes = boxes;
        // Small (<=3) page stack; invalidate overlays so the highlight follows.
        applyToChildren(new ViewMapper() {
            @Override void applyToView(View view) {
                if (!(view instanceof PageView)) return;
                try { ((PageView) view).invalidateOverlay(); } catch (Throwable ignore) {}
            }
        });
    }

    public void clearReadAloudHighlight() {
        setReadAloudHighlight(-1, null);
    }

    @androidx.annotation.Nullable
    public RectF[] getReadAloudHighlightBoxesForPage(int pageIndex) {
        if (readAloudHighlightPage != pageIndex) return null;
        return readAloudHighlightBoxes;
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
    private void startScrollAndPost(int dx, int dy, int durationMs) {
        mScroller.startScroll(0, 0, dx, dy, durationMs);
        postOnAnimation(this);
    }

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
                @Override public void postSelf() { postOnAnimation(ReaderView.this); }
            };
    // Host bridges for GestureRouter (package-private)
    boolean isScalingForHost() { return mScaling; }
    void setScalingForHost(boolean s) { mScaling = s; }
    boolean isScrollDisabledForHost() { return mScrollDisabled; }
    void setScrollDisabledForHost(boolean d) { mScrollDisabled = d; }
    android.graphics.Rect getScrollBoundsForView(View v) { return layoutEngine.computeScrollBounds(v); }
    int getFlingMarginConst() { return FLING_MARGIN; }
    void slideViewOntoScreenBridge(View v) {
        android.graphics.Rect bounds = layoutEngine.computeScrollBounds(v);
        android.graphics.Point corr = org.opendroidpdf.app.reader.ReaderGeometry.correction(bounds);
        if (corr.x != 0 || corr.y != 0) {
            unsettleAllChildren();
            if (org.opendroidpdf.BuildConfig.DEBUG) {
                android.util.Log.d("ReaderView", "slideViewOntoScreen corr=(" + corr.x + "," + corr.y + ")"
                        + " bounds=" + bounds
                        + " view=(" + v.getLeft() + "," + v.getTop() + " " + v.getMeasuredWidth() + "x" + v.getMeasuredHeight() + ")"
                        + " container=" + getWidth() + "x" + getHeight());
            }
            scrollState.setScrollerLast(0, 0);
            startScrollAndPost(corr.x, corr.y, 400);
        }
    }
    void flingWithinBoundsBridge(int velocityX, int velocityY, android.graphics.Rect bounds) {
        unsettleAllChildren();
        scrollState.setScrollerLast(0, 0);
        if (mScrollMode == ScrollMode.CONTINUOUS) {
            velocityX = scaleFlingVelocity(velocityX, mContinuousFlingVelocityMultiplier);
            velocityY = scaleFlingVelocity(velocityY, mContinuousFlingVelocityMultiplier);
        }
        if (mScrollMode == ScrollMode.CONTINUOUS) {
            mScroller.fling(
                    0, 0,
                    velocityX, velocityY,
                    bounds.left, bounds.right,
                    -CONTINUOUS_FLING_Y_RANGE_PX, CONTINUOUS_FLING_Y_RANGE_PX);
        } else {
            mScroller.fling(0, 0, velocityX, velocityY, bounds.left, bounds.right, bounds.top, bounds.bottom);
        }
        postOnAnimation(this);
    }

    private int scaleFlingVelocity(int velocity, float multiplier) {
        if (multiplier == 1.0f) return velocity;
        long scaled = Math.round((double) velocity * (double) multiplier);
        if (scaled > Integer.MAX_VALUE) scaled = Integer.MAX_VALUE;
        if (scaled < Integer.MIN_VALUE) scaled = Integer.MIN_VALUE;
        int out = (int) scaled;
        try {
            int max = android.view.ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity();
            if (out > max) return max;
            if (out < -max) return -max;
        } catch (Throwable ignore) {
        }
        return out;
    }
    void addScrollFromHost(float dx, float dy) {
        if ((dx != 0f || dy != 0f) && !mUnsettledForTouch) {
            unsettleAllChildren();
            mUnsettledForTouch = true;
        }
        scrollState.addScroll(dx, dy);
        clampPendingScrollToBoundsIfNeeded();
    }

    /**
     * Programmatically scrolls the reader by a raw pixel delta.
     *
     * <p>Used by gesture flows that intentionally consume the user's pan gesture but still need to
     * auto-scroll the document (e.g., cross-page drag-move of annotations).</p>
     */
    public void addScrollForOverlayDrag(float dxPx, float dyPx) {
        addScrollFromHost(dxPx, dyPx);
        requestLayout();
    }
    void setScrollFromHost(int x, int y) {
        if ((x != 0 || y != 0) && !mUnsettledForTouch) {
            unsettleAllChildren();
            mUnsettledForTouch = true;
        }
        scrollState.setScroll(x, y);
        clampPendingScrollToBoundsIfNeeded();
    }
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

    private void clampPendingScrollToBoundsIfNeeded() {
        View selected = getSelectedView();
        if (selected == null) return;

        boolean clampX = false;
        boolean clampY = false;

        if (mScrollMode == ScrollMode.CONTINUOUS) {
            // In continuous mode (Acrobat-style), never allow panning beyond the stacked page bounds.
            // This prevents “pushing” the document into empty background both when fit-to-screen and
            // when zoomed in.
            clampX = true;
            clampY = true;
        } else {
            // In paged mode, allow swipes on the paging axis to move between pages when fit-to-screen.
            // When zoomed in, treat swipes as in-page panning and clamp to the page edges.
            boolean zoomedIn = false;
            try { zoomedIn = getNormalizedScale() > 1.001f; } catch (Throwable ignore) {}
            if (zoomedIn) {
                clampX = true;
                clampY = true;
            } else if (mPagingAxis == PagingAxis.VERTICAL) {
                clampX = true;
            } else {
                clampY = true;
            }
        }

        if (!clampX && !clampY) return;

        Rect bounds = layoutEngine.computeScrollBounds(selected);
        Point corr = org.opendroidpdf.app.reader.ReaderGeometry.correction(bounds);
        if (corr.x == 0 && corr.y == 0) return;

        int x = scrollState.getX();
        int y = scrollState.getY();
        if (clampX) x += corr.x;
        if (clampY) y += corr.y;
        scrollState.setScroll(x, y);
    }

    public void run() {
        gestureController.run();
    }
    
    @Override
        public boolean onDown(MotionEvent arg0) {
        mScroller.forceFinished(true);
        mUnsettledForTouch = false;
        return true;
    }

    @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                               float velocityY) {
        return gestureRouter.onFling(e1, e2, velocityX, velocityY);
    }
    
    @Override
        public void onLongPress(MotionEvent e) {
    }

    @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return gestureRouter.onScroll(e1, e2, distanceX, distanceY);
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
        return gestureRouter.onScaleBegin(detector);
    }

    @Override 
        public boolean onScale(ScaleGestureDetector detector) {
        gestureRouter.onScaleUsing(detector, scrollState.getX(), scrollState.getY());
        return true;
    }

    @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        gestureRouter.onScaleEnd(detector);
    }

    /**
     * Debug helper to run the same snap-to-fit-width logic used at the end of pinch-zoom.
     * No-op in release builds.
     */
    public void debugTriggerSnapToFitWidthIfEligible() {
        if (!org.opendroidpdf.BuildConfig.DEBUG) return;
        snapToFitWidthIfEligible();
    }

    /**
     * Debug helper to run snap-to-fit-width while temporarily assuming fit-width is enabled.
     * This avoids mutating SharedPreferences from debug actions.
     * No-op in release builds.
     */
    public void debugTriggerSnapToFitWidthAssumingFitWidthEnabled() {
        if (!org.opendroidpdf.BuildConfig.DEBUG) return;
        boolean previousFitWidth = mFitWidth;
        mFitWidth = true;
        try {
            snapToFitWidthIfEligible();
        } finally {
            mFitWidth = previousFitWidth;
        }
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
        return gestureController.onTouchEvent(event);
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
        
        View cv = org.opendroidpdf.app.reader.LayoutSwitchHelper.handleSwitches(layoutSwitchHost, mPagingAxis);
        
            // Remove not needed children and hold them for reuse
        removeSuperflousChildren();
        
            //Caculate placement of the current view
        int cvLeft, cvRight, cvTop, cvBottom;
        {
            cv = getOrCreateChild(mCurrent, right-left, bottom-top);
            
                //Set mXScroll, mYScroll and mScale from the values set in setScale() and setScroll()
            layoutEngine.applyPendingScrollAndScale(cv);
            
            // Note: Scroll deltas are applied by LayoutSwitchHelper.layoutCurrentAndNeighbors, which also
            // resets scrollState. Keep scrollState intact here so panning/zoom scroll corrections apply.
        }
        onScaleChild(cv, mScale);

        org.opendroidpdf.app.reader.LayoutSwitchHelper.LayoutResult lr =
                org.opendroidpdf.app.reader.LayoutSwitchHelper.layoutCurrentAndNeighbors(
                        layoutHost,
                        cv,
                        mCurrent,
                        mPagingAxis,
                        !mScrubbing);
        cvLeft = lr.left; cvTop = lr.top; cvRight = lr.right; cvBottom = lr.bottom;

        if (mScrollMode == ScrollMode.CONTINUOUS && !mUserInteracting && mScroller.isFinished() && !mScrubbing) {
            // In continuous mode, multiple pages can be visible at rest. Ensure visible neighbors
            // also render HQ patches (otherwise the "next" page can look blurry when partially on-screen).
            postSettleIfVisible(lr.previousView);
            postSettleIfVisible(lr.nextView);
        }
    }

    private void postSettleIfVisible(View v) {
        if (v == null) return;
        if (!intersectsViewport(v)) return;
        postSettle(v);
    }

    private boolean intersectsViewport(View v) {
        if (v == null) return false;
        int visLeft = getPaddingLeft();
        int visTop = getPaddingTop();
        int visRight = getWidth() - getPaddingRight();
        int visBottom = getHeight() - getPaddingBottom();
        return v.getRight() > visLeft
                && v.getLeft() < visRight
                && v.getBottom() > visTop
                && v.getTop() < visBottom;
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
        applyScrollModeDecorToChild(v);
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
    

    void postSettle(final View v) {
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

    // Motion helpers moved to org.opendroidpdf.app.reader.ReaderMotion
        
    public float getNormalizedScale() 
    {
        return layoutEngine.getNormalizedScale();
    }
        
    public float getNormalizedXScroll()
    {
        return layoutEngine.getNormalizedXScroll();
    }

    public float getNormalizedYScroll()
    {
        return layoutEngine.getNormalizedYScroll();
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

    public void applyViewerPrefs(ViewerPrefsSnapshot prefs) {
        if (prefs == null) return;
        mUseStylus = prefs.useStylus;
        mFitWidth = prefs.fitWidth;
        mScrollMode = prefs.scrollMode != null ? prefs.scrollMode : ScrollMode.CONTINUOUS;
        // Paged mode respects the paging axis preference. Continuous mode is always stacked vertically.
        PagingAxis axis = prefs.pagingAxis != null ? prefs.pagingAxis : PagingAxis.VERTICAL;
        mPagingAxis = (mScrollMode == ScrollMode.CONTINUOUS) ? PagingAxis.VERTICAL : axis;
        mFlingMomentum = prefs.flingMomentum != null ? prefs.flingMomentum : FlingMomentum.NORMAL;
        mContinuousFlingVelocityMultiplier = mFlingMomentum.velocityMultiplier;
        boolean nightMode = prefs.nightMode;
        boolean nightChanged = mNightMode != nightMode;
        mNightMode = nightMode;

        applyScrollModeDecorToChildren();
        if (nightChanged) {
            applyNightModeToSelf();
            applyNightModeToChildren();
        }
        requestLayout();
    }

    private int gapPx() {
        return (mScrollMode == ScrollMode.CONTINUOUS) ? mGapContinuousPx : GAP_PAGED_PX;
    }

    private void applyScrollModeDecorToChildren() {
        applyToChildren(new ViewMapper() {
            @Override
            void applyToView(View view) {
                applyScrollModeDecorToChild(view);
            }
        });
    }

    private void applyScrollModeDecorToChild(View view) {
        if (view == null) return;
        if (view instanceof PageView) {
            ((PageView) view).applyFrameDecorFromParent();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        view.setElevation(mScrollMode == ScrollMode.CONTINUOUS ? mContinuousPageElevationPx : 0f);
    }

    private void applyNightModeToSelf() {
        setBackgroundResource(mNightMode ? R.color.window_background_night : R.color.window_background);
    }

    private void applyNightModeToChildren() {
        applyToChildren(new ViewMapper() {
            @Override
            void applyToView(View view) {
                if (view instanceof PageView) {
                    ((PageView) view).setNightModeEnabled(mNightMode);
                }
            }
        });
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
