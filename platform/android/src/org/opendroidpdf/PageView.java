package org.opendroidpdf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ArrayDeque;
import android.util.TypedValue;
import java.lang.Thread;

import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectStreamException;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.TextSelector;
import org.opendroidpdf.app.preferences.EditorPreferences;

import kotlinx.coroutines.CoroutineScope;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.helpers.BusyIndicatorHelper;
import org.opendroidpdf.app.helpers.BusyIndicatorAdapter;
import org.opendroidpdf.DrawingController;
import org.opendroidpdf.core.DocumentAnnotationCallback;
import org.opendroidpdf.core.DocumentContentController;
import org.opendroidpdf.core.DocumentContentController.DocumentJob;
import org.opendroidpdf.core.DocumentLinkCallback;
import org.opendroidpdf.core.DocumentTextCallback;
import org.opendroidpdf.app.content.PageContentController;
import org.opendroidpdf.app.reader.PageState;
import org.opendroidpdf.app.overlay.PageSelectionState;
import org.opendroidpdf.app.overlay.SelectionTextHelper;
import org.opendroidpdf.app.overlay.PageMeasureHelper;
import org.opendroidpdf.app.content.PagePrefHost;

// TextProcessor and TextSelector moved to top-level classes in org.opendroidpdf.

public abstract class PageView extends ViewGroup implements MuPDFView {
    private static final int BACKGROUND_COLOR = 0xFFFFFFFF;
    private static final int PROGRESS_DIALOG_DELAY = 200;
    
    protected final Context mContext;
    protected ViewGroup mParent;
    
    protected     int       mPageNumber;
    private       Point     mSize;   // Size of page at minimum zoom
    private       float     mSourceScale;
    // moved into PageState: docRelXmin/docRelXmax
    private       boolean   mIsBlank;
    private       boolean   mHighlightLinks;

    // Removed legacy text annotation scratch bitmap (no longer used)
    
    private       org.opendroidpdf.app.overlay.PagePatchView mEntireView; // Page rendered at minimum zoom
    private       Bitmap    mEntireBm;
    private       Matrix    mEntireMat;    

    private       org.opendroidpdf.app.overlay.PagePatchView mHqView;
    
    private       TextWord  mText[][];
    private final DocumentContentController documentContentController;
    // moved to PageContentController
    protected     LinkInfo  mLinks[];
    protected     Annotation mAnnotations[];
    private final PageContentController pageContentController;
    private final PageState pageState = new PageState();
    private final Runnable overlayInvalidator;
    private final PagePrefHost prefHost;
    private final PageSelectionState selectionState;

    private       org.opendroidpdf.app.overlay.PageOverlayView mOverlayView;
    private       SearchResult mSearchResult = null;
    private       boolean   mForceFullRedrawOnNextAnnotationLoad;
    
    private final DrawingController drawingController;
    private final CoroutineScope uiScope = AppCoroutines.newMainScope();

    private final org.opendroidpdf.app.helpers.BusyIndicatorAdapter busyIndicator = new org.opendroidpdf.app.helpers.BusyIndicatorAdapter();
    
    // Preferences are read on demand via EditorPreferences; avoid static caches here.
    private final EditorPreferences editorPrefs;

    protected abstract CancellableTaskDefinition<PatchInfo,PatchInfo> getRenderTask(PatchInfo patchInfo);
    protected abstract LinkInfo[] getLinkInfo();
    protected abstract TextWord[][] getText();
    protected abstract Annotation[] getAnnotations();
    protected abstract void addMarkup(PointF[] quadPoints, Annotation.Type type);
    protected abstract void addTextAnnotation(Annotation annot);

    // Host adapter for PagePatchView to delegate back into PageView
    private final org.opendroidpdf.app.overlay.PagePatchView.Host patchHost =
            new org.opendroidpdf.app.overlay.PagePatchView.Host() {
                @Override
                public void removeBusyIndicator() {
                    busyIndicator.cancelAndRemove(PageView.this);
                }

                @Override
                public CancellableTaskDefinition<org.opendroidpdf.PatchInfo, org.opendroidpdf.PatchInfo> getRenderTask(org.opendroidpdf.PatchInfo patchInfo) {
                    return PageView.this.getRenderTask(patchInfo);
                }
            };
    
    // Host for the independent PageOverlayView
    private final org.opendroidpdf.app.overlay.PageOverlayView.Host overlayHost =
            new org.opendroidpdf.app.overlay.PageOverlayHostAdapter(new OverlayHost(), pageState);



    /* package */ boolean hitsLeftMarker(float x, float y) { return selectionState.hitsLeftMarker(x, y); }
    /* package */ boolean hitsRightMarker(float x, float y) { return selectionState.hitsRightMarker(x, y); }
    /* package */ void moveLeftMarker(MotionEvent e){ selectionState.moveLeftMarker(e.getX(), e.getY()); }
    /* package */ void moveRightMarker(MotionEvent e){ selectionState.moveRightMarker(e.getX(), e.getY()); }
    
    
    /* removed: inlined overlay moved to org.opendroidpdf.app.overlay.PageOverlayView */

