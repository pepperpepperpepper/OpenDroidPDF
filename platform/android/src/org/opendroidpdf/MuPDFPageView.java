package org.opendroidpdf;

import org.opendroidpdf.core.AnnotationCallback;
import org.opendroidpdf.core.AnnotationController;
import org.opendroidpdf.core.DocumentContentController;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.SignatureBooleanCallback;
import org.opendroidpdf.core.SignatureStringCallback;
import org.opendroidpdf.core.WidgetBooleanCallback;
import org.opendroidpdf.core.WidgetCompletionCallback;
import org.opendroidpdf.core.WidgetController;
import org.opendroidpdf.core.WidgetAreasCallback;
import org.opendroidpdf.core.WidgetPassClickCallback;
import org.opendroidpdf.app.annotation.AnnotationUiController;
import org.opendroidpdf.app.annotation.TextAnnotationQuadPoints;
import org.opendroidpdf.app.annotation.InkUndoController;
import org.opendroidpdf.app.annotation.TextAnnotationPageDelegate;
import org.opendroidpdf.app.drawing.InkController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.selection.PageSelectionCoordinator;
import org.opendroidpdf.app.selection.SelectionActionRouter;
import org.opendroidpdf.app.selection.SelectionPageModel;
import org.opendroidpdf.app.selection.SelectionUiBridge;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.model.SidecarNote;
import org.opendroidpdf.app.widget.WidgetAreasLoader;
import org.opendroidpdf.widget.WidgetUiController;
import org.opendroidpdf.app.reader.ReaderComposition;
import org.opendroidpdf.app.reader.gesture.AnnotationHitHelper;
import org.opendroidpdf.app.reader.gesture.PageHitRouter;
import org.opendroidpdf.app.reader.gesture.PageTapHitRouter;
import org.opendroidpdf.app.reader.gesture.ReaderMode;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import android.annotation.TargetApi;
import org.opendroidpdf.TextProcessor;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import java.util.Objects;
import android.text.InputType;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import org.opendroidpdf.app.overlay.MuPdfPatchRenderer;
import org.opendroidpdf.app.widget.WidgetUiBridge;
import org.opendroidpdf.app.sidecar.model.SidecarNote;
import java.util.List;


public class MuPDFPageView extends PageView implements MuPDFView, SelectionPageModel {
	private static final String TAG = "MuPDFPageView";
    private static final Annotation[] EMPTY_ANNOTATIONS = new Annotation[0];
    private static final int UNDO_DOMAIN_INK = 1;
    private static final int UNDO_DOMAIN_TEXT = 2;
    private int lastUndoDomain = UNDO_DOMAIN_INK;

		
	private final FilePicker.FilePickerSupport mFilePickerSupport;
	private final MuPdfController muPdfController;
	    private final ReaderComposition composition;
	    private final AnnotationController annotationController;
	    private final AnnotationUiController annotationUiController;
private final InkController inkController;
    @Nullable private final SidecarAnnotationSession sidecarSession;
    private final SidecarSelectionController sidecarSelectionController;
    private final WidgetController widgetController;
    private final PageHitRouter pageHitRouter;
	    private final PageTapHitRouter tapHitRouter;
	    private final SelectionActionRouter selectionRouter;
	    private final PageSelectionCoordinator selectionCoordinator;
	    private final MuPDFPageViewWidgets widgets;
	    private final MuPDFPageViewTextAnnotations textAnnotations;
	private Runnable changeReporter;
			    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager;
			    private final SelectionUiBridge selectionUiBridge;
			    private final AnnotationHitHelper annotationHitHelper;
			    private final MuPdfPatchRenderer patchRenderer;
                private final TextAnnotationPageDelegate textAnnotationDelegate;

