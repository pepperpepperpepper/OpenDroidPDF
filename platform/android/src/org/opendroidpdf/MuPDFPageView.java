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
import org.opendroidpdf.app.overlay.TextDragPreviewOverlay;
import org.opendroidpdf.app.annotation.TextFontFamily;

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
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import org.opendroidpdf.app.overlay.MuPdfPatchRenderer;
import org.opendroidpdf.app.widget.WidgetUiBridge;
import org.opendroidpdf.app.sidecar.model.SidecarNote;
import java.util.List;


public class MuPDFPageView extends PageView implements MuPDFView, SelectionPageModel {
	private static final String TAG = "MuPDFPageView";
    private static final int UNDO_DOMAIN_INK = 1;
    private static final int UNDO_DOMAIN_TEXT = 2;
    private int lastUndoDomain = UNDO_DOMAIN_INK;
    private long inkCommitPreviewToken = 0L;

		
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
		        this.selectionManager = composition.selectionManager();
		        this.selectionUiBridge = new SelectionUiBridge(this, selectionManager);
		        annotationHitHelper = new AnnotationHitHelper(selectionUiBridge.selectionManager());
		        selectionRouter = new SelectionActionRouter(selectionUiBridge.selectionManager(), annotationUiController, selectionUiBridge.selectionRouterHost());
	        inkController = new InkController(new MuPDFPageViewInkHost(this, composition), muPdfController, sidecarSession);
            pageHitRouter = new PageHitRouter(new MuPDFPageViewHitHost(
                    this,
                    widgets,
                    widgetController,
                    annotationHitHelper,
                    selectionManager,
                    selectionUiBridge));

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
    @Override public boolean addEmbeddedMarkupAnnotationWithUndo(int pageNumber, PointF[] quadPoints, Annotation.Type type, Runnable onComplete) {
        try {
            int page = pageNumber != mPageNumber ? mPageNumber : pageNumber;
            return textAnnotationDelegate.addEmbeddedMarkupAnnotationWithUndo(page, type, quadPoints, onComplete);
        } catch (Throwable ignore) {
            return false;
        }
    }

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

			    /* package */ void forwardTextAnnotation(Annotation annotation) {
			        textAnnotations.forwardTextAnnotation(annotation);
			    }

	    /* package */ void requestChangeReport() {
	        try {
	            if (muPdfController != null) muPdfController.markDocumentDirty();
	        } catch (Throwable ignore) {
	        }
	        if (changeReporter != null) changeReporter.run();
	    }

    @NonNull
    public TextAnnotationPageDelegate textAnnotationDelegate() {
        return textAnnotationDelegate;
    }

	    @Nullable
	    public SidecarSelectionController.Selection selectedSidecarSelectionOrNull() {
	        return sidecarSelectionController != null ? sidecarSelectionController.selectionOrNull() : null;
	    }

	    /** Exposes the active sidecar annotation session when this page is backed by sidecar annotations. */
	    @Nullable
	    public SidecarAnnotationSession sidecarSessionOrNull() {
	        return sidecarSession;
	    }

	    /** Exposes the underlying MuPDF controller for cross-page operations (best-effort; may be null). */
	    @Nullable
	    public MuPdfController muPdfControllerOrNull() {
	        return muPdfController;
	    }