    // Expose overlay view for host adapters
    public View getOverlayView() { return mOverlayView; }
    public void invalidateOverlay() { if (mOverlayView != null) mOverlayView.invalidate(); }

    
    public PageView(Context c, ViewGroup parent, DocumentContentController contentController) {
        super(c);
        mContext = c;
        mParent = parent;
        mEntireMat = new Matrix();
        documentContentController = contentController;
        pageContentController = new PageContentController(documentContentController);
        overlayInvalidator = new Runnable() {
            @Override public void run() { PageView.this.invalidateOverlay(); }
        };
        drawingController = new DrawingController(new org.opendroidpdf.app.overlay.DrawingHostAdapter(this));
        prefHost = new PagePrefHost(pageState, overlayInvalidator);
        selectionState = new PageSelectionState(this, pageState, overlayInvalidator);
        editorPrefs = new EditorPreferences(c);
        org.opendroidpdf.app.content.PagePreferenceUpdater.apply(editorPrefs, prefHost, pageState);
    }

    @Override
    public boolean isOpaque() {
        return true;
    }
    
    private void reset() {
        pageContentController.cancelAll();

            //Reset the child views
        if(mEntireView != null) mEntireView.reset();
        if(mHqView != null) mHqView.reset();
        if(mOverlayView != null)
        {
            removeView(mOverlayView);
            mOverlayView = null;
        }
        busyIndicator.cancelAndRemove(this);
        
        mIsBlank = true;
        mPageNumber = 0;        
        mSize = null;
                    
        mSearchResult = null;
        mLinks = null;
        mText = null;
        selectionState.deselect();
        selectionState.setItemSelectBox(null);
    }

    public void releaseResources() {        
        reset();
        
        busyIndicator.cancelAndRemove(this);
        
        drawingController.clear();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        busyIndicator.cancelAndRemove(this);
        AppCoroutines.cancelScope(uiScope);
    }

    public void releaseBitmaps() {
        reset();
        mEntireBm = null;
    }

