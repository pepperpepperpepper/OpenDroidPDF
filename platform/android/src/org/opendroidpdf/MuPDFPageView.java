package org.opendroidpdf;

import org.opendroidpdf.MuPDFCore.Cookie;
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
import org.opendroidpdf.app.annotation.InkUndoController;
import org.opendroidpdf.app.drawing.InkController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.widget.WidgetAreasLoader;
import org.opendroidpdf.SelectionActionRouter;
import org.opendroidpdf.widget.WidgetUiController;
import org.opendroidpdf.app.reader.ReaderComposition;

import androidx.annotation.Nullable;

import android.annotation.TargetApi;
import org.opendroidpdf.TextProcessor;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import java.util.Objects;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import org.opendroidpdf.BuildConfig;


public class MuPDFPageView extends PageView implements MuPDFView, SelectionPageModel {
private static final String TAG = "MuPDFPageView";

private final FilePicker.FilePickerSupport mFilePickerSupport;
private final MuPdfController muPdfController;
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
    private final org.opendroidpdf.AnnotationHitHelper annotationHitHelper;

public MuPDFPageView(Context context,
                     FilePicker.FilePickerSupport filePickerSupport,
                     MuPdfController controller,
                     ViewGroup parent,
                     ReaderComposition composition) {
        super(context, parent, new DocumentContentController(Objects.requireNonNull(controller, "MuPdfController required")));
		mFilePickerSupport = filePickerSupport;
		muPdfController = controller;
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
	        annotationHitHelper = new org.opendroidpdf.AnnotationHitHelper(selectionUiBridge.selectionManager());
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
	    @Override public TextWord[][] textLines() { return getText(); }

	    @Override public void setModeDrawing() {
	        // PageViews can be constructed before being attached to the ReaderView.
	        // Resolve the parent at call time to avoid NPEs during edit flows.
        MuPDFReaderView rv = mParent instanceof MuPDFReaderView ? (MuPDFReaderView) mParent : null;
        if (rv != null) rv.requestMode(MuPDFReaderView.Mode.Drawing);
    }

    @Override public void processSelectedText(TextProcessor processor) { super.processSelectedText(processor); }

    @Override public void setSelectionBox(RectF rect) { setItemSelectBox(rect); }
    @Override public void refreshUndoState() { inkController.refreshUndoState(); }

	    private class InkHost implements InkController.Host {
	        @Override public DrawingController drawingController() { return MuPDFPageView.this.getDrawingController(); }
	        @Override public void requestReaderErasingMode() {
                MuPDFReaderView rv = mParent instanceof MuPDFReaderView ? (MuPDFReaderView) mParent : null;
                if (rv != null) rv.requestMode(MuPDFReaderView.Mode.Erasing);
            }
	        @Override public int pageNumber() { return mPageNumber; }
        @Override public void requestFullRedraw() { requestFullRedrawAfterNextAnnotationLoad(); }
        @Override public void loadAnnotations() { MuPDFPageView.this.loadAnnotations(); }
        @Override public void discardRenderedPage() { MuPDFPageView.this.discardRenderedPage(); }
        @Override public void redraw(boolean updateHq) { MuPDFPageView.this.redraw(updateHq); }
        @Override public void invalidateOverlay() { MuPDFPageView.this.invalidateOverlay(); }
        @Override public float currentInkThickness() { return MuPDFPageView.this.currentInkThickness(); }
        @Override public int currentInkColor() { return MuPDFPageView.this.currentInkColor(); }
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
		float scale = getScale();
		float docRelX = (x - getLeft())/scale;
		float docRelY = (y - getTop())/scale;

		for (LinkInfo l: mLinks)
			if (l.rect.contains(docRelX, docRelY))
				return l;

		return null;
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
        ((MuPDFReaderView) mParent).addTextAnnotFromUserInput(annotation);
    }

    public void setChangeReporter(Runnable reporter) {
        changeReporter = reporter;
        widgetUiController.setChangeReporter(() -> { if (changeReporter != null) changeReporter.run(); });
    }

    // passClickEvent/clickWouldHit override below to include sidecar overlay hit-testing.
    


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
        super.startDraw(x, y);
        inkController.refreshUndoState();
    }

