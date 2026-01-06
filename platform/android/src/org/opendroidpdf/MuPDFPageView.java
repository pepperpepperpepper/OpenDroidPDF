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
import org.opendroidpdf.app.annotation.FreeTextBoundsFitter;
import org.opendroidpdf.app.annotation.TextAnnotationQuadPoints;
import org.opendroidpdf.app.annotation.InkUndoController;
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
import android.graphics.Rect;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import java.util.Objects;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import org.opendroidpdf.app.overlay.MuPdfPatchRenderer;
import org.opendroidpdf.app.widget.WidgetUiBridge;
import org.opendroidpdf.app.sidecar.model.SidecarNote;
import java.util.List;


public class MuPDFPageView extends PageView implements MuPDFView, SelectionPageModel {
	private static final String TAG = "MuPDFPageView";

    // When true, show corner resize handles for the currently selected text annotation.
    // Default is false so accidental resizes are less likely; users can explicitly enable via
    // a toolbar action or a long-press on the selected box.
    private boolean textResizeHandlesEnabled = false;
    @Nullable private String lastSelectionKey;
	
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

    @Nullable private EditText inlineWidgetEditor;
    @Nullable private Rect inlineWidgetEditorBoundsPx;
    private final WidgetUiBridge.InlineTextEditorHost inlineTextEditorHost =
            new WidgetUiBridge.InlineTextEditorHost() {
                @Override public void showInlineTextEditor(EditText editor, Rect boundsPx) {
                    if (editor == null || boundsPx == null) return;
                    try {
                        android.view.ViewParent p = editor.getParent();
                        if (p instanceof ViewGroup && p != MuPDFPageView.this) {
                            ((ViewGroup) p).removeView(editor);
                        }
                    } catch (Throwable ignore) {
                    }

                    inlineWidgetEditor = editor;
                    inlineWidgetEditorBoundsPx = new Rect(boundsPx);
                    try {
                        if (editor.getParent() != MuPDFPageView.this) {
                            MuPDFPageView.this.addView(editor, new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT));
                        }
                        editor.bringToFront();
                        editor.setVisibility(View.VISIBLE);
                    } catch (Throwable ignore) {
                    }

                    try { MuPDFPageView.this.requestLayout(); } catch (Throwable ignore) {}
                }