	public MuPDFPageView(Context context,
	                     FilePicker.FilePickerSupport filePickerSupport,
	                     MuPdfController controller,
	                     ViewGroup parent,
	                     ReaderComposition composition) {
	        super(context,
	                parent,
	                new DocumentContentController(Objects.requireNonNull(controller, "MuPdfController required")),
	                composition.editorPreferences());
				mFilePickerSupport = filePickerSupport;
				muPdfController = controller;
		        this.composition = composition;
		        patchRenderer = new MuPdfPatchRenderer(muPdfController);
	            annotationController = composition.annotationController();
	            annotationUiController = composition.annotationUiController();
				widgetController = composition.widgetController();
                widgets = new MuPDFPageViewWidgets(
                        new MuPDFPageViewWidgets.Host() {
                            @NonNull @Override public ViewGroup viewGroup() { return MuPDFPageView.this; }
                            @NonNull @Override public Context context() { return MuPDFPageView.this.getContext(); }
                            @NonNull @Override public android.content.res.Resources resources() { return MuPDFPageView.this.getResources(); }
                            @Override public float scale() { return MuPDFPageView.this.getScale(); }
                            @Override public int viewWidthPx() { return MuPDFPageView.this.getWidth(); }
                            @Override public int viewHeightPx() { return MuPDFPageView.this.getHeight(); }
                            @Override public void requestLayoutSafe() { try { MuPDFPageView.this.requestLayout(); } catch (Throwable ignore) {} }
                            @Override public void invalidateOverlay() { try { MuPDFPageView.this.invalidateOverlay(); } catch (Throwable ignore) {} }
                        },
                        mFilePickerSupport,
                        composition,
                        widgetController,
                        () -> { if (changeReporter != null) changeReporter.run(); });
	        sidecarSession = composition.sidecarSession();
	        if (sidecarSession != null) {
	            setSidecarAnnotations(sidecarSession);
	        }
	        inkController = new InkController(new InkHost(), muPdfController, sidecarSession);
			        pageHitRouter = new PageHitRouter(new HitHost());
		        this.selectionManager = composition.selectionManager();
		        this.selectionUiBridge = new SelectionUiBridge(this, selectionManager);
		        annotationHitHelper = new AnnotationHitHelper(selectionUiBridge.selectionManager());
		        selectionRouter = new SelectionActionRouter(selectionUiBridge.selectionManager(), annotationUiController, selectionUiBridge.selectionRouterHost());

        sidecarSelectionController = new SidecarSelectionController(new SidecarSelectionController.Host() {
            @Override public SidecarAnnotationSession sidecarSessionOrNull() { return sidecarSession; }
            @Override public int pageNumber() { return mPageNumber; }
            @Override public boolean commentsVisible() { return MuPDFPageView.this.areCommentsVisible(); }
            @Override public float scale() { return getScale(); }
            @Override public int viewLeft() { return getLeft(); }
            @Override public int viewTop() { return getTop(); }
            @Override public void setItemSelectBox(@Nullable RectF rect) { MuPDFPageView.this.setItemSelectBox(rect); }
            @Override public void forwardTextAnnotation(Annotation annotation) { MuPDFPageView.this.forwardTextAnnotation(annotation); }
        });
        tapHitRouter = new PageTapHitRouter(pageHitRouter, sidecarSelectionController);
	        selectionCoordinator = new PageSelectionCoordinator(
	                sidecarSelectionController,
	                selectionRouter,
	                () -> inkController.refreshUndoState());

	        textAnnotationDelegate = new TextAnnotationPageDelegate(new TextAnnotationPageDelegate.Host() {
            @NonNull @Override public Context context() { return MuPDFPageView.this.getContext(); }
            @NonNull @Override public android.content.res.Resources resources() { return MuPDFPageView.this.getResources(); }
            @Override public float scale() { return MuPDFPageView.this.getScale(); }
            @Override public int viewWidthPx() { return MuPDFPageView.this.getWidth(); }
            @Override public int viewHeightPx() { return MuPDFPageView.this.getHeight(); }
            @Override public int pageNumber() { return mPageNumber; }

            @Nullable @Override public MuPdfController muPdfControllerOrNull() { return muPdfController; }
            @Nullable @Override public SidecarAnnotationSession sidecarSessionOrNull() { return sidecarSession; }

            @NonNull @Override public SidecarSelectionController sidecarSelectionController() { return sidecarSelectionController; }
            @NonNull @Override public org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager() { return selectionManager; }
            @NonNull @Override public SelectionUiBridge selectionUiBridge() { return selectionUiBridge; }
            @NonNull @Override public InkController inkController() { return inkController; }
            @NonNull @Override public AnnotationUiController annotationUiController() { return annotationUiController; }

            @Nullable @Override public Annotation[] embeddedAnnotationsOrNull() { return mAnnotations; }

            @Override public void requestFullRedrawAfterNextAnnotationLoad() { MuPDFPageView.this.requestFullRedrawAfterNextAnnotationLoad(); }
            @Override public void discardRenderedPage() { MuPDFPageView.this.discardRenderedPage(); }
            @Override public void loadAnnotations() { MuPDFPageView.this.loadAnnotations(); }
	            @Override public void invalidateOverlay() { MuPDFPageView.this.invalidateOverlay(); }
	            @Override public void setAnnotationSelectionBox(@Nullable RectF rectDoc) {
                    try {
                        MuPDFPageView.this.setItemSelectBox(rectDoc);
                    } catch (Throwable ignore) {
                    }
                }
	        });

            textAnnotations = new MuPDFPageViewTextAnnotations(
                    new MuPDFPageViewTextAnnotations.Host() {
                        @NonNull @Override public ViewGroup viewGroup() { return MuPDFPageView.this; }
                        @NonNull @Override public Context context() { return MuPDFPageView.this.getContext(); }
                        @Override public float scale() { return MuPDFPageView.this.getScale(); }
                        @Override public int viewWidthPx() { return MuPDFPageView.this.getWidth(); }
                        @Override public int viewHeightPx() { return MuPDFPageView.this.getHeight(); }
                        @Override public int pageNumber() { return mPageNumber; }
                        @Override public void requestLayoutSafe() { try { MuPDFPageView.this.requestLayout(); } catch (Throwable ignore) {} }
                        @Override public void invalidateOverlaySafe() { try { MuPDFPageView.this.invalidateOverlay(); } catch (Throwable ignore) {} }
                        @Override public void addTextAnnotationFromUi(@NonNull Annotation annotation) { MuPDFPageView.this.addTextAnnotationFromUi(annotation); }
                    },
                    muPdfController,
                    composition,
                    sidecarSession,
                    sidecarSelectionController,
                    selectionManager,
                    selectionUiBridge,
                    selectionRouter,
                    selectionCoordinator,
                    textAnnotationDelegate,
                    widgets);

		        // Signature UI now handled by SignatureFlowController
		}

