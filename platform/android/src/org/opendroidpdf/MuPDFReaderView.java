package org.opendroidpdf;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.app.reader.gesture.ReaderMode;
import org.opendroidpdf.app.reader.TextAnnotationRequester;
import org.opendroidpdf.app.reader.MuPDFReaderInteractionController;
import org.opendroidpdf.app.annotation.AnnotationModeStore;
import org.opendroidpdf.app.widget.WidgetUiBridge;
import org.opendroidpdf.app.fillsign.FillSignAction;

import android.widget.Adapter;

abstract public class MuPDFReaderView extends ReaderView {
    private final MuPDFReaderInteractionController interaction;
    private boolean formFieldHighlightEnabled = false;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        try {
            if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                View focused = findFocus();
                if (focused instanceof EditText && focused.getId() == R.id.dialog_text_input) {
                    int[] loc = new int[2];
                    focused.getLocationOnScreen(loc);
                    float rawX = event.getRawX();
                    float rawY = event.getRawY();
                    int left = loc[0];
                    int top = loc[1];
                    int right = left + focused.getWidth();
                    int bottom = top + focused.getHeight();
                    if (rawX < left || rawX > right || rawY < top || rawY > bottom) {
                        focused.clearFocus();
                        // Consume the tap that dismisses the inline editor so the underlying
                        // document doesn't also receive a click (which can race widget focus).
                        return true;
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return super.dispatchTouchEvent(event);
    }
    
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

    public void setLinksEnabled(boolean b) {
        interaction.setLinksEnabled(b);
    }

    public boolean linksEnabled() {
        return interaction.linksEnabled();
    }

    /** Whether AcroForm widget bounds are highlighted on-page. */
    public boolean isFormFieldHighlightEnabled() {
        return formFieldHighlightEnabled;
    }

    /** Enables/disables highlighting of AcroForm widget bounds on-page. */
    public void setFormFieldHighlightEnabled(boolean enabled) {
        if (formFieldHighlightEnabled == enabled) return;
        formFieldHighlightEnabled = enabled;
        applyToChildren(new ViewMapper() {
            @Override void applyToView(View view) {
                if (view instanceof PageView) {
                    ((PageView) view).setFormFieldHighlightEnabled(enabled);
                }
            }
        });
    }

    public void setMode(ReaderMode m) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MuPDFReaderView", "setMode " + interaction.mode() + " -> " + m);
        }
        interaction.setMode(m);
    }

    public ReaderMode getMode() {
        return interaction.mode();
    }

    /**
     * Injects the app-owned annotation-mode store so gesture-driven mode switches
     * (e.g., stylus down) route through a single owner (DrawingService) rather than
     * mutating view state directly.
     */
    public void setAnnotationModeStore(AnnotationModeStore store) {
        interaction.setAnnotationModeStore(store);
    }

    /**
     * Request a mode transition. Annotation-related modes are delegated to the injected
     * {@link AnnotationModeStore} so any required side-effects (e.g., committing pending ink)
     * live in one place. Non-annotation modes fall back to {@link #setMode(ReaderMode)}.
     */
    public void requestMode(ReaderMode desiredMode) {
        interaction.requestMode(desiredMode);
    }