                @Override public void hideInlineTextEditor(EditText editor) {
                    if (editor == null) return;
                    try {
                        if (editor.getParent() == MuPDFPageView.this) {
                            MuPDFPageView.this.removeView(editor);
                        } else {
                            android.view.ViewParent p = editor.getParent();
                            if (p instanceof ViewGroup) ((ViewGroup) p).removeView(editor);
                        }
                    } catch (Throwable ignore) {
                    }
                    if (inlineWidgetEditor == editor) {
                        inlineWidgetEditor = null;
                        inlineWidgetEditorBoundsPx = null;
                    }
                    try { MuPDFPageView.this.requestLayout(); } catch (Throwable ignore) {}
                }
            };
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

    @Nullable
    private String currentSelectionKeyOrNull() {
        try {
            SidecarSelectionController.Selection sel = selectedSidecarSelectionOrNull();
            if (sel != null && sel.kind == SidecarSelectionController.Kind.NOTE
                    && sel.id != null && !sel.id.trim().isEmpty()) {
                return "sidecar:" + sel.id;
            }
        } catch (Throwable ignore) {
        }
        try {
            long obj = selectionManager != null ? selectionManager.selectedObjectNumber() : -1L;
            if (obj > 0L) return "obj:" + obj;
        } catch (Throwable ignore) {
        }
        try {
            int idx = selectionManager != null ? selectionManager.selectedIndex() : -1;
            if (idx >= 0) return "idx:" + idx;
        } catch (Throwable ignore) {
        }
        return null;
    }

    @Override
    /* package */ void setItemSelectBox(RectF rect) {
        // Reset resize-handles whenever the selection changes (including to "no selection").
        String key = currentSelectionKeyOrNull();
        if (!Objects.equals(key, lastSelectionKey)) {
            textResizeHandlesEnabled = false;
            lastSelectionKey = key;
        }
        super.setItemSelectBox(rect);
    }

    /** Whether corner resize handles are enabled for the currently selected text annotation. */
    public boolean textResizeHandlesEnabled() {
        return textResizeHandlesEnabled;
    }

    /** Enables/disables corner resize handles for the current selection (if applicable). */
    public boolean setTextResizeHandlesEnabled(boolean enabled) {
        if (enabled == textResizeHandlesEnabled) return true;
        if (enabled && !hasSelectedTextAnnotation()) return false;
        textResizeHandlesEnabled = enabled;
        invalidateOverlay();
        return true;
    }

    /** Toggles corner resize handles for the current selection (if applicable). */
    public boolean toggleTextResizeHandlesEnabled() {
        return setTextResizeHandlesEnabled(!textResizeHandlesEnabled);
    }

    private boolean hasSelectedTextAnnotation() {
        try {
            if (sidecarSession != null) {
                SidecarSelectionController.Selection sel = selectedSidecarSelectionOrNull();
                return sel != null && sel.kind == SidecarSelectionController.Kind.NOTE;
            }
        } catch (Throwable ignore) {
        }
        Annotation.Type selectedType = null;
        try { selectedType = selectedAnnotationType(); } catch (Throwable ignore) { selectedType = null; }
        return selectedType == Annotation.Type.FREETEXT || selectedType == Annotation.Type.TEXT;
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

        @Override public void requestChangeReport() {
            try {
                if (muPdfController != null) muPdfController.markDocumentDirty();
            } catch (Throwable ignore) {
            }
            if (changeReporter != null) changeReporter.run();
        }
        @Override public void invokeTextDialog(String text, float docRelX, float docRelY) {
            MuPDFPageView.this.invokeTextDialog(text, docRelX, docRelY);
        }
        @Override public void invokeChoiceDialog(String[] options, String[] selected, boolean multiSelect, boolean editable, float docRelX, float docRelY) {
            MuPDFPageView.this.invokeChoiceDialog(options, selected, multiSelect, editable, docRelX, docRelY);
        }
        @Override public void warnNoSignatureSupport() { MuPDFPageView.this.warnNoSignatureSupport(); }
        @Override public void invokeSigningDialog() { MuPDFPageView.this.invokeSigningDialog(); }
        @Override public void invokeSignatureCheckingDialog() { MuPDFPageView.this.invokeSignatureCheckingDialog(); }
    }

    // Signature flow moved to SignatureFlowController

    public LinkInfo hitLink(float x, float y) {
        return pageHitRouter.hitLink(x, y);
	}

    private void invokeTextDialog(String text, float docRelX, float docRelY) {
        RectF hit = widgetAreaAt(docRelX, docRelY);
        Rect boundsPx = hit != null ? widgetBoundsDocToViewPx(hit) : null;
        if (boundsPx != null) {
            widgetUiController.showInlineTextEditor(inlineTextEditorHost, text, boundsPx, docRelX, docRelY);
            return;
        }
        widgetUiController.showTextDialog(text, docRelX, docRelY);
    }
    // Debug-only entry points used by DebugActionsController via MuPDFReaderView
    public void debugShowTextWidgetDialog() { widgetUiController.showTextDialog("", 0f, 0f); }
    public void debugShowChoiceWidgetDialog() {
        widgetUiController.showChoiceDialog(new String[]{"One","Two","Three"}, new String[]{"Two"}, false, false, 0f, 0f);
    }

    private void invokeChoiceDialog(final String [] options,
                                    final String[] selected,
                                    boolean multiSelect,
                                    boolean editable,
                                    float docRelX,
                                    float docRelY) {
        widgetUiController.showChoiceDialog(options, selected, multiSelect, editable, docRelX, docRelY);
    }

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
        try {
            widgetUiController.setFieldNavigationRequester(requester);
        } catch (Throwable ignore) {
        }
    }

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
    public SidecarSelectionController.Selection selectedSidecarSelectionOrNull() {
        return sidecarSelectionController != null ? sidecarSelectionController.selectionOrNull() : null;
    }

    @Nullable
    public SidecarNote sidecarNoteById(@NonNull String noteId) {
        SidecarAnnotationSession sidecar = sidecarSession;
        if (sidecar == null) return null;
        if (noteId == null || noteId.trim().isEmpty()) return null;
        try {
            List<SidecarNote> notes = sidecar.notesForPage(mPageNumber);
            if (notes == null || notes.isEmpty()) return null;
            for (SidecarNote n : notes) {
                if (n != null && noteId.equals(n.id)) return n;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    @Nullable
    public String sidecarNoteTextById(@NonNull String noteId) {
        SidecarNote note = sidecarNoteById(noteId);
        return note != null ? note.text : null;
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
    public boolean commitTextAnnotationRectByObjectNumber(long objectId, @NonNull RectF boundsDoc, boolean markUserResized) {
        if (sidecarSession != null) return false;
        if (objectId <= 0L || boundsDoc == null) return false;

        RectF normalized = normalizeTextAnnotationBoundsForCommit(boundsDoc);
        if (normalized == null) return false;

        muPdfController.rawRepository().updateAnnotationRectByObjectNumber(
                mPageNumber,
                objectId,
                normalized.left,
                normalized.top,
                normalized.right,
                normalized.bottom);
        if (markUserResized) {
            try {
                muPdfController.rawRepository().setFreeTextUserResizedByObjectNumber(mPageNumber, objectId, true);
            } catch (Throwable ignore) {
            }
        }
        muPdfController.markDocumentDirty();

        requestFullRedrawAfterNextAnnotationLoad();
        discardRenderedPage();
        loadAnnotations();

        RectF updated = new RectF(normalized);
        try {
            selectionManager.selectByObjectNumber(objectId, updated, selectionUiBridge.selectionBoxHost());
        } catch (Throwable ignore) {
            setAnnotationSelectionBox(updated);
        }
	        return true;
	    }

    @Nullable
    private RectF normalizeTextAnnotationBoundsForCommit(@NonNull RectF boundsDoc) {
        if (boundsDoc == null) return null;
        float scale = getScale();
        if (scale <= 0f) return null;
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

        if (right <= left || bottom <= top) return null;
        return new RectF(left, top, right, bottom);
    }

    public boolean commitSidecarNoteBounds(@NonNull String noteId, @NonNull RectF boundsDoc) {
        return commitSidecarNoteBounds(noteId, boundsDoc, false);
    }

    public boolean commitSidecarNoteBounds(@NonNull String noteId, @NonNull RectF boundsDoc, boolean markUserResized) {
        SidecarAnnotationSession sidecar = sidecarSession;
        if (sidecar == null) return false;
        if (noteId == null || noteId.trim().isEmpty()) return false;
        if (boundsDoc == null) return false;

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteBounds(mPageNumber, noteId, boundsDoc, markUserResized);
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Failed to update sidecar note bounds", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        try { sidecarSelectionController.updateSelectionBounds(noteId, updated.bounds); } catch (Throwable ignore) {}
        try { invalidateOverlay(); } catch (Throwable ignore) {}
        try { inkController.refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    public boolean updateSelectedSidecarNoteText(@Nullable String text) {
        SidecarAnnotationSession sidecar = sidecarSession;
        if (sidecar == null) return false;

        SidecarSelectionController.Selection sel = sidecarSelectionController.selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        if (sel.id == null || sel.id.trim().isEmpty()) return false;

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteText(mPageNumber, sel.id, text);
        } catch (Throwable t) {
            android.util.Log.e(TAG, "Failed to update sidecar note text", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        // Acrobat-ish behavior: auto-fit/grow the sidecar note bounds as text is edited until the
        // user explicitly resizes the box (then respect width and only grow height).
        RectF desiredBoundsDoc = computeAutoFitBoundsForSidecarNoteTextUpdate(updated, text);
        if (desiredBoundsDoc != null) {
            try {
                SidecarNote fitted = sidecar.updateNoteBounds(mPageNumber, sel.id, desiredBoundsDoc, false);
                if (fitted != null && fitted.bounds != null) {
                    updated = fitted;
                }
            } catch (Throwable t) {
                android.util.Log.e(TAG, "Failed to auto-fit sidecar note bounds after edit", t);
            }
        }

        try { sidecarSelectionController.updateSelectionBounds(sel.id, updated.bounds); } catch (Throwable ignore) {}
        try { invalidateOverlay(); } catch (Throwable ignore) {}
        try { inkController.refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    @Nullable
    private RectF computeAutoFitBoundsForSidecarNoteTextUpdate(@NonNull SidecarNote note, @Nullable String nextText) {
        if (sidecarSession == null) return null;
        if (note == null || note.bounds == null) return null;
        if (nextText == null || nextText.trim().isEmpty()) return null;
        float scale = getScale();
        if (scale <= 0f) return null;
        float pageDocWidth = getWidth() / scale;
        float pageDocHeight = getHeight() / scale;
        if (pageDocWidth <= 0f || pageDocHeight <= 0f) return null;
        boolean allowWidthGrow = !note.userResized;
        // Sidecar notes store font sizes in doc units already; use a base dpi of 72 so the
        // FreeText fitter's pt->doc conversion becomes a no-op.
        return org.opendroidpdf.app.annotation.FreeTextBoundsFitter.compute(
                getResources(),
                scale,
                pageDocWidth,
                pageDocHeight,
                note.bounds,
                nextText,
                note.fontSize,
                72,
                allowWidthGrow,
                false);
    }

	    /** Applies the requested style (font size + palette color) to the selected text annotation. */
	    public boolean applyTextStyleToSelectedTextAnnotation(float fontSize, int colorIndex) {
	        if (sidecarSession != null) {
	            SidecarSelectionController.Selection sel = sidecarSelectionController.selectionOrNull();
	            if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
	            if (sel.id == null || sel.id.trim().isEmpty()) return false;

	            SidecarNote updated;
	            try {
	                updated = sidecarSession.updateNoteStyle(mPageNumber, sel.id, ColorPalette.getHex(colorIndex), fontSize);
	            } catch (Throwable t) {
	                android.util.Log.e(TAG, "Failed to update sidecar note style", t);
	                return false;
	            }
	            if (updated == null || updated.bounds == null) return false;

	            try { sidecarSelectionController.updateSelectionBounds(sel.id, updated.bounds); } catch (Throwable ignore) {}
	            try { invalidateOverlay(); } catch (Throwable ignore) {}
	            try { inkController.refreshUndoState(); } catch (Throwable ignore) {}
	            return true;
	        }

	        Annotation.Type selectedType = selectedAnnotationType();
	        if (selectedType != Annotation.Type.FREETEXT) return false;

	        Annotation annot = selectedEmbeddedAnnotationOrNull();
	        if (annot == null) return false;
	        long objectId = annot.objectNumber;
	        if (objectId <= 0L) return false;

	        float r = ColorPalette.getR(colorIndex);
	        float g = ColorPalette.getG(colorIndex);
	        float b = ColorPalette.getB(colorIndex);

	        muPdfController.rawRepository().updateFreeTextStyleByObjectNumber(mPageNumber, objectId, fontSize, r, g, b);
	        muPdfController.markDocumentDirty();

	        requestFullRedrawAfterNextAnnotationLoad();
	        discardRenderedPage();
	        loadAnnotations();
	        invalidateOverlay();
	        return true;
	    }

	    /** Returns the current justification (0=left, 1=center, 2=right) for the selected FreeText box. */
	    public int selectedTextAnnotationAlignmentOrDefault() {
	        if (sidecarSession != null) return 0;
	        Annotation annot = selectedEmbeddedAnnotationOrNull();
	        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return 0;
	        try {
	            int q = muPdfController.rawRepository().getFreeTextAlignmentByObjectNumber(mPageNumber, annot.objectNumber);
	            return Math.max(0, Math.min(2, q));
	        } catch (Throwable ignore) {
	            return 0;
	        }
	    }

	    /** Returns the current font size (pt) for the selected FreeText box, or {@code fallbackPt}. */
	    public float selectedTextAnnotationFontSizeOrDefault(float fallbackPt) {
	        if (sidecarSession != null) return fallbackPt;
	        Annotation annot = selectedEmbeddedAnnotationOrNull();
	        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallbackPt;
	        try {
	            float pt = muPdfController.rawRepository().getFreeTextFontSizeByObjectNumber(mPageNumber, annot.objectNumber);
	            if (!Float.isNaN(pt) && !Float.isInfinite(pt) && pt > 0.0f) return pt;
	        } catch (Throwable ignore) {
	        }
	        return fallbackPt;
	    }

	    /** Applies justification (0=left, 1=center, 2=right) to the selected FreeText box. */
	    public boolean applyTextAlignmentToSelectedTextAnnotation(int alignment) {
	        if (sidecarSession != null) return false;
	        Annotation annot = selectedEmbeddedAnnotationOrNull();
	        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return false;
	        long objectId = annot.objectNumber;
	        alignment = Math.max(0, Math.min(2, alignment));

	        try {
	            muPdfController.rawRepository().updateFreeTextAlignmentByObjectNumber(mPageNumber, objectId, alignment);
	            muPdfController.markDocumentDirty();

	            requestFullRedrawAfterNextAnnotationLoad();
	            discardRenderedPage();
	            loadAnnotations();
	            invalidateOverlay();

	            // Best-effort: keep selection stable.
	            try { selectionManager.selectByObjectNumber(objectId, new RectF(annot), selectionUiBridge.selectionBoxHost()); } catch (Throwable ignore) {}
	            return true;
	        } catch (Throwable t) {
	            android.util.Log.e(TAG, "Failed to update FreeText alignment", t);
	            return false;
	        }
	    }

	    /** Tightens the selected FreeText bounds to its current content (Acrobat-ish). */
	    public boolean fitSelectedTextAnnotationToText() {
	        if (sidecarSession != null) return false;
	        Annotation annot = selectedEmbeddedAnnotationOrNull();
	        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return false;
	        String text = annot.text;
	        if (text == null || text.trim().isEmpty()) return false;

	        float scale = getScale();
	        if (scale <= 0f) return false;
	        float docW = getWidth() / scale;
	        float docH = getHeight() / scale;

	        float fontSizePt = 12.0f;
	        int baseDpi = 160;
	        try { fontSizePt = muPdfController.rawRepository().getFreeTextFontSizeByObjectNumber(mPageNumber, annot.objectNumber); } catch (Throwable ignore) {}
	        try { baseDpi = muPdfController.rawRepository().getBaseResolutionDpi(); } catch (Throwable ignore) {}

	        RectF fitted = FreeTextBoundsFitter.compute(
	                getResources(),
	                scale,
	                docW,
	                docH,
	                new RectF(annot),
	                text,
	                fontSizePt,
	                baseDpi,
	                false,
	                true);
	        if (fitted == null) return false;
	        return commitTextAnnotationRectByObjectNumber(annot.objectNumber, fitted, true);
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
        // Show handles for text boxes that support direct manipulation:
        // - embedded PDF FreeText/Text
        // - sidecar notes (EPUB / read-only PDFs)
        if (sidecarSession != null) {
            SidecarSelectionController.Selection sel = sidecarSelectionController.selectionOrNull();
            return sel != null && sel.kind == SidecarSelectionController.Kind.NOTE;
        }

        Annotation.Type selectedType = null;
        try { selectedType = selectedAnnotationType(); } catch (Throwable ignore) { selectedType = null; }
        return selectedType == Annotation.Type.FREETEXT || selectedType == Annotation.Type.TEXT;
    }

    @Override
    protected boolean showItemResizeHandles() {
        // Resize handles are an explicit mode; keep them hidden by default to avoid accidental resizes.
        return textResizeHandlesEnabled && showItemSelectionHandles();
    }

    @Override
    @Nullable
    protected RectF[] widgetAreasForOverlay() {
        return mWidgetAreas;
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
		if (objectNumber < 0) return;
		if (sidecarSession != null) return;
		// Like add: FreeText updates can be missed by incremental updatePage() paths.
		// Force a full draw on the next annotation reload so updated text is visible.
		requestFullRedrawAfterNextAnnotationLoad();
		final RectF desiredBoundsDoc = computeAutoFitBoundsForTextUpdate(objectNumber, text);
		annotationUiController.updateTextAnnotationContentsByObjectNumber(
				mPageNumber,
				objectNumber,
				text,
				() -> {
					// If this was a FreeText edit, auto-grow the bounds to fit content (unless the user
					// has explicitly resized the box, in which case only height may grow).
					if (desiredBoundsDoc != null) {
						RectF normalized = normalizeTextAnnotationBoundsForCommit(desiredBoundsDoc);
						if (normalized != null) {
							try {
								muPdfController.rawRepository().updateAnnotationRectByObjectNumber(
										mPageNumber,
										objectNumber,
										normalized.left,
										normalized.top,
										normalized.right,
										normalized.bottom);
								muPdfController.markDocumentDirty();
								try { selectionManager.selectByObjectNumber(objectNumber, normalized, selectionUiBridge.selectionBoxHost()); } catch (Throwable ignore) {}
							} catch (Throwable t) {
								android.util.Log.e(TAG, "Failed to auto-fit FreeText bounds after edit", t);
							}
						}
					}
					requestFullRedrawAfterNextAnnotationLoad();
					discardRenderedPage();
					loadAnnotations();
				});
		inkController.refreshUndoState();
	}

    @Nullable
    private RectF computeAutoFitBoundsForTextUpdate(long objectNumber, @Nullable String nextText) {
        if (sidecarSession != null) return null;
        if (objectNumber <= 0L) return null;
        if (nextText == null || nextText.trim().isEmpty()) return null;

        Annotation target = null;
        Annotation[] annots = mAnnotations;
        if (annots != null) {
            for (Annotation a : annots) {
                if (a != null && a.objectNumber == objectNumber) {
                    target = a;
                    break;
                }
            }
        }
        if (target == null || target.type != Annotation.Type.FREETEXT) return null;

        float scale = getScale();
        if (scale <= 0f) return null;
        float docW = getWidth() / scale;
        float docH = getHeight() / scale;

        boolean userResized = true;
        float fontSizePt = 12.0f;
        int baseDpi = 160;
        try { userResized = muPdfController.rawRepository().getFreeTextUserResizedByObjectNumber(mPageNumber, objectNumber); } catch (Throwable ignore) { userResized = true; }
        try { fontSizePt = muPdfController.rawRepository().getFreeTextFontSizeByObjectNumber(mPageNumber, objectNumber); } catch (Throwable ignore) {}
        try { baseDpi = muPdfController.rawRepository().getBaseResolutionDpi(); } catch (Throwable ignore) {}

        return FreeTextBoundsFitter.compute(
                getResources(),
                scale,
                docW,
                docH,
                new RectF(target),
                nextText,
                fontSizePt,
                baseDpi,
                !userResized,
                false);
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
        widgetUiController.dismissInlineTextEditor();
        widgetUiController.setPageNumber(page);

        widgetAreasLoader.load(page, new WidgetAreasCallback() {
            @Override public void onResult(RectF[] areas) {
                mWidgetAreas = areas;
                invalidateOverlay();
            }
        });

		super.setPage(page, size);
        loadAnnotations();//Must be done after super.setPage() otherwise page number is wrong!
	}

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        EditText editor = inlineWidgetEditor;
        Rect bounds = inlineWidgetEditorBoundsPx;
        if (editor == null || bounds == null) return;
        if (editor.getParent() != this) return;
        int w = Math.max(1, bounds.width());
        int h = Math.max(1, bounds.height());
        try {
            editor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            editor.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
        } catch (Throwable ignore) {
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            if (ev != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                EditText editor = inlineWidgetEditor;
                if (editor != null && editor.getParent() == this) {
                    float x = ev.getX();
                    float y = ev.getY();
                    if (x < editor.getLeft() || x > editor.getRight() || y < editor.getTop() || y > editor.getBottom()) {
                        editor.clearFocus();
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return super.dispatchTouchEvent(ev);
    }

    @Nullable
    private RectF widgetAreaAt(float docRelX, float docRelY) {
        RectF[] areas = mWidgetAreas;
        if (areas == null) return null;
        for (RectF r : areas) {
            if (r != null && r.contains(docRelX, docRelY)) return r;
        }
        return null;
    }

    @Nullable
    private Rect widgetBoundsDocToViewPx(@NonNull RectF docBounds) {
        float scale = getScale();
        if (scale <= 0f) return null;

        int left = Math.round(docBounds.left * scale);
        int top = Math.round(docBounds.top * scale);
        int right = Math.round(docBounds.right * scale);
        int bottom = Math.round(docBounds.bottom * scale);

        float density = getResources() != null ? getResources().getDisplayMetrics().density : 1f;
        int pad = (int) (2f * density + 0.5f);
        left -= pad;
        top -= pad;
        right += pad;
        bottom += pad;

        int w = getWidth();
        int h = getHeight();
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(w, right);
        bottom = Math.min(h, bottom);

        int minH = (int) (48f * density + 0.5f);
        if (bottom - top < minH) {
            int cy = (top + bottom) / 2;
            top = cy - (minH / 2);
            bottom = top + minH;
            top = Math.max(0, top);
            bottom = Math.min(h, bottom);
        }

        if (right <= left || bottom <= top) return null;
        return new Rect(left, top, right, bottom);
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