    @Override
    public void finishDraw() {
        super.finishDraw();
        inkController.refreshUndoState();
    }

    @Override
    public void cancelDraw() {
        super.cancelDraw();
        inkController.refreshUndoState();
    }

    @Override
    public void startErase(final float x, final float y) {
        try {
            inkController.onStartEraseGesture(x, y, getScale(), getLeft(), getTop());
        } catch (Throwable ignore) {
        }
        super.startErase(x, y);
        inkController.refreshUndoState();
    }

    @Override
    public void continueErase(final float x, final float y) {
        try {
            inkController.onContinueEraseGesture(x, y, getScale(), getLeft(), getTop());
        } catch (Throwable ignore) {
        }
        super.continueErase(x, y);
    }

    @Override
    public void finishErase(final float x, final float y) {
        super.finishErase(x, y);
        inkController.onFinishEraseGesture();
        inkController.refreshUndoState();
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


    private void drawPage(Bitmap bm, int sizeX, int sizeY,
                          int patchX, int patchY, int patchWidth, int patchHeight, MuPDFCore.Cookie cookie) {
        if (mPageNumber < 0) {
            Log.w(TAG, "drawPage() skipped invalid page=" + mPageNumber);
            return;
        }
        try {
            muPdfController.drawPage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
        } catch (Throwable t) {
            Log.e(TAG, "drawPage() failed page=" + mPageNumber + " area=" + patchWidth + "x" + patchHeight
                    + " view=" + sizeX + "x" + sizeY, t);
            throw t;
        }
    }

    // Wait (best-effort) for the asynchronous ink-commit task to finish so that
    // subsequent export/print includes the accepted stroke. Safe to call off the UI thread.
    public void awaitInkCommit(long timeoutMs) {
        // Ink commits run synchronously; retained for legacy callers that awaited AsyncTasks.
    }
    
    private void updatePage(Bitmap bm, int sizeX, int sizeY,
                            int patchX, int patchY, int patchWidth, int patchHeight, MuPDFCore.Cookie cookie) {
        if (mPageNumber < 0) {
            Log.w(TAG, "updatePage() skipped invalid page=" + mPageNumber);
            return;
        }
        try {
            muPdfController.updatePage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
        } catch (Throwable t) {
            Log.e(TAG, "updatePage() failed page=" + mPageNumber + " area=" + patchWidth + "x" + patchHeight
                    + " view=" + sizeX + "x" + sizeY, t);
            throw t;
        }
    }

	@Override
	protected CancellableTaskDefinition<PatchInfo, PatchInfo> getRenderTask(PatchInfo patchInfo) {
		return new MuPDFCancellableTaskDefinition<PatchInfo, PatchInfo>(muPdfController.rawRepository()) {
            @Override
			public PatchInfo doInBackground(MuPDFCore.Cookie cookie, PatchInfo... v) {
				PatchInfo patchInfo = v[0];
                if (mPageNumber < 0) {
                    Log.w(TAG, "render patch skipped: invalid page=" + mPageNumber);
                    return patchInfo;
                }
                Log.d(TAG, "render patch page=" + mPageNumber
                        + " complete=" + patchInfo.completeRedraw
                        + " view=" + patchInfo.viewArea.width() + "x" + patchInfo.viewArea.height()
                        + " patch=" + patchInfo.patchArea.width() + "x" + patchInfo.patchArea.height());
                if (patchInfo.viewArea.width() <= 0 || patchInfo.viewArea.height() <= 0
                        || patchInfo.patchArea.width() <= 0 || patchInfo.patchArea.height() <= 0) {
                    Log.w(TAG, "render patch skipped invalid dims page=" + mPageNumber
                            + " viewArea=" + patchInfo.viewArea + " patchArea=" + patchInfo.patchArea);
                }
					// Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
					// is not incremented when drawing.
					//Careful: We must not let the native code draw to a bitmap that is alreay set to the view. The view might redraw itself (this can even happen without draw() or onDraw() beeing called) and then immediately appear with the new content of the bitmap. This leads to flicker if the view would have to be moved before showing the new content. This is avoided by the ReaderView providing one of two bitmaps in a smart way such that v[0].patchBm is always set to the one not currently set.		
				if (patchInfo.completeRedraw) {
					patchInfo.patchBm.eraseColor(0xFFFFFFFF);
					drawPage(patchInfo.patchBm, patchInfo.viewArea.width(), patchInfo.viewArea.height(),
							 patchInfo.patchArea.left, patchInfo.patchArea.top,
							 patchInfo.patchArea.width(), patchInfo.patchArea.height(),
							 cookie);
				} else {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
						Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
						patchInfo.patchBm.eraseColor(0);
					}
					updatePage(patchInfo.patchBm, patchInfo.viewArea.width(), patchInfo.viewArea.height(),
							   patchInfo.patchArea.left, patchInfo.patchArea.top,
							   patchInfo.patchArea.width(), patchInfo.patchArea.height(),
							   cookie);
				}

                if (looksUniform(patchInfo.patchBm)) {
                    Log.w(TAG, "Rendered uniform patch page=" + mPageNumber
                            + " complete=" + patchInfo.completeRedraw
                            + " view=" + patchInfo.viewArea.width() + "x" + patchInfo.viewArea.height()
                            + " patch=" + patchInfo.patchArea.width() + "x" + patchInfo.patchArea.height());
                } else if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Patch ok page=" + mPageNumber
                            + " view=" + patchInfo.viewArea.width() + "x" + patchInfo.viewArea.height()
                            + " patch=" + patchInfo.patchArea.width() + "x" + patchInfo.patchArea.height());
                }
				return patchInfo;
			}			
		};
	}

    private boolean looksUniform(Bitmap bm) {
        if (bm == null) return false;
        int w = bm.getWidth();
        int h = bm.getHeight();
        if (w == 0 || h == 0) return false;
        int[] samples = new int[25];
        int idx = 0;
        for (int yi = 0; yi < 5; yi++) {
            for (int xi = 0; xi < 5; xi++) {
                int x = (int) ((xi + 0.5f) * w / 5f);
                int y = (int) ((yi + 0.5f) * h / 5f);
                samples[idx++] = bm.getPixel(Math.min(x, w - 1), Math.min(y, h - 1));
            }
        }
        int base = samples[0];
        final int tol = 3;
        for (int i = 1; i < samples.length; i++) {
            int c = samples[i];
            int dr = Math.abs(((c >> 16) & 0xFF) - ((base >> 16) & 0xFF));
            int dg = Math.abs(((c >> 8) & 0xFF) - ((base >> 8) & 0xFF));
            int db = Math.abs((c & 0xFF) - (base & 0xFF));
            if (dr > tol || dg > tol || db > tol) return false;
        }
        return true;
    }
	
    
	@Override
	protected LinkInfo[] getLinkInfo() {
		return muPdfController.links(mPageNumber);
	}

	@Override
	protected TextWord[][] getText() {
        TextWord[][] lines = muPdfController.textLines(mPageNumber);
        return lines != null ? lines : new TextWord[0][];
	}

	@Override
	protected Annotation[] getAnnotations() {
        return muPdfController.annotations(mPageNumber);
	}
    
	@Override
	protected void addMarkup(PointF[] quadPoints, Annotation.Type type) {
		muPdfController.addMarkupAnnotation(mPageNumber, quadPoints, type);
	}

	@Override
	protected void addTextAnnotation(final Annotation annot) {
        final PointF[] quadPoints;
        if (sidecarSession != null) {
            // Sidecar anchors live in the same doc-relative coordinate space as the overlay (top-left origin).
            quadPoints = new PointF[]{ new PointF(annot.left, annot.top), new PointF(annot.right, annot.bottom) };
        } else {
            // Embedded PDF annotations use PDF coordinates (bottom-left origin).
            PointF start = new PointF(annot.left, getHeight()/getScale()-annot.top);
            PointF end = new PointF(annot.right, getHeight()/getScale()-annot.bottom);
            quadPoints = new PointF[]{start, end};
        }
        annotationUiController.addTextAnnotation(
                mPageNumber,
                quadPoints,
                annot.text,
                this::loadAnnotations);
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
