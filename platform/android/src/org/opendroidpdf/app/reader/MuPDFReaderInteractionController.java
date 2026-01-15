package org.opendroidpdf.app.reader;

import android.app.Activity;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import kotlinx.coroutines.CoroutineScope;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.Hit;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.SearchResult;
import org.opendroidpdf.SearchResultsController;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.annotation.AnnotationModeStore;
import org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController;
import org.opendroidpdf.app.fillsign.FillSignController;
import org.opendroidpdf.app.reader.gesture.ReaderGestureController;
import org.opendroidpdf.app.reader.gesture.ReaderMode;
import org.opendroidpdf.app.fillsign.FillSignAction;

/**
 * Owns MuPDFReaderView interaction state (mode/links/search/gestures) so the view itself can
 * focus on paging/child management.
 */
public final class MuPDFReaderInteractionController {

    public interface Host {
        // --- paging/navigation hooks used by SearchResultsController ---
        int currentPage();
        void setDisplayedViewIndex(int page);
        void doNextScrollWithCenter();
        void setDocRelXScroll(float docRelXScroll);
        void setDocRelYScroll(float docRelYScroll);
        void resetupChildren();

        // --- view hooks used by ReaderGestureController ---
        @Nullable MuPDFPageView currentPageView();
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

        // --- super delegates (ReaderGestureController gates scroll/fling/scale) ---
        boolean superOnDown(MotionEvent e);
        boolean superOnScroll(MotionEvent e1, MotionEvent e2, float dx, float dy);
        boolean superOnFling(MotionEvent e1, MotionEvent e2, float vx, float vy);
        boolean superOnScaleBegin(ScaleGestureDetector d);
        boolean superOnTouchEvent(MotionEvent event);
        boolean superOnSingleTapUp(MotionEvent e);

        // --- mode transition surface (keeps DocViewFactory override semantics) ---
        void setMode(ReaderMode mode);
    }

    private final Host host;
    private final CoroutineScope gestureScope;
    private final ReaderGestureController gestureController;
    private final SearchResultsController searchResults;
    private boolean linksEnabled = true;
    private ReaderMode mode = ReaderMode.VIEWING;
    private int tapPageMargin;

    @Nullable
    private AnnotationModeStore annotationModeStore;

    private final FillSignController fillSignController;

    @Nullable private FormFieldNavigator formFieldNavigator;
    @Nullable private Object formFieldNavigatorKey;

    public MuPDFReaderInteractionController(@NonNull Activity activity, @NonNull Host host) {
        this.host = host;

        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        tapPageMargin = (int) dm.xdpi;
        if (tapPageMargin < 100) tapPageMargin = 100;
        if (tapPageMargin > dm.widthPixels / 5) tapPageMargin = dm.widthPixels / 5;
        if (tapPageMargin > dm.heightPixels / 5) tapPageMargin = dm.heightPixels / 5;

        this.searchResults = new SearchResultsController(new SearchResultsController.Host() {
            @Override public int currentPage() { return host.currentPage(); }
            @Override public void setDisplayedViewIndex(int page) { host.setDisplayedViewIndex(page); }
            @Override public void doNextScrollWithCenter() { host.doNextScrollWithCenter(); }
            @Override public void setDocRelXScroll(float docRelXScroll) { host.setDocRelXScroll(docRelXScroll); }
            @Override public void setDocRelYScroll(float docRelYScroll) { host.setDocRelYScroll(docRelYScroll); }
            @Override public void resetupChildren() { host.resetupChildren(); }
        });

        this.gestureScope = AppCoroutines.newMainScope();
        this.gestureController = new ReaderGestureController(activity, gestureScope, new ReaderGestureController.Host() {
            @Override public ReaderMode mode() { return MuPDFReaderInteractionController.this.mode(); }

            @Override public void requestMode(ReaderMode mode) { MuPDFReaderInteractionController.this.requestMode(mode); }

            @Override public MuPDFPageView currentPageView() { return host.currentPageView(); }
            @Override public boolean linksEnabled() { return MuPDFReaderInteractionController.this.linksEnabled(); }
            @Override public int tapPageMargin() { return MuPDFReaderInteractionController.this.tapPageMargin(); }
            @Override public void onDocMotion() { host.onDocMotion(); }
            @Override public void onHit(Hit item) { host.onHit(item); }
            @Override public void onTapMainDocArea() { host.onTapMainDocArea(); }
            @Override public void onTapTopLeftMargin() { host.onTapTopLeftMargin(); }
            @Override public void onBottomRightMargin() { host.onBottomRightMargin(); }
            @Override public void addTextAnnotation(Annotation annot) { host.addTextAnnotation(annot); }
            @Override public void onNumberOfStrokesChanged(int strokes) { host.onNumberOfStrokesChanged(strokes); }
            @Override public boolean maySwitchView() { return host.maySwitchView(); }
            @Override public boolean useStylus() { return host.useStylus(); }
            @Override public View rootView() { return host.rootView(); }

            @Override public boolean superOnDown(MotionEvent e) { return host.superOnDown(e); }
            @Override public boolean superOnScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) { return host.superOnScroll(e1, e2, dx, dy); }
            @Override public boolean superOnFling(MotionEvent e1, MotionEvent e2, float vx, float vy) { return host.superOnFling(e1, e2, vx, vy); }
            @Override public boolean superOnScaleBegin(ScaleGestureDetector d) { return host.superOnScaleBegin(d); }
            @Override public boolean superOnTouchEvent(MotionEvent event) { return host.superOnTouchEvent(event); }
        });