    public MuPDFReaderView(Activity act) {
        super(act);
        interaction = new MuPDFReaderInteractionController(act, new MuPDFReaderInteractionController.Host() {
            @Override public int currentPage() { return getSelectedItemPosition(); }
            @Override public void setDisplayedViewIndex(int page) { MuPDFReaderView.this.setDisplayedViewIndex(page); }
            @Override public void doNextScrollWithCenter() { MuPDFReaderView.this.doNextScrollWithCenter(); }
            @Override public void setDocRelXScroll(float docRelXScroll) { MuPDFReaderView.this.setDocRelXScroll(docRelXScroll); }
            @Override public void setDocRelYScroll(float docRelYScroll) { MuPDFReaderView.this.setDocRelYScroll(docRelYScroll); }
            @Override public void resetupChildren() { MuPDFReaderView.this.resetupChildren(); }

            @Override public MuPDFPageView currentPageView() {
                View v = getSelectedView();
                return v instanceof MuPDFPageView ? (MuPDFPageView) v : null;
            }

            @Override public void onDocMotion() { MuPDFReaderView.this.onDocMotion(); }
            @Override public void onHit(Hit item) { MuPDFReaderView.this.onHit(item); }
            @Override public void onTapMainDocArea() { MuPDFReaderView.this.onTapMainDocArea(); }
            @Override public void onTapTopLeftMargin() { MuPDFReaderView.this.onTapTopLeftMargin(); }
            @Override public void onBottomRightMargin() { MuPDFReaderView.this.onBottomRightMargin(); }
            @Override public void addTextAnnotation(Annotation annot) { MuPDFReaderView.this.addTextAnnotFromUserInput(annot); }
            @Override public void onNumberOfStrokesChanged(int strokes) { MuPDFReaderView.this.onNumberOfStrokesChanged(strokes); }
            @Override public boolean maySwitchView() { return MuPDFReaderView.this.maySwitchView(); }
            @Override public boolean useStylus() { return mUseStylus; }
            @Override public View rootView() { return MuPDFReaderView.this; }

            @Override public boolean superOnDown(MotionEvent e) { return MuPDFReaderView.super.onDown(e); }
            @Override public boolean superOnScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) { return MuPDFReaderView.super.onScroll(e1, e2, dx, dy); }
            @Override public boolean superOnFling(MotionEvent e1, MotionEvent e2, float vx, float vy) { return MuPDFReaderView.super.onFling(e1, e2, vx, vy); }
            @Override public boolean superOnScaleBegin(ScaleGestureDetector d) { return MuPDFReaderView.super.onScaleBegin(d); }
            @Override public boolean superOnTouchEvent(MotionEvent event) { return MuPDFReaderView.super.onTouchEvent(event); }
            @Override public boolean superOnSingleTapUp(MotionEvent e) { return MuPDFReaderView.super.onSingleTapUp(e); }

            @Override public void setMode(ReaderMode mode) { MuPDFReaderView.this.setMode(mode); }
        });
    }

    @Override
    public void setAdapter(Adapter adapter) {
        super.setAdapter(adapter);
        try { interaction.resetFormFieldNavigator(); } catch (Throwable ignore) {}
        if (!(adapter instanceof MuPDFPageAdapter)) return;
        ((MuPDFPageAdapter) adapter).setModeRequester(MuPDFReaderView.this::requestMode);
        ((MuPDFPageAdapter) adapter).setTextAnnotationRequester(
                (TextAnnotationRequester) MuPDFReaderView.this::addTextAnnotFromUserInput);
    }

    /**
     * Navigate to the next/previous AcroForm widget (form field) in reading order.
     *
     * <p>Requires the {@code Forms} highlight toggle to be enabled for discoverability.</p>
     *
     * @param direction +1 for next, -1 for previous
     */
    public boolean navigateFormField(int direction) {
        return interaction.navigateFormField(direction);
    }

    /**
     * Starts a Fill & Sign action (signature/initials placement or one-tap stamps).
     *
     * <p>This is intentionally routed through the reader interaction controller so gesture
     * handling and overlay drawing stay coordinated.</p>
     */
    public void requestFillSignAction(@NonNull FillSignAction action) {
        interaction.requestFillSignAction(action);
    }

    public boolean onSingleTapUp(MotionEvent e) {
        MuPDFView pageView = (MuPDFView)getSelectedView();
        if (pageView == null ) return super.onSingleTapUp(e);
        return interaction.onSingleTapUp(e);
    }


    protected void addTextAnnotion(Annotation annot) {
        MuPDFView pageView = (MuPDFView)getSelectedView();
        ((MuPDFPageView)pageView).addTextAnnotation(annot);
    }
    
    @Override
    public boolean onDown(MotionEvent e) {
        return interaction.onDown(e);
    }

    
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {
        return interaction.onScroll(e1, e2, distanceX, distanceY);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                           float velocityY) {
        return interaction.onFling(e1, e2, velocityX, velocityY);
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector d) {
        return interaction.onScaleBegin(d);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return interaction.onTouchEvent(event);
    }

    public void addSearchResult(SearchResult result) { interaction.addSearchResult(result); }
    public void clearSearchResults() { interaction.clearSearchResults(); }
    public boolean hasSearchResults() { return interaction.hasSearchResults(); }
    public void goToNextSearchResult(int direction) { interaction.goToNextSearchResult(direction); }
    
    
    @Override
    protected void onChildSetup(int i, View v) {
        interaction.applySearchResultsToView(i, (MuPDFView) v);
        ((MuPDFView) v).setLinkHighlighting(interaction.linksEnabled());
        if (v instanceof PageView) {
            ((PageView) v).setFormFieldHighlightEnabled(formFieldHighlightEnabled);
        }
        if (v instanceof MuPDFPageView) {
            ((MuPDFPageView) v).setWidgetFieldNavigationRequester(new WidgetUiBridge.FieldNavigationRequester() {
                @Override public boolean navigate(int pageNumber, float docRelX, float docRelY, int direction) {
                    return interaction.navigateFormFieldFromLocation(pageNumber, docRelX, docRelY, direction);
                }
            });
        }

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
        bundle.putString("mMode", interaction.mode().toString());
        bundle.putInt("tapPageMargin", interaction.tapPageMargin());
        bundle.putBoolean("formFieldHighlights", formFieldHighlightEnabled);
        if(getSelectedView() != null) bundle.putParcelable("displayedViewInstanceState", ((PageView)getSelectedView()).onSaveInstanceState());
        
        return bundle;
    }
    
    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
                //Load 
            try {
                ReaderMode restoredMode = ReaderMode.valueOf(bundle.getString("mMode", interaction.mode().toString()));
                // Route restoration through requestMode so any injected mode owners
                // (e.g., AnnotationModeStore/DrawingService) stay in sync.
                requestMode(restoredMode);
            } catch (Throwable ignore) {
                // Keep best-effort restore behavior; fall back to current mode.
            }
            try {
                setFormFieldHighlightEnabled(bundle.getBoolean("formFieldHighlights", false));
            } catch (Throwable ignore) {
                // keep default
            }
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
        interaction.detach();
    }

    @Override
    public boolean maySwitchView() {
        ReaderMode m = interaction.mode();
        return m == ReaderMode.VIEWING || m == ReaderMode.SEARCHING;
    }

}