	    /**
	     * Replaces an embedded PDF Ink annotation by deleting it and re-adding the provided ink arcs.
	     *
	     * <p>This is used for direct manipulation (move/resize) of ink signatures. It is a best-effort
	     * operation: if the replacement fails after deletion, the original arcs are re-added.</p>
	     *
	     * @return {@code true} if the replacement succeeded, {@code false} otherwise.
	     */
	    public boolean replaceEmbeddedInkAnnotationByObjectNumberFromUi(long objectNumber,
	                                                                    @NonNull PointF[][] originalArcsDoc,
	                                                                    @NonNull PointF[][] updatedArcsDoc) {
	        if (sidecarSession != null) return false;
	        if (muPdfController == null) return false;
	        if (objectNumber <= 0L) return false;
	        if (originalArcsDoc == null || originalArcsDoc.length == 0) return false;
	        if (updatedArcsDoc == null || updatedArcsDoc.length == 0) return false;

	        try {
	            muPdfController.deleteAnnotationByObjectNumber(mPageNumber, objectNumber);
	        } catch (Throwable t) {
	            android.util.Log.e(TAG, "Failed to delete ink annotation for replacement", t);
	            return false;
	        }

	        boolean ok = false;
	        try {
	            ok = addInkAnnotationFromUi(updatedArcsDoc);
	        } catch (Throwable ignore) {
	            ok = false;
	        }
	        if (ok) return true;

	        // Best-effort rollback: restore original arcs if the re-add failed.
	        try { addInkAnnotationFromUi(originalArcsDoc); } catch (Throwable ignore) {}
	        return false;
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

        /**
         * Best-effort: wait for any in-flight annotation mutations (add/update/delete) to finish.
         *
         * <p>Some annotation edits run off the UI thread; export/save should wait so the
         * resulting PDF copy includes the latest state.</p>
         */
        public boolean awaitPendingAnnotationJobsBlocking(long timeoutMs) {
            try {
                if (annotationUiController == null) return true;
                return annotationUiController.awaitIdleBlocking(timeoutMs);
            } catch (Throwable ignore) {
                return true;
            }
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

	        PointF[][] sanitized = sanitizeInkArcs(arcsDoc);
	        if (sanitized == null || sanitized.length == 0) return false;

	        // If the user is placing/editing a signature, ensure annotation rendering is enabled so it
	        // doesn't "disappear" when the placement overlay clears (e.g., if the user hid comments).
	        try { ensureCommentsVisibleForEditing(); } catch (Throwable ignore) {}

	        // Sidecar-backed documents (e.g., EPUB or read-only PDFs) persist ink strokes into the
	        // sidecar session so Fill & Sign placements do not "disappear" after the overlay clears.
	        if (sidecarSession != null) {
	            try {
	                int color = currentInkColor();
	                float thickness = currentInkThickness();
	                long now = System.currentTimeMillis();
		                java.util.List<org.opendroidpdf.app.sidecar.model.SidecarInkStroke> inserted =
		                        sidecarSession.addInkFromArcs(mPageNumber, sanitized, color, thickness, now);
		                if (inserted != null && !inserted.isEmpty()) {
		                    sidecarSession.recordUndoInkAdded(mPageNumber, inserted);
		                    try { sidecarSelectionController.selectInkGroupByCreatedAt(now); } catch (Throwable ignore) {}
		                    invalidateOverlay();
		                    try { inkController.refreshUndoState(); } catch (Throwable ignore) {}
		                    return true;
		                }
		            } catch (Throwable t) {
	                android.util.Log.e(TAG, "Failed to add sidecar ink stroke", t);
	            }
	            try { inkController.refreshUndoState(); } catch (Throwable ignore) {}
	            return false;
	        }

	        java.util.HashSet<Long> beforeObjectIds = new java.util.HashSet<>();
	        int beforeCount = -1;
	        try {
	            Annotation[] beforeAnnots = muPdfController.annotations(mPageNumber);
	            if (beforeAnnots != null) {
	                beforeCount = beforeAnnots.length;
	                for (Annotation a : beforeAnnots) {
	                    if (a != null && a.objectNumber > 0L) beforeObjectIds.add(a.objectNumber);
	                }
	            }
	        } catch (Throwable ignore) {
	            beforeCount = -1;
	        }
	        try {
	            muPdfController.addInkAnnotation(mPageNumber, sanitized);
	        } catch (Throwable t) {
	            android.util.Log.e(TAG, "Failed to add ink annotation", t);
	            return false;
	        }

	        // Like InkController: refresh appearance streams and force full redraw.
	        try { muPdfController.refreshAnnotationAppearance(mPageNumber); } catch (Throwable ignore) {}

	        Annotation[] afterAnnots = null;
	        try { afterAnnots = muPdfController.annotations(mPageNumber); } catch (Throwable ignore) { afterAnnots = null; }

	        boolean added = true;
	        if (beforeCount >= 0 && afterAnnots != null && afterAnnots.length <= beforeCount) {
	            added = false;
	        }

	        int matchIndex = findMatchingNewInkAnnotationIndex(afterAnnots, sanitized, beforeObjectIds);
	        if (matchIndex < 0) {
	            matchIndex = findMostRecentNewInkAnnotationIndex(afterAnnots, beforeObjectIds);
	        }
	        if (matchIndex >= 0) {
	            added = true;
	            try {
	                Annotation match = afterAnnots[matchIndex];
	                if (match != null) {
	                    long obj = match.objectNumber;
	                    selectionManager.select(matchIndex, obj, new RectF(match), selectionUiBridge.selectionBoxHost());
	                    invalidateOverlay();
	                }
	            } catch (Throwable ignore) {
	            }
	        } else if (afterAnnots != null && afterAnnots.length > 0) {
	            // Best-effort: if we couldn't match arcs precisely, still keep the UI stable by
	            // requesting a redraw. Selection is left unchanged in this fallback.
	        }

	        // Prevent a "blink" after the placement overlay clears: show a short-lived overlay preview
	        // while MuPDF re-renders the page with the newly committed ink annotation.
	        try {
	            RectF previewBounds = null;
	            if (matchIndex >= 0 && afterAnnots != null && matchIndex < afterAnnots.length) {
	                Annotation a = afterAnnots[matchIndex];
	                if (a != null) previewBounds = new RectF(a);
	            }
	            if (previewBounds == null) previewBounds = boundsForInkArcsOrNull(sanitized);
	            if (previewBounds != null) {
	                setInkDragPreviewOverlay(new org.opendroidpdf.app.overlay.InkDragPreviewOverlay(
	                        previewBounds,
	                        previewBounds,
	                        sanitized,
	                        0xFF000000,
	                        2.5f));
	                final long token = android.os.SystemClock.uptimeMillis();
	                inkCommitPreviewToken = token;
	                postDelayed(() -> {
	                    if (inkCommitPreviewToken != token) return;
	                    try { setInkDragPreviewOverlay(null); } catch (Throwable ignore2) {}
	                }, 650L);
	            }
	        } catch (Throwable ignore) {
	        }

	        requestFullRedrawAfterNextAnnotationLoad();
	        discardRenderedPage();
	        loadAnnotations();

	        if (!added && beforeCount >= 0 && afterAnnots != null) {
	            android.util.Log.e(TAG, "Ink commit may have failed (page=" + mPageNumber + " before=" + beforeCount + " after=" + afterAnnots.length + ")");
	        }

	        // Only record an undo snapshot when we have strong evidence the ink exists, otherwise we risk
	        // matching/deleting a different ink annotation later.
	        if (added) {
	            try { inkController.undo().recordCommittedInkForUndo(sanitized); } catch (Throwable ignore) {}
	        }
	        try { inkController.refreshUndoState(); } catch (Throwable ignore) {}
	        return added;
	    }

	    /**
	     * Ensures "Show comments" is enabled for interactive annotation flows.
	     *
	     * <p>Fill &amp; Sign (signatures/initials) commits ink annotations. If annotation rendering is
	     * disabled, the in-progress overlay will disappear on commit, which looks like the signature
	     * was lost. This method flips both the native render flag and the reader/page overlay flags.</p>
	     */
		    public void ensureCommentsVisibleForEditing() {
		        try {
		            if (muPdfController != null) {
		                try { muPdfController.rawRepository().setAnnotationRenderingEnabled(true); } catch (Throwable ignore) {}
		            }
	        } catch (Throwable ignore) {
	        }

	        // Enable the reader-level flag so hit-testing and overlays stay consistent across pages.
	        try {
	            ViewParent p = getParent();
	            while (p != null) {
	                if (p instanceof MuPDFReaderView) {
	                    try { ((MuPDFReaderView) p).setCommentsVisible(true); } catch (Throwable ignore) {}
	                    break;
	                }
	                p = p.getParent();
	            }
	        } catch (Throwable ignore) {
	        }

	        // Also enable the local page overlay flag (in case the parent traversal fails).
		        try { setCommentsVisible(true); } catch (Throwable ignore) {}
		    }

		    /**
		     * Enables/disables native rendering of embedded PDF annotation appearances.
		     *
		     * <p>Used by direct-manipulation gesture previews to temporarily suppress the original
		     * annotation appearance so the overlay preview does not "ghost" beneath it.</p>
		     */
			    public void setEmbeddedAnnotationRenderingEnabled(boolean enabled) {
			        try {
			            if (muPdfController != null) {
			                try { muPdfController.rawRepository().setAnnotationRenderingEnabled(enabled); } catch (Throwable ignore) {}
			            }
			        } catch (Throwable ignore) {
			        }
			    }

			    /**
			     * Builds a best-effort overlay preview style for an embedded FreeText annotation.
			     *
			     * <p>This is used while moving/resizing a text annotation: the PDF re-render is committed
			     * on ACTION_UP, so we keep the text visible by drawing it in the page overlay.</p>
			     */
			    @Nullable
			    public TextDragPreviewOverlay embeddedFreeTextDragPreviewOverlayOrNull(long objectNumber, @Nullable String text) {
			        if (text == null) return null;
			        String trimmed = text.trim();
			        if (trimmed.isEmpty()) return null;

			        int color = 0xFF111111;
			        float fontPt = 12.0f;
			        int fontFamily = TextFontFamily.SANS;
			        int styleFlags = 0;
			        int align = 0;

			        final MuPdfController controller = muPdfController;
			        if (controller != null && objectNumber > 0L) {
			            try { fontPt = controller.rawRepository().getFreeTextFontSizeByObjectNumber(mPageNumber, objectNumber); } catch (Throwable ignore) { fontPt = 12.0f; }
			            try { fontFamily = controller.rawRepository().getFreeTextFontFamilyByObjectNumber(mPageNumber, objectNumber); } catch (Throwable ignore) { fontFamily = TextFontFamily.SANS; }
			            try { styleFlags = controller.rawRepository().getFreeTextStyleFlagsByObjectNumber(mPageNumber, objectNumber); } catch (Throwable ignore) { styleFlags = 0; }
			            try { align = controller.rawRepository().getFreeTextAlignmentByObjectNumber(mPageNumber, objectNumber); } catch (Throwable ignore) { align = 0; }
			            try {
			                float[] rgb = controller.rawRepository().getFreeTextTextColorByObjectNumber(mPageNumber, objectNumber);
			                if (rgb != null && rgb.length >= 3) {
			                    int r = Math.round(Math.max(0f, Math.min(1f, rgb[0])) * 255f);
			                    int g = Math.round(Math.max(0f, Math.min(1f, rgb[1])) * 255f);
			                    int b = Math.round(Math.max(0f, Math.min(1f, rgb[2])) * 255f);
			                    color = 0xFF000000 | (r << 16) | (g << 8) | b;
			                }
			            } catch (Throwable ignore) {
			            }
			        }

			        int baseDpi = 160;
			        try { if (controller != null) baseDpi = controller.rawRepository().getBaseResolutionDpi(); } catch (Throwable ignore) { baseDpi = 160; }
			        float dpi = baseDpi > 0 ? (float) baseDpi : 160f;

			        float fontSizeDoc = fontPt * (dpi / 72f);
			        if (!Float.isFinite(fontSizeDoc) || fontSizeDoc <= 0f) {
			            fontSizeDoc = 12.0f * (dpi / 72f);
			        }

			        fontFamily = TextFontFamily.normalize(fontFamily);
			        align = Math.max(0, Math.min(2, align));

			        return new TextDragPreviewOverlay(trimmed, color, fontSizeDoc, fontFamily, styleFlags, align);
			    }

		    private static int findMatchingNewInkAnnotationIndex(@Nullable Annotation[] annotations,
		                                                         @NonNull PointF[][] committedArcs,
		                                                         @NonNull java.util.Set<Long> beforeObjectIds) {
	        if (annotations == null || annotations.length == 0) return -1;
	        int fallback = -1;
	        for (int i = annotations.length - 1; i >= 0; i--) {
	            Annotation a = annotations[i];
	            if (a == null || a.type != Annotation.Type.INK || a.arcs == null || a.arcs.length == 0) continue;
	            if (!arcsApproximatelyEqual(committedArcs, a.arcs)) continue;
	            long obj = a.objectNumber;
	            if (obj > 0L && !beforeObjectIds.contains(obj)) return i;
	            if (fallback < 0) fallback = i;
	        }
	        return fallback;
	    }

	    private static int findMostRecentNewInkAnnotationIndex(@Nullable Annotation[] annotations,
	                                                           @NonNull java.util.Set<Long> beforeObjectIds) {
	        if (annotations == null || annotations.length == 0) return -1;
	        for (int i = annotations.length - 1; i >= 0; i--) {
	            Annotation a = annotations[i];
	            if (a == null || a.type != Annotation.Type.INK) continue;
	            long obj = a.objectNumber;
	            if (obj > 0L && !beforeObjectIds.contains(obj)) return i;
	        }
	        return -1;
	    }

	    private static boolean arcsApproximatelyEqual(@Nullable PointF[][] expected, @Nullable PointF[][] actual) {
	        if (expected == null || actual == null) return false;
	        if (expected.length != actual.length) return false;
	        final float e = 5e-1f;
	        for (int i = 0; i < expected.length; i++) {
	            PointF[] ea = expected[i];
	            PointF[] aa = actual[i];
	            if (ea == null || aa == null) {
	                if (ea != aa) return false;
	                continue;
	            }
	            if (ea.length != aa.length) return false;
	            for (int j = 0; j < ea.length; j++) {
	                PointF ep = ea[j];
	                PointF ap = aa[j];
	                if (ep == null || ap == null) {
	                    if (ep != ap) return false;
	                    continue;
	                }
	                if (Math.abs(ep.x - ap.x) > e || Math.abs(ep.y - ap.y) > e) return false;
	            }
	        }
	        return true;
	    }

	    @Nullable
	    private static RectF boundsForInkArcsOrNull(@NonNull PointF[][] arcs) {
	        if (arcs == null || arcs.length == 0) return null;
	        float minX = Float.POSITIVE_INFINITY;
	        float minY = Float.POSITIVE_INFINITY;
	        float maxX = Float.NEGATIVE_INFINITY;
	        float maxY = Float.NEGATIVE_INFINITY;
	        for (PointF[] stroke : arcs) {
	            if (stroke == null) continue;
	            for (PointF p : stroke) {
	                if (p == null) continue;
	                if (!Float.isFinite(p.x) || !Float.isFinite(p.y)) continue;
	                if (p.x < minX) minX = p.x;
	                if (p.y < minY) minY = p.y;
	                if (p.x > maxX) maxX = p.x;
	                if (p.y > maxY) maxY = p.y;
	            }
	        }
	        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) return null;
	        if (maxX <= minX) maxX = minX + 0.001f;
	        if (maxY <= minY) maxY = minY + 0.001f;
	        return new RectF(minX, minY, maxX, maxY);
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
		        // Must be done after super.setPage() otherwise page number is wrong!
		        // Defer while scrubbing so page preview rendering can keep up with the thumb.
		        if (!isScrubbingNow()) {
		            loadAnnotations();
		        }
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