        this.fillSignController = new FillSignController(activity, new FillSignController.PageViewProvider() {
            @Override public MuPDFPageView currentPageView() { return host.currentPageView(); }
        });
    }

    public void setAnnotationModeStore(@Nullable AnnotationModeStore store) {
        annotationModeStore = store;
    }

    public boolean linksEnabled() { return linksEnabled; }

    public void setLinksEnabled(boolean enabled) {
        linksEnabled = enabled;
        host.resetupChildren();
    }

    public void setTextAnnotationMultiSelectController(@Nullable TextAnnotationMultiSelectController controller) {
        try { gestureController.setTextAnnotationMultiSelectController(controller); } catch (Throwable ignore) {}
    }

    public int tapPageMargin() { return tapPageMargin; }

    public @NonNull ReaderMode mode() { return mode != null ? mode : ReaderMode.VIEWING; }

    public void setMode(@NonNull ReaderMode mode) {
        this.mode = mode != null ? mode : ReaderMode.VIEWING;
    }

    public void requestMode(@Nullable ReaderMode desiredMode) {
        if (desiredMode == null) return;
        AnnotationModeStore store = annotationModeStore;
        if (store != null) {
            switch (desiredMode) {
                case DRAWING:
                    store.enterDrawingMode();
                    return;
                case ERASING:
                    store.enterErasingMode();
                    return;
                case ADDING_TEXT_ANNOT:
                    store.enterAddingTextMode();
                    return;
                case VIEWING:
                    store.enterViewingMode();
                    return;
                default:
                    break;
            }
        }
        host.setMode(desiredMode);
    }

    /**
     * Entry point for Fill & Sign actions invoked from the toolbar/menus.
     *
     * <p>Implementation is owned by the gesture/controller layer so placement can safely
     * intercept scroll/fling while active.</p>
     */
    public void requestFillSignAction(@NonNull FillSignAction action) {
        fillSignController.requestAction(action);
    }

    public void detach() {
        AppCoroutines.cancelScope(gestureScope);
    }

    // --- Gesture delegation ---
    public boolean onSingleTapUp(MotionEvent e) {
        gestureController.onSingleTapUp(e);
        return host.superOnSingleTapUp(e);
    }

    public boolean onDown(MotionEvent e) { return gestureController.onDown(e); }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return gestureController.onScroll(e1, e2, distanceX, distanceY);
    }

    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return gestureController.onFling(e1, e2, velocityX, velocityY);
    }

    public boolean onScaleBegin(ScaleGestureDetector d) {
        if (fillSignController != null && fillSignController.isActive()) return false;
        return gestureController.onScaleBegin(d);
    }

    public boolean onTouchEvent(MotionEvent event) {
        try {
            if (fillSignController != null && fillSignController.onTouchEvent(event)) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        return gestureController.onTouchEvent(event);
    }

    // --- Search results ---
    public void addSearchResult(@NonNull SearchResult result) { searchResults.addResult(result); }
    public void clearSearchResults() { searchResults.clear(); }
    public boolean hasSearchResults() { return searchResults.hasResults(); }
    public void goToNextSearchResult(int direction) { searchResults.goToNext(direction); }
    public void applySearchResultsToView(int pageIndex, @NonNull MuPDFView view) { searchResults.applyToView(pageIndex, view); }

    // --- Form field navigation (AcroForm widgets) ---
    public boolean navigateFormField(int direction) {
        FormFieldNavigator navigator = formFieldNavigatorOrNull();
        return navigator != null && navigator.navigate(direction);
    }

    public boolean navigateFormFieldFromLocation(int pageIndex, float docRelX, float docRelY, int direction) {
        FormFieldNavigator navigator = formFieldNavigatorOrNull();
        return navigator != null && navigator.navigateFromLocation(pageIndex, docRelX, docRelY, direction);
    }

    public void resetFormFieldNavigator() {
        if (formFieldNavigator != null) formFieldNavigator.reset();
        formFieldNavigator = null;
        formFieldNavigatorKey = null;
    }

    @Nullable
    private FormFieldNavigator formFieldNavigatorOrNull() {
        MuPDFPageView pageView = host.currentPageView();
        if (pageView == null) return null;

        Object key = pageView.formFieldNavigationKey();
        if (formFieldNavigator != null && formFieldNavigatorKey == key) return formFieldNavigator;

        formFieldNavigatorKey = key;
        formFieldNavigator = new FormFieldNavigator(
                new FormFieldNavigator.Host() {
                    @Override public int currentPage() { return host.currentPage(); }
                    @Override public void setDisplayedViewIndex(int page) { host.setDisplayedViewIndex(page); }
                    @Override public void doNextScrollWithCenter() { host.doNextScrollWithCenter(); }
                    @Override public void setDocRelXScroll(float docRelXScroll) { host.setDocRelXScroll(docRelXScroll); }
                    @Override public void setDocRelYScroll(float docRelYScroll) { host.setDocRelYScroll(docRelYScroll); }
                    @Override public void resetupChildren() { host.resetupChildren(); }
                },
                new FormFieldNavigator.WidgetProvider() {
                    @Override public int pageCount() { return pageView.documentPageCountForNavigation(); }

                    @Override public RectF[] widgetAreas(int pageIndex) {
                        RectF[] areas = pageView.widgetAreasForNavigation(pageIndex);
                        return areas != null ? areas : new RectF[0];
                    }
                });
        return formFieldNavigator;
    }
}