    public void setPage(int page, PointF size) {
        reset();
        mPageNumber = page;
        mIsBlank = false;
        
            // Calculate scaled size that fits within the parent
            // This is the size at minimum zoom
        if (mParent == null) {
            android.view.ViewParent p = getParent();
            if (p instanceof ViewGroup) {
                mParent = (ViewGroup) p;
            }
        }

        float fallbackWidth = getResources().getDisplayMetrics().widthPixels;
        float fallbackHeight = getResources().getDisplayMetrics().heightPixels;

        float parentWidth  = (mParent != null && mParent.getWidth()  > 0) ? mParent.getWidth()  : fallbackWidth;
        float parentHeight = (mParent != null && mParent.getHeight() > 0) ? mParent.getHeight() : fallbackHeight;
        if (parentWidth <= 0) parentWidth = size.x;
        if (parentHeight <= 0) parentHeight = size.y;
        mSourceScale = Math.min(parentWidth/size.x, parentHeight/size.y);
        mSize = new Point((int)(size.x*mSourceScale), (int)(size.y*mSourceScale));
        org.opendroidpdf.app.content.PageStateUpdater.set(pageState, mPageNumber, mSize, mSourceScale);

            //Set the background to white for now and
            //prepare and show the busy indicator
        setBackgroundColor(BACKGROUND_COLOR);
        busyIndicator.attachIfNeeded(this, mContext, PROGRESS_DIALOG_DELAY);

            //Create the mEntireView
        addEntire(false);

            // Get the link info and text in the background
        loadLinkInfo();
        loadText();
        
            //Create the mOverlayView if not present
        if (mOverlayView == null) {
            mOverlayView = new org.opendroidpdf.app.overlay.PageOverlayView(mContext, overlayHost, drawingController, editorPrefs);

                //Fit the overlay view to the PageView
            int overlayW = (int) ((mParent != null && mParent.getWidth() > 0) ? mParent.getWidth() : parentWidth);
            int overlayH = (int) ((mParent != null && mParent.getHeight() > 0) ? mParent.getHeight() : parentHeight);
            mOverlayView.measure(MeasureSpec.makeMeasureSpec(overlayW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(overlayH, MeasureSpec.AT_MOST));
            addView(mOverlayView);
        }
        mOverlayView.invalidate();
        
        requestLayout();
    }

    
    public void setLinkHighlighting(boolean f) {
        mHighlightLinks = f;
        if (mOverlayView != null)
            mOverlayView.invalidate();
    }
    
    
    public void deselectText() {
        selectionState.deselect();
    }

    
    public boolean hasSelection() {
        return selectionState.hasSelection();
    }

    private final class OverlayHost implements org.opendroidpdf.app.overlay.PageOverlayHostAdapter.Host {
        @Override public boolean isBlank() { return mIsBlank; }
        @Override public float scale() { return PageView.this.getScale(); }
        @Override public boolean isLinkHighlightingEnabled() { return mHighlightLinks; }
        @Override public LinkInfo[] links() { return mLinks; }
        @Override public SearchResult searchResult() { return mSearchResult; }
        @Override public TextWord[][] text() { return mText; }
        @Override public RectF selectBox() { return selectionState.getSelectBox(); }
        @Override public RectF itemSelectBox() { return selectionState.getItemSelectBox(); }
        @Override public int viewWidth() { return PageView.this.getWidth(); }
        @Override public int viewHeight() { return PageView.this.getHeight(); }
        @Override public int viewLeft() { return PageView.this.getLeft(); }
        @Override public int viewTop() { return PageView.this.getTop(); }
        @Override public RectF leftMarkerRect() { return selectionState.getLeftMarkerRect(); }
        @Override public RectF rightMarkerRect() { return selectionState.getRightMarkerRect(); }
    }

    // Selection accessors used by adapters/controllers
    public RectF getSelectBox() { return selectionState.getSelectBox(); }
    public void setSelectBox(RectF box) { selectionState.setSelectBox(box); }

    public boolean hasTextSelected() {
        return SelectionTextHelper.hasTextSelected(
                mText,
                selectionState.getSelectBox(),
                pageState,
                editorPrefs.isSmartTextSelectionEnabled());
    }
    
    public void selectText(float x0, float y0, float x1, float y1) {
        selectionState.selectFromViewRect(x0, y0, x1, y1);
        loadText(); // We should do this earlier in the background ...
    }
    private void loadText() {
        pageContentController.loadText(contentHost);
    }

    private void loadLinkInfo() {
        pageContentController.loadLinks(contentHost);
    }

    protected void requestFullRedrawAfterNextAnnotationLoad() {
        mForceFullRedrawOnNextAnnotationLoad = true;
    }

    protected void loadAnnotations() {
        mAnnotations = null;
        pageContentController.loadAnnotations(contentHost);
    }

    // Host adapter for PageContentController
    private final org.opendroidpdf.app.content.PageContentController.Host contentHost = new PageContentHostImpl();

    private final class PageContentHostImpl implements org.opendroidpdf.app.content.PageContentController.Host {
        @Override public int getPageNumber() { return mPageNumber; }
        @Override public void setText(TextWord[][] text) { mText = text; }
        @Override public void setLinks(LinkInfo[] links) { mLinks = links; }
        @Override public void setAnnotations(Annotation[] annotations) { mAnnotations = annotations; }
        @Override public void invalidateOverlay() { PageView.this.invalidateOverlay(); }
        @Override public boolean consumeForceFullRedrawFlag() {
            boolean v = mForceFullRedrawOnNextAnnotationLoad;
            mForceFullRedrawOnNextAnnotationLoad = false;
            return v;
        }
        @Override public void requestRedraw(boolean update) { redraw(update); }
        @Override public void setSelectBox(RectF box) { PageView.this.setSelectBox(box); }
        @Override public RectF getSelectBox() { return selectionState.getSelectBox(); }
    }

    
    public void startDraw(final float x, final float y) {
        drawingController.startDraw(x, y, editorPrefs.getInkThickness());
        // In-progress drawing creates undo state; update toolbar cache.
        org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
    }

    public void continueDraw(final float x, final float y) {
        drawingController.continueDraw(x, y, editorPrefs.getInkThickness());
    }
    
    public void finishDraw() {
	    drawingController.finishDraw(editorPrefs.getInkThickness());
        org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
    }

    public void startErase(final float x, final float y) {
        drawingController.startErase(x, y, editorPrefs.getEraserThickness());
    }
    
    public void continueErase(final float x, final float y) {
        drawingController.continueErase(x, y, editorPrefs.getEraserThickness());
    }

    public void finishErase(final float x, final float y) {
        drawingController.finishErase(x, y, editorPrefs.getEraserThickness());
    }

    public void undoDraw() {
        drawingController.undoDraw();
        org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
    }
    
    public boolean canUndo() {
        return drawingController.canUndo();
    }
    
    
    public void cancelDraw() {
        drawingController.cancelDraw();
        org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
    }
    
    public int getDrawingSize() {
        return drawingController.getDrawingSize();
    }

    public void setDraw(PointF[][] arcs) {
        drawingController.setDraw(arcs);
    }
    
    protected PointF[][] getDraw() {
        return drawingController.getDraw();
    }

    protected void processSelectedText(TextProcessor tp) {
        SelectionTextHelper.processSelectedText(
                mText,
                selectionState.getSelectBox(),
                pageState,
                editorPrefs.isSmartTextSelectionEnabled(),
                tp);
    }

    /* package */ void setItemSelectBox(RectF rect) {
        selectionState.setItemSelectBox(rect);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Point measured = PageMeasureHelper.measure(pageState, mParent, mOverlayView, busyIndicator, this, widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(measured.x, measured.y);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int w  = right - left;
        final int h = bottom - top;

        // Layout or discard the hi‑res patch using the orchestrator helper
        org.opendroidpdf.app.overlay.PageRenderOrchestrator.layoutOrDiscardHq(mHqView, w, h);

        // Delegate remaining child layout and busy indicator placement
        org.opendroidpdf.app.overlay.PageLayoutController.layoutAll(
                mEntireView,
                mHqView,
                mOverlayView,
                mEntireMat,
                left,
                top,
                right,
                bottom,
                pageState.getMinZoomSize(),
                busyIndicator.getHandle(),
                changed
        );
    }


    /* package */ void addEntire(boolean update) {
        Point s = pageState.getMinZoomSize();
        if (s == null) return;
        Rect viewArea = new Rect(0, 0, s.x, s.y);
        if (mEntireBm == null || s.x != mEntireBm.getWidth() || s.y != mEntireBm.getHeight()) {
            mEntireBm = Bitmap.createBitmap(s.x, s.y, Config.ARGB_8888);
        }
        mEntireView = org.opendroidpdf.app.overlay.PageRenderOrchestrator.ensureAndRender(
                mContext,
                this,
                mEntireView,
                viewArea,
                mEntireBm,
                update,
                patchHost,
                mOverlayView);
    }
    
    
    public void addHq(boolean update) { // If update is true, still redraw even if area hasn't changed
        Rect viewArea = new Rect(getLeft(), getTop(), getRight(), getBottom());
        Point s2 = pageState.getMinZoomSize();
        if (viewArea == null || s2 == null) return;
        if (viewArea.width() == s2.x && viewArea.height() == s2.y) return; // no HQ needed at min zoom

        mHqView = org.opendroidpdf.app.overlay.PageRenderOrchestrator.ensureAndRender(
                mContext,
                this,
                mHqView,
                viewArea,
                ((ReaderView) mParent).getPatchBm(update),
                update,
                patchHost,
                mOverlayView);
    }

    public void removeHq() {
        if (mHqView != null) mHqView.reset();
    }

    public void redraw(boolean update) {
        addEntire(update);
        addHq(update);
        mOverlayView.invalidate();
    }

    @Override
    public float getScale() {
        Point s = pageState.getMinZoomSize();
        float base = pageState.getSourceScale();
        if (s == null || s.x == 0) return 1f;
        return base * (float) getWidth() / (float) s.x;
    }

    public void setSearchResult(SearchResult searchTaskResult) {
        mSearchResult = searchTaskResult;
    }
    
    public static void onSharedPreferenceChanged(SharedPreferences sharedPref, String key, Context context) {
        // No-op: PageView reads preferences on demand via EditorPreferences.
        // Retained for compatibility with PreferenceApplier.
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("superInstanceState", super.onSaveInstanceState());
        org.opendroidpdf.app.annotation.DrawingStateSerializer.putInto(
                bundle,
                drawingController.getDrawing(),
                drawingController.getHistory());
        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            org.opendroidpdf.app.annotation.DrawingStateSerializer.Restored restored =
                    org.opendroidpdf.app.annotation.DrawingStateSerializer.restoreFrom(bundle);
            drawingController.restore(restored.drawing, restored.history);
            state = bundle.getParcelable("superInstanceState");
        }
        super.onRestoreInstanceState(state);
    }

    /* package */ Bitmap getHqImageBitmap() {
        if(mHqView == null) return null;
        return mHqView.getImageBitmap();
    }

    protected void discardRenderedPage() {
        if (mEntireView != null) {
            mEntireView.reset();
        }
        if (mHqView != null) {
            mHqView.reset();
        }
        mEntireBm = null;
    }

    public boolean saveDraw() {
        if (mOverlayView != null) {
            // Prefer drawing into the hi‑res patch if present; otherwise fall back to the
            // full‑page view so that accepting a stroke never makes it disappear visually
            // while the annotation is being committed asynchronously.
            org.opendroidpdf.app.overlay.PagePatchView targetView = mHqView != null ? mHqView : mEntireView;
            org.opendroidpdf.app.overlay.SaveDrawHelper.drawOntoPatch(
                    targetView,
                    this,
                    getScale(),
                    new org.opendroidpdf.app.overlay.SaveDrawHelper.Drawer() {
                        @Override public void draw(Canvas canvas, float scale) {
                            mOverlayView.drawDrawing(canvas, scale);
                        }
                    }
            );
        }
        return true;
    }
}
