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
import org.opendroidpdf.app.overlay.ItemSelectionHandles;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

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
    private boolean pendingTextAnnotationMove;
    @Nullable private RectF pendingTextAnnotationMoveBoundsDoc;
    private long pendingTextAnnotationMoveObjectId;

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
        if (annotation == null) return;
        try {
            if (composition == null) return;
            composition.textAnnotationRequester().requestTextAnnotationFromUserInput(annotation);
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Failed to open text annotation editor", t);
        }
    }

    /**
     * Returns the currently selected embedded annotation (from MuPDF's annotation list) or null.
     *
     * <p>Sidecar selections are owned by {@link SidecarSelectionController} and are not returned here.</p>
     */
    @Nullable
    public Annotation selectedEmbeddedAnnotationOrNull() {
        if (sidecarSession != null) return null;
        Annotation[] annots = mAnnotations;
        if (annots == null || annots.length == 0) return null;

        // Prefer stable identity if available.
        long objectId = -1L;
        try { objectId = selectionManager.selectedObjectNumber(); } catch (Throwable ignore) { objectId = -1L; }
        if (objectId > 0L) {
            Annotation byId = findAnnotationByObjectNumber(annots, objectId);
            if (byId != null) return byId;
        }

        int idx = selectionManager.selectedIndex();
        if (idx < 0 || idx >= annots.length) return null;
        return annots[idx];
    }

    @Nullable
    private static Annotation findAnnotationByObjectNumber(@Nullable Annotation[] annots, long objectId) {
        if (annots == null || objectId <= 0L) return null;
        for (Annotation a : annots) {
            if (a != null && a.objectNumber == objectId) return a;
        }
        return null;
    }

    /** Updates the on-screen selection box for the currently selected annotation (doc-relative coords). */
    public void setAnnotationSelectionBox(@Nullable RectF rectDoc) {
        try {
            setItemSelectBox(rectDoc);
        } catch (Throwable ignore) {
        }
    }

    /**
     * Commits a new bounding rect for an embedded text annotation (FreeText/Text) by stable object id.
     *
     * <p>Used by gesture-based direct manipulation (drag to move/resize).</p>
     */
    public boolean commitTextAnnotationRectByObjectNumber(long objectId, @NonNull RectF boundsDoc) {
        if (sidecarSession != null) return false;
        if (objectId <= 0L || boundsDoc == null) return false;

        float scale = getScale();
        if (scale <= 0f) return false;
        final float docWidth = getWidth() / scale;
        final float docHeight = getHeight() / scale;

        float left = Math.min(boundsDoc.left, boundsDoc.right);
        float right = Math.max(boundsDoc.left, boundsDoc.right);
        float top = Math.min(boundsDoc.top, boundsDoc.bottom);
        float bottom = Math.max(boundsDoc.top, boundsDoc.bottom);

        // Enforce a minimum on-screen size so the box remains selectable.
        float minEdgeDoc = ItemSelectionHandles.minEdgePx(getResources()) / scale;
        if ((right - left) < minEdgeDoc) right = Math.min(docWidth, left + minEdgeDoc);
        if ((bottom - top) < minEdgeDoc) bottom = Math.min(docHeight, top + minEdgeDoc);

        // Clamp to doc bounds.
        left = Math.max(0f, Math.min(left, docWidth));
        right = Math.max(0f, Math.min(right, docWidth));
        top = Math.max(0f, Math.min(top, docHeight));
        bottom = Math.max(0f, Math.min(bottom, docHeight));

        if (right <= left || bottom <= top) return false;

        muPdfController.rawRepository().updateAnnotationRectByObjectNumber(mPageNumber, objectId, left, top, right, bottom);
        muPdfController.markDocumentDirty();

        requestFullRedrawAfterNextAnnotationLoad();
        discardRenderedPage();
        loadAnnotations();

        RectF updated = new RectF(left, top, right, bottom);
        try {
            selectionManager.selectByObjectNumber(objectId, updated, selectionUiBridge.selectionBoxHost());
        } catch (Throwable ignore) {
            setAnnotationSelectionBox(updated);
        }
        return true;
    }

    /** Arms a one-shot "tap-to-move" operation for the currently selected embedded text annotation. */
    public boolean beginMoveSelectedTextAnnotation() {
        cancelPendingTextAnnotationMove();
        if (sidecarSession != null) return false;

        Annotation.Type selectedType = selectedAnnotationType();
        if (selectedType != Annotation.Type.FREETEXT && selectedType != Annotation.Type.TEXT) return false;

        Annotation[] annots = mAnnotations;
        int idx = selectionManager.selectedIndex();
        if (annots == null || idx < 0 || idx >= annots.length) return false;
        Annotation annot = annots[idx];
        if (annot == null) return false;

        pendingTextAnnotationMove = true;
        pendingTextAnnotationMoveObjectId = annot.objectNumber;
        pendingTextAnnotationMoveBoundsDoc = new RectF(annot);
        return true;
    }

    private void cancelPendingTextAnnotationMove() {
        pendingTextAnnotationMove = false;
        pendingTextAnnotationMoveBoundsDoc = null;
        pendingTextAnnotationMoveObjectId = 0L;
    }

    private boolean tryMovePendingTextAnnotation(@Nullable MotionEvent e) {
        if (e == null) return false;
        if (sidecarSession != null) return false;
        RectF fromBounds = pendingTextAnnotationMoveBoundsDoc;
        if (fromBounds == null) return false;
        final long objectId = pendingTextAnnotationMoveObjectId;
        if (objectId <= 0L) return false;

        float scale = getScale();
        if (scale == 0f) return false;

        final float docWidth = getWidth() / scale;
        final float docHeight = getHeight() / scale;
        final float docRelX = (e.getX() - getLeft()) / scale;
        final float docRelY = (e.getY() - getTop()) / scale;

        float width = fromBounds.width();
        float height = fromBounds.height();
        if (width <= 0f || height <= 0f) return false;

        float left = docRelX - width * 0.5f;
        float top = docRelY - height * 0.5f;

        if (left < 0f) left = 0f;
        if (top < 0f) top = 0f;
        if (left + width > docWidth) left = Math.max(0f, docWidth - width);
        if (top + height > docHeight) top = Math.max(0f, docHeight - height);

        float right = left + width;
        float bottom = top + height;

        // Move in-place via MuPDF stable object id, then refresh caches + selection box.
        muPdfController.rawRepository().updateAnnotationRectByObjectNumber(mPageNumber, objectId, left, top, right, bottom);
        muPdfController.markDocumentDirty();

        requestFullRedrawAfterNextAnnotationLoad();
        discardRenderedPage();
        loadAnnotations();

        RectF movedTo = new RectF(left, top, right, bottom);
        try {
            selectionManager.selectByObjectNumber(objectId, movedTo, selectionUiBridge.selectionBoxHost());
        } catch (Throwable ignore) {
            try { setItemSelectBox(movedTo); } catch (Throwable ignore2) {}
        }
        invalidateOverlay();
        return true;
    }

    /** Applies the current pen (color/size) as the style for the selected embedded FreeText annotation. */
    public boolean applyPenStyleToSelectedTextAnnotation() {
        cancelPendingTextAnnotationMove();
        if (sidecarSession != null) return false;

        Annotation.Type selectedType = selectedAnnotationType();
        if (selectedType != Annotation.Type.FREETEXT) return false;

        Annotation annot = selectedEmbeddedAnnotationOrNull();
        if (annot == null) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;

        float fontSize = currentInkThickness();
        int colorInt = currentInkColor();
        float r = ((colorInt >> 16) & 0xFF) / 255f;
        float g = ((colorInt >> 8) & 0xFF) / 255f;
        float b = (colorInt & 0xFF) / 255f;

        muPdfController.rawRepository().updateFreeTextStyleByObjectNumber(mPageNumber, objectId, fontSize, r, g, b);
        muPdfController.markDocumentDirty();

        requestFullRedrawAfterNextAnnotationLoad();
        discardRenderedPage();
        loadAnnotations();
        invalidateOverlay();
        return true;
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
        cancelPendingTextAnnotationMove();
        selectionCoordinator.deleteSelectedAnnotation();
    }

    public void editSelectedAnnotation() {
        // Sidecar notes already own edit via SidecarSelectionController.
        try {
            if (sidecarSelectionController != null && sidecarSelectionController.editSelected()) return;
        } catch (Throwable ignore) {
        }

        // Embedded FreeText edits route through the shared TextAnnotationController (dialog + in-place update).
        Annotation.Type selectedType = null;
        try {
            selectedType = selectedAnnotationType();
        } catch (Throwable ignore) {
            selectedType = null;
        }
        if (selectedType == Annotation.Type.FREETEXT || selectedType == Annotation.Type.TEXT) {
            try {
                Annotation target = selectedEmbeddedAnnotationOrNull();
                if (target != null) {
                    forwardTextAnnotation(target);
                    return;
                }
            } catch (Throwable ignore) {
            }
        }

        selectionRouter.editSelectedAnnotation();
    }

    public Annotation.Type selectedAnnotationType() { return selectionRouter.selectedAnnotationType(); }
    public boolean selectedAnnotationIsEditable() {
        return selectionCoordinator.selectedAnnotationIsEditable();
    }

    @Override
    protected boolean showItemSelectionHandles() {
        // Only show handles for embedded text annotations that support drag move/resize.
        Annotation.Type selectedType = null;
        try {
            selectedType = selectedAnnotationType();
        } catch (Throwable ignore) {
            selectedType = null;
        }
        return selectedType == Annotation.Type.FREETEXT || selectedType == Annotation.Type.TEXT;
    }

    @Override
    protected void onAnnotationsLoaded(Annotation[] annotations) {
        super.onAnnotationsLoaded(annotations);
        if (sidecarSession != null) return;

        // If we have a stable object id selection, re-resolve it across reloads so "tap-to-edit"
        // and direct manipulation don't accidentally target a different annotation after a refresh.
        long objectId = -1L;
        try { objectId = selectionManager.selectedObjectNumber(); } catch (Throwable ignore) { objectId = -1L; }
        if (objectId > 0L) {
            int idx = -1;
            if (annotations != null) {
                for (int i = 0; i < annotations.length; i++) {
                    Annotation a = annotations[i];
                    if (a != null && a.objectNumber == objectId) {
                        idx = i;
                        break;
                    }
                }
            }
            if (idx >= 0 && annotations != null) {
                selectionManager.setSelectedIndex(idx);
                RectF bounds = new RectF(annotations[idx]);
                selectionManager.select(idx, objectId, bounds, selectionUiBridge.selectionBoxHost());
                invalidateOverlay();
            } else if (selectionManager.hasSelection()) {
                // Selected annotation disappeared (deleted) or could not be resolved.
                selectionManager.deselect(selectionUiBridge.selectionBoxHost());
                invalidateOverlay();
            }
            return;
        }

        // If selection is index-only, ensure it stays in-bounds after reloads.
        int idx = selectionManager.selectedIndex();
        if (idx >= 0) {
            if (annotations == null || idx >= annotations.length) {
                selectionManager.deselect(selectionUiBridge.selectionBoxHost());
                invalidateOverlay();
            } else {
                // Opportunistically capture a stable object id if it exists.
                long newId = annotations[idx] != null ? annotations[idx].objectNumber : -1L;
                if (newId > 0L) {
                    RectF bounds = new RectF(annotations[idx]);
                    selectionManager.select(idx, newId, bounds, selectionUiBridge.selectionBoxHost());
                    invalidateOverlay();
                }
            }
        }
    }

    public void deselectAnnotation() {
        cancelPendingTextAnnotationMove();
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

	public void addTextAnnotationFromUi(final Annotation annot) {
		addTextAnnotation(annot);
	}

	public void updateTextAnnotationContentsByObjectNumber(long objectNumber, String text) {
		if (objectNumber < 0) return;
		if (sidecarSession != null) return;
		// Like add: FreeText updates can be missed by incremental updatePage() paths.
		// Force a full draw on the next annotation reload so updated text is visible.
		requestFullRedrawAfterNextAnnotationLoad();
		annotationUiController.updateTextAnnotationContentsByObjectNumber(
				mPageNumber,
				objectNumber,
				text,
				() -> {
					requestFullRedrawAfterNextAnnotationLoad();
					discardRenderedPage();
					loadAnnotations();
				});
		inkController.refreshUndoState();
	}

	@Override
	public void setPage(final int page, PointF size) {
        cancelPendingTextAnnotationMove();
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
        if (pendingTextAnnotationMove) {
            pendingTextAnnotationMove = false;
            boolean moved = false;
            try {
                moved = tryMovePendingTextAnnotation(e);
            } catch (Throwable t) {
                android.util.Log.e(TAG, "Failed to move text annotation", t);
            } finally {
                pendingTextAnnotationMoveBoundsDoc = null;
                pendingTextAnnotationMoveObjectId = 0L;
            }
            return moved ? Hit.TextAnnotation : Hit.Nothing;
        }
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