	    @Override
	    public void setSidecarNotesStickyModeEnabled(boolean enabled) {
	        super.setSidecarNotesStickyModeEnabled(enabled);
	        try { sidecarSelectionController.setStickyNotesOnly(enabled); } catch (Throwable ignore) {}
	    }

    @Override public Annotation[] annotations() { return mAnnotations; }
    @Override public int pageNumber() { return mPageNumber; }
    @Override public int pageCount() { return muPdfController != null ? muPdfController.pageCount() : 0; }
    @Override public long reflowLocation() {
        try {
            if (muPdfController == null) return -1L;
            return muPdfController.rawRepository().locationFromPageNumber(mPageNumber);
        } catch (Throwable ignore) {
            return -1L;
        }
    }

	    @Override public void requestFullRedrawAfterNextAnnotationLoad() { super.requestFullRedrawAfterNextAnnotationLoad(); }
		    @Override public void loadAnnotations() { super.loadAnnotations(); }
		    @Override public void discardRenderedPage() { super.discardRenderedPage(); }
		    @Override public void redraw(boolean updateHq) { super.redraw(updateHq); }
		    @Override public TextWord[][] textLines() {
	        TextWord[][] lines = muPdfController != null ? muPdfController.textLines(mPageNumber) : null;
	        return lines != null ? lines : new TextWord[0][];
	    }

