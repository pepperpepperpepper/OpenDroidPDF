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
import org.opendroidpdf.app.drawing.InkController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.selection.PageSelectionCoordinator;
import org.opendroidpdf.app.selection.SelectionActionRouter;
import org.opendroidpdf.app.selection.SelectionPageModel;
import org.opendroidpdf.app.selection.SelectionUiBridge;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.widget.WidgetAreasLoader;
import org.opendroidpdf.widget.WidgetUiController;
import org.opendroidpdf.app.reader.ReaderComposition;
import org.opendroidpdf.app.reader.gesture.AnnotationHitHelper;
import org.opendroidpdf.app.reader.gesture.PageHitRouter;
import org.opendroidpdf.app.reader.gesture.PageTapHitRouter;
import org.opendroidpdf.app.reader.gesture.ReaderMode;

import androidx.annotation.Nullable;

import android.annotation.TargetApi;
import org.opendroidpdf.TextProcessor;
import android.content.ClipData;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import java.util.Objects;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import org.opendroidpdf.app.overlay.MuPdfPatchRenderer;


public class MuPDFPageView extends PageView implements MuPDFView, SelectionPageModel {
	private static final String TAG = "MuPDFPageView";
	
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
    private WidgetController.WidgetJob mPassClickJob;
	private RectF mWidgetAreas[];
    // Widget area loading now handled by WidgetAreasLoader
    private final WidgetUiController widgetUiController;
    private WidgetAreasLoader widgetAreasLoader;
	private Runnable changeReporter;
	    private final org.opendroidpdf.app.signature.SignatureFlowController signatureFlow;
		    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager;
		    private final SelectionUiBridge selectionUiBridge;
		    private final AnnotationHitHelper annotationHitHelper;
		    private final MuPdfPatchRenderer patchRenderer;

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
        org.opendroidpdf.app.signature.SignatureFlowController.FilePickerLauncher pickerLauncher =
                callback -> {
                    FilePicker picker = new FilePicker(mFilePickerSupport) {
                        @Override void onPick(Uri uri) { callback.onPick(uri); }
                    };
                    picker.pick();
                };
        signatureFlow = composition.newSignatureFlow(
                pickerLauncher,
                () -> { if (changeReporter != null) changeReporter.run(); });
        sidecarSession = composition.sidecarSession();
        if (sidecarSession != null) {
            setSidecarAnnotations(sidecarSession);
        }
        inkController = new InkController(new InkHost(), muPdfController, sidecarSession);
	        widgetUiController = composition.newWidgetUiController();
	        widgetAreasLoader = composition.newWidgetAreasLoader();
		        pageHitRouter = new PageHitRouter(new HitHost());
		        this.selectionManager = composition.selectionManager();
		        this.selectionUiBridge = new SelectionUiBridge(this, selectionManager);
		        annotationHitHelper = new AnnotationHitHelper(selectionUiBridge.selectionManager());
		        selectionRouter = new SelectionActionRouter(selectionUiBridge.selectionManager(), annotationUiController, selectionUiBridge.selectionRouterHost());

        sidecarSelectionController = new SidecarSelectionController(new SidecarSelectionController.Host() {
            @Override public SidecarAnnotationSession sidecarSessionOrNull() { return sidecarSession; }
            @Override public int pageNumber() { return mPageNumber; }
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

		        // Signature UI now handled by SignatureFlowController
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
        @Override public Annotation[] annotations() { return mAnnotations; }
        @Override public RectF[] widgetAreas() { return mWidgetAreas; }

        @Override public AnnotationHitHelper annotationHitHelper() { return annotationHitHelper; }
        @Override public WidgetController widgetController() { return widgetController; }
        @Override public void setWidgetJob(WidgetController.WidgetJob job) {
            if (mPassClickJob != null) mPassClickJob.cancel();
            mPassClickJob = job;
        }

        @Override public void deselectAnnotation() { MuPDFPageView.this.deselectAnnotation(); }
        @Override public void selectAnnotation(int index, RectF bounds) { selectionManager.select(index, bounds, selectionUiBridge.selectionBoxHost()); }
        @Override public void onTextAnnotationTapped(Annotation annotation) { forwardTextAnnotation(annotation); }

        @Override public void requestChangeReport() { if (changeReporter != null) changeReporter.run(); }
        @Override public void invokeTextDialog(String text) { MuPDFPageView.this.invokeTextDialog(text); }
        @Override public void invokeChoiceDialog(String[] options) { MuPDFPageView.this.invokeChoiceDialog(options); }
        @Override public void warnNoSignatureSupport() { MuPDFPageView.this.warnNoSignatureSupport(); }
        @Override public void invokeSigningDialog() { MuPDFPageView.this.invokeSigningDialog(); }
        @Override public void invokeSignatureCheckingDialog() { MuPDFPageView.this.invokeSignatureCheckingDialog(); }
    }

    // Signature flow moved to SignatureFlowController

    public LinkInfo hitLink(float x, float y) {
        return pageHitRouter.hitLink(x, y);
	}

    private void invokeTextDialog(String text) { widgetUiController.showTextDialog(text); }
    // Debug-only entry points used by DebugActionsController via MuPDFReaderView
    public void debugShowTextWidgetDialog() { widgetUiController.showTextDialog(""); }
    public void debugShowChoiceWidgetDialog() { widgetUiController.showChoiceDialog(new String[]{"One","Two","Three"}); }

    private void invokeChoiceDialog(final String [] options) { widgetUiController.showChoiceDialog(options); }

    private void invokeSignatureCheckingDialog() { signatureFlow.checkFocusedSignature(); }

    private void invokeSigningDialog() { signatureFlow.showSigningDialog(); }

    private void warnNoSignatureSupport() { signatureFlow.showNoSignatureSupport(); }

    private void forwardTextAnnotation(Annotation annotation) {
        if (composition != null) {
            composition.textAnnotationRequester().requestTextAnnotationFromUserInput(annotation);
        }
    }

    public void setChangeReporter(Runnable reporter) {
        changeReporter = reporter;
        widgetUiController.setChangeReporter(() -> { if (changeReporter != null) changeReporter.run(); });
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

    @Override
    public void deleteSelectedAnnotation() {
        selectionCoordinator.deleteSelectedAnnotation();
    }

    public void editSelectedAnnotation() {
        selectionCoordinator.editSelectedAnnotation();
    }

    public Annotation.Type selectedAnnotationType() { return selectionRouter.selectedAnnotationType(); }
    public boolean selectedAnnotationIsEditable() {
        return selectionCoordinator.selectedAnnotationIsEditable();
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
    public void undoDraw() { inkController.undoDraw(); }

    @Override
	    public boolean canUndo() { return inkController.canUndo(); }

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

	@Override
	public void setPage(final int page, PointF size) {
        sidecarSelectionController.clearSelection();
        inkController.resetEraserSession();
        inkController.clear();
        widgetUiController.setPageNumber(page);

        widgetAreasLoader.load(page, new WidgetAreasCallback() {
            @Override public void onResult(RectF[] areas) { mWidgetAreas = areas; }
        });

		super.setPage(page, size);
        loadAnnotations();//Must be done after super.setPage() otherwise page number is wrong!
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
        if (mPassClickJob != null) {
            mPassClickJob.cancel();
            mPassClickJob = null;
        }

        if (widgetAreasLoader != null) widgetAreasLoader.release();

        widgetUiController.release();

        // Release signature controller jobs
        signatureFlow.release();

        if (annotationUiController != null) annotationUiController.release();

		super.releaseResources();
	}
}