	    @Override public void setModeDrawing() {
        composition.modeRequester().requestMode(ReaderMode.DRAWING);
    }

    @Override public void processSelectedText(TextProcessor processor) { super.processSelectedText(processor); }

    @Override public void setSelectionBox(RectF rect) { setItemSelectBox(rect); }
    @Override public void refreshUndoState() { inkController.refreshUndoState(); }

	    @Override
	    /* package */ void setItemSelectBox(RectF rect) {
	        textAnnotations.onSetItemSelectBox();
	        super.setItemSelectBox(rect);
	    }

	    /** Whether corner resize handles are enabled for the currently selected text annotation. */
	    public boolean textResizeHandlesEnabled() {
	        return textAnnotations.textResizeHandlesEnabled();
	    }

	    /** Enables/disables corner resize handles for the current selection (if applicable). */
	    public boolean setTextResizeHandlesEnabled(boolean enabled) {
	        return textAnnotations.setTextResizeHandlesEnabled(enabled);
	    }

	    /** Toggles corner resize handles for the current selection (if applicable). */
	    public boolean toggleTextResizeHandlesEnabled() {
	        return textAnnotations.toggleTextResizeHandlesEnabled();
	    }

	    private class InkHost implements InkController.Host {
	        @Override public DrawingController drawingController() { return MuPDFPageView.this.getDrawingController(); }
	        @Override public void requestReaderErasingMode() {
                composition.modeRequester().requestMode(ReaderMode.ERASING);
            }
	        @Override public int pageNumber() { return mPageNumber; }
        @Override public void requestFullRedraw() { requestFullRedrawAfterNextAnnotationLoad(); }
        @Override public void loadAnnotations() { MuPDFPageView.this.loadAnnotations(); }
        @Override public void discardRenderedPage() { MuPDFPageView.this.discardRenderedPage(); }
        @Override public void redraw(boolean updateHq) { MuPDFPageView.this.redraw(updateHq); }
        @Override public void invalidateOverlay() { MuPDFPageView.this.invalidateOverlay(); }
        @Override public float currentInkThickness() { return MuPDFPageView.this.currentInkThickness(); }
        @Override public int currentInkColor() { return MuPDFPageView.this.currentInkColor(); }
        @Override public float currentEraserThickness() { return MuPDFPageView.this.currentEraserThickness(); }
    }

    private class HitHost implements PageHitRouter.Host {
        @Override public float scale() { return getScale(); }
        @Override public int viewLeft() { return getLeft(); }
        @Override public int viewTop() { return getTop(); }
        @Override public int pageNumber() { return mPageNumber; }

	        @Override public LinkInfo[] links() { return mLinks; }
	        @Override public Annotation[] annotations() { return MuPDFPageView.this.areCommentsVisible() ? mAnnotations : EMPTY_ANNOTATIONS; }
	        @Override public RectF[] widgetAreas() { return widgets.widgetAreas(); }

	        @Override public AnnotationHitHelper annotationHitHelper() { return annotationHitHelper; }
	        @Override public WidgetController widgetController() { return widgetController; }
	        @Override public void setWidgetJob(WidgetController.WidgetJob job) {
	            widgets.setWidgetJob(job);
	        }

        @Override public void deselectAnnotation() { MuPDFPageView.this.deselectAnnotation(); }
        @Override public void selectAnnotation(int index, RectF bounds) {
            long objectId = -1L;
            try {
                Annotation[] annots = mAnnotations;
                if (annots != null && index >= 0 && index < annots.length) {
                    Annotation a = annots[index];
                    if (a != null) objectId = a.objectNumber;
                }
            } catch (Throwable ignore) {
                objectId = -1L;
            }
            selectionManager.select(index, objectId, bounds, selectionUiBridge.selectionBoxHost());
        }
        @Override public void onTextAnnotationTapped(Annotation annotation) { forwardTextAnnotation(annotation); }

        @Override public void requestChangeReport() {
            try {
                if (muPdfController != null) muPdfController.markDocumentDirty();
            } catch (Throwable ignore) {
            }
            if (changeReporter != null) changeReporter.run();
        }
	        @Override public void invokeTextDialog(String text, float docRelX, float docRelY) {
	            widgets.invokeTextDialog(text, docRelX, docRelY);
	        }
	        @Override public void invokeChoiceDialog(String[] options, String[] selected, boolean multiSelect, boolean editable, float docRelX, float docRelY) {
	            widgets.invokeChoiceDialog(options, selected, multiSelect, editable, docRelX, docRelY);
	        }
	        @Override public void warnNoSignatureSupport() { widgets.warnNoSignatureSupport(); }
	        @Override public void invokeSigningDialog() { widgets.invokeSigningDialog(); }
	        @Override public void invokeSignatureCheckingDialog() { widgets.invokeSignatureCheckingDialog(); }
	    }

    // Signature flow moved to SignatureFlowController

    public LinkInfo hitLink(float x, float y) {
        return pageHitRouter.hitLink(x, y);
	}

	    // Debug-only entry points used by DebugActionsController via MuPDFReaderView
	    public void debugShowTextWidgetDialog() { widgets.debugShowTextWidgetDialog(); }
	    public void debugShowChoiceWidgetDialog() { widgets.debugShowChoiceWidgetDialog(); }

    /**
     * Supplies a stable identity for form field navigation state. When the document changes
     * (new adapter/core), this key changes and navigators should reset.
     */
    public Object formFieldNavigationKey() {
        return muPdfController != null ? muPdfController : this;
    }

    /** Returns the document's page count for form field navigation. */
    public int documentPageCountForNavigation() {
        try {
            return muPdfController != null ? muPdfController.pageCount() : 0;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    /** Loads widget areas for an arbitrary page index for form field navigation. */
    public RectF[] widgetAreasForNavigation(int pageIndex) {
        try {
            return widgetController != null ? widgetController.widgetAreas(pageIndex) : new RectF[0];
        } catch (Throwable ignore) {
            return new RectF[0];
        }
    }

    /** Injects a callback so widget dialogs can request "Next field" navigation. */
    public void setWidgetFieldNavigationRequester(@Nullable org.opendroidpdf.app.widget.WidgetUiBridge.FieldNavigationRequester requester) {
        widgets.setWidgetFieldNavigationRequester(requester);
    }

		    private void forwardTextAnnotation(Annotation annotation) {
		        textAnnotations.forwardTextAnnotation(annotation);
		    }

    @NonNull
    public TextAnnotationPageDelegate textAnnotationDelegate() {
        return textAnnotationDelegate;
    }

    @Nullable
    public SidecarSelectionController.Selection selectedSidecarSelectionOrNull() {
        return sidecarSelectionController != null ? sidecarSelectionController.selectionOrNull() : null;
    }

    public void setChangeReporter(Runnable reporter) {
        changeReporter = reporter;
        widgets.setChangeReporter(() -> { if (changeReporter != null) changeReporter.run(); });
    }

    // passClickEvent/clickWouldHit override below to include sidecar overlay hit-testing.
    
    // Expose selection-handle hit-testing/movement across package boundaries.
    @Override public boolean hitsLeftMarker(float x, float y) { return super.hitsLeftMarker(x, y); }
    @Override public boolean hitsRightMarker(float x, float y) { return super.hitsRightMarker(x, y); }
    public void moveLeftMarker(float x, float y) { super.moveLeftMarker(x, y); }
    public void moveRightMarker(float x, float y) { super.moveRightMarker(x, y); }


    @TargetApi(11)
    public boolean copySelection() { return selectionRouter.copySelection(); }

    public boolean markupSelection(final Annotation.Type type) { return selectionRouter.markupSelection(type); }
    public boolean replaceSelection() { return selectionRouter.replaceSelection(); }

		    @Override
		    public void deleteSelectedAnnotation() {
		        textAnnotations.deleteSelectedAnnotation();
		    }

	    public void editSelectedAnnotation() {
	        textAnnotations.editSelectedAnnotation();
	    }

    public Annotation.Type selectedAnnotationType() { return selectionRouter.selectedAnnotationType(); }
    public boolean selectedAnnotationIsEditable() {
        return selectionCoordinator.selectedAnnotationIsEditable();
    }

	    @Override
	    protected boolean showItemSelectionHandles() {
	        return textAnnotations.showItemSelectionHandles();
	    }

	    @Override
	    protected boolean showItemResizeHandles() {
	        return textAnnotations.showItemResizeHandles();
	    }

    @Override
    @Nullable
    protected RectF[] widgetAreasForOverlay() {
        return widgets.widgetAreas();
    }

	    @Override
	    protected void onAnnotationsLoaded(Annotation[] annotations) {
	        super.onAnnotationsLoaded(annotations);
	        textAnnotations.onAnnotationsLoaded(annotations);
	    }

	    public void deselectAnnotation() {
	        selectionCoordinator.deselectAnnotation();
	    }

    @Override
    public void startDraw(final float x, final float y) {
        inkController.onStartDrawGesture(x, y);
    }

    @Override
    public void continueDraw(final float x, final float y) {
        inkController.onContinueDrawGesture(x, y);
    }

    @Override
    public void finishDraw() {
        inkController.onFinishDrawGesture();
    }

    @Override
    public void cancelDraw() {
        inkController.onCancelDrawGesture();
    }

    @Override
    public void startErase(final float x, final float y) {
        inkController.beginEraseGesture(x, y, getScale(), getLeft(), getTop());
    }

    @Override
    public void continueErase(final float x, final float y) {
        inkController.continueEraseGesture(x, y, getScale(), getLeft(), getTop());
    }

    @Override
    public void finishErase(final float x, final float y) {
        inkController.finishEraseGesture(x, y);
    }

    @Override
    public boolean saveDraw() {
        return inkController.saveDraw(this::burnPendingInkOntoPatch);
    }

    private void burnPendingInkOntoPatch() {
        // Keep the just-drawn stroke visible immediately (burn onto the current
        // patch bitmap) while the native ink annotation is committed and the
        // page re-renders.
        super.saveDraw();
    }

    @Override
    public void undoDraw() {
        boolean canTextUndo = false;
        boolean canInkUndo = false;
        try { canTextUndo = (sidecarSession == null) && textAnnotationDelegate.hasEmbeddedTextUndo(); } catch (Throwable ignore) { canTextUndo = false; }
        try { canInkUndo = inkController.canUndo(); } catch (Throwable ignore) { canInkUndo = false; }

        if (canTextUndo && (!canInkUndo || shouldPreferTextUndo())) {
            try {
                if (textAnnotationDelegate.undoLastEmbeddedTextEdit()) {
                    lastUndoDomain = UNDO_DOMAIN_TEXT;
                    return;
                }
            } catch (Throwable ignore) {
            }
        }

        if (canInkUndo) {
            inkController.undoDraw();
            lastUndoDomain = UNDO_DOMAIN_INK;
            return;
        }

        if (canTextUndo) {
            try {
                if (textAnnotationDelegate.undoLastEmbeddedTextEdit()) {
                    lastUndoDomain = UNDO_DOMAIN_TEXT;
                }
            } catch (Throwable ignore) {
            }
        }
    }

    @Override
    public void redoDraw() {
        boolean canTextRedo = false;
        boolean canInkRedo = false;
        try { canTextRedo = (sidecarSession == null) && textAnnotationDelegate.hasEmbeddedTextRedo(); } catch (Throwable ignore) { canTextRedo = false; }
        try { canInkRedo = inkController.canRedo(); } catch (Throwable ignore) { canInkRedo = false; }

        if (lastUndoDomain == UNDO_DOMAIN_TEXT && canTextRedo) {
            try {
                if (textAnnotationDelegate.redoLastEmbeddedTextEdit()) {
                    return;
                }
            } catch (Throwable ignore) {
            }
        } else if (lastUndoDomain == UNDO_DOMAIN_INK && canInkRedo) {
            inkController.redoDraw();
            return;
        }

        if (canTextRedo) {
            try {
                if (textAnnotationDelegate.redoLastEmbeddedTextEdit()) {
                    lastUndoDomain = UNDO_DOMAIN_TEXT;
                    return;
                }
            } catch (Throwable ignore) {
            }
        }
        if (canInkRedo) {
            inkController.redoDraw();
            lastUndoDomain = UNDO_DOMAIN_INK;
        }
    }

    @Override
	    public boolean canUndo() {
            if (inkController.canUndo()) return true;
            try { return sidecarSession == null && textAnnotationDelegate.hasEmbeddedTextUndo(); } catch (Throwable ignore) { return false; }
        }

        private boolean shouldPreferTextUndo() {
            long tText = 0L;
            long tInk = 0L;
            try { tText = textAnnotationDelegate.embeddedTextHistoryLastMutationUptimeMs(); } catch (Throwable ignore) { tText = 0L; }
            try { tInk = inkController.lastUndoMutationUptimeMs(); } catch (Throwable ignore) { tInk = 0L; }
            return tText >= tInk;
        }

	    // Wait (best-effort) for the asynchronous ink-commit task to finish so that
	    // subsequent export/print includes the accepted stroke. Safe to call off the UI thread.
	    public void awaitInkCommit(long timeoutMs) {
	        // Ink commits run synchronously; retained for legacy callers that awaited AsyncTasks.
	    }
	    
		@Override
		protected CancellableTaskDefinition<PatchInfo, PatchInfo> getRenderTask(PatchInfo patchInfo) {
			return patchRenderer.newRenderTask(mPageNumber);
		}
		
	    
		@Override
		protected void addMarkup(PointF[] quadPoints, Annotation.Type type) {
			muPdfController.addMarkupAnnotation(mPageNumber, quadPoints, type);
		}

	@Override
	protected void addTextAnnotation(final Annotation annot) {
		try {
			textAnnotationDelegate.addTextAnnotationFromUiWithUndo(annot);
		} catch (Throwable t) {
			// Fallback: keep legacy behavior when the delegate fails unexpectedly.
			// FreeText appearance streams can be missed by incremental updatePage() paths.
			// Force a full draw on the next annotation reload so newly added text is visible.
			requestFullRedrawAfterNextAnnotationLoad();
			final PointF[] quadPoints = TextAnnotationQuadPoints.fromBounds(
					sidecarSession != null,
					annot.left,
					annot.top,
					annot.right,
					annot.bottom,
					getHeight() / getScale());
			annotationUiController.addTextAnnotation(
					mPageNumber,
					quadPoints,
					annot.text,
					() -> {
						// Ensure the next draw regenerates the page bitmap (some devices keep
						// stale caches even on full redraws after FreeText updates).
						requestFullRedrawAfterNextAnnotationLoad();
						if (sidecarSession == null) discardRenderedPage();
						loadAnnotations();
					});
			inkController.refreshUndoState();
		}
	}

	public void addTextAnnotationFromUi(final Annotation annot) {
		addTextAnnotation(annot);
	}

    /**
     * Adds a committed Ink annotation from a non-drawing UI flow (e.g., Fill &amp; Sign).
     *
     * <p>This bypasses the pending-stroke lifecycle so the placement flow can commit directly
     * while still updating undo/dirty/redraw state consistently.</p>
     */
    public boolean addInkAnnotationFromUi(@NonNull PointF[][] arcsDoc) {
        if (arcsDoc == null || arcsDoc.length == 0) return false;
        if (sidecarSession != null) return false;

        PointF[][] sanitized = sanitizeInkArcs(arcsDoc);
        if (sanitized == null || sanitized.length == 0) return false;

        final int before = safeAnnotationCount(mPageNumber);
        try {
            muPdfController.addInkAnnotation(mPageNumber, sanitized);
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Failed to add ink annotation", t);
            return false;
        }

        if (before >= 0) {
            final int after = safeAnnotationCount(mPageNumber);
            if (after >= 0 && after <= before) {
                android.util.Log.e(TAG, "Ink commit did not add annotation (page=" + mPageNumber + " before=" + before + " after=" + after + ")");
                return false;
            }
        }

        // Like InkController: refresh appearance streams and force full redraw.
        try {
            android.graphics.Bitmap onePx = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
            MuPDFCore.Cookie cookie = muPdfController.newRenderCookie();
            muPdfController.drawPage(onePx, mPageNumber, 1, 1, 0, 0, 1, 1, cookie);
            cookie.destroy();
            onePx.recycle();
        } catch (Throwable ignore) {
        }

        requestFullRedrawAfterNextAnnotationLoad();
        discardRenderedPage();
        loadAnnotations();

        try { inkController.undo().recordCommittedInkForUndo(sanitized); } catch (Throwable ignore) {}
        try { inkController.refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

		public void updateTextAnnotationContentsByObjectNumber(long objectNumber, String text) {
			textAnnotationDelegate.updateTextAnnotationContentsByObjectNumber(objectNumber, text);
		}

    private int safeAnnotationCount(int pageNumber) {
        try {
            Annotation[] annots = muPdfController.annotations(pageNumber);
            return annots != null ? annots.length : 0;
        } catch (Throwable ignore) {
            return -1;
        }
    }

    @Nullable
    private static PointF[][] sanitizeInkArcs(@Nullable PointF[][] arcs) {
        if (arcs == null) return null;
        java.util.ArrayList<PointF[]> strokes = new java.util.ArrayList<>();
        for (PointF[] arc : arcs) {
            if (arc == null) continue;
            java.util.ArrayList<PointF> pts = new java.util.ArrayList<>(arc.length);
            for (PointF p : arc) {
                if (p == null) continue;
                if (!isFinite(p.x) || !isFinite(p.y)) continue;
                pts.add(new PointF(p.x, p.y));
            }
            if (pts.size() >= 2) strokes.add(pts.toArray(new PointF[0]));
        }
        if (strokes.isEmpty()) return null;
        return strokes.toArray(new PointF[0][]);
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

	@Override
	public void setPage(final int page, PointF size) {
        sidecarSelectionController.clearSelection();
        inkController.resetEraserSession();
	        inkController.clear();
	        dismissInlineTextAnnotationEditor();
	        try { textAnnotationDelegate.clearEmbeddedTextUndoHistory(); } catch (Throwable ignore) {}
	        lastUndoDomain = UNDO_DOMAIN_INK;
	        widgets.onSetPage(page);

			super.setPage(page, size);
	        loadAnnotations();//Must be done after super.setPage() otherwise page number is wrong!
		}

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        widgets.onLayout();
        textAnnotations.onLayout();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        widgets.onDispatchTouchEvent(ev);
        textAnnotations.onDispatchTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    /** Shows an in-place editor overlay for the provided text annotation (FreeText or sidecar note). */
    public boolean showInlineTextAnnotationEditor(@NonNull Annotation annotation) {
        return textAnnotations.showInlineTextAnnotationEditor(annotation);
    }

    /** Dismisses the in-place text annotation editor if present (commits on focus loss). */
    public void dismissInlineTextAnnotationEditor() {
        textAnnotations.dismissInlineTextAnnotationEditor();
    }

	    @Override
	    public Hit passClickEvent(MotionEvent e) {
	        return tapHitRouter.passClick(e);
	    }

    public Hit clickWouldHit(MotionEvent e) {
        return tapHitRouter.wouldHit(e);
    }

	public void setScale(float scale) {
            // This type of view scales automatically to fit the size
            // determined by the parent view groups during layout
	}

    @Override
    public void releaseResources() {
        inkController.resetEraserSession();
        widgets.releaseResources();

        if (annotationUiController != null) annotationUiController.release();

		super.releaseResources();
	}
}
