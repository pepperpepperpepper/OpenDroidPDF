package org.opendroidpdf;

import org.opendroidpdf.MuPDFCore.Cookie;
import org.opendroidpdf.core.AnnotationCallback;
import org.opendroidpdf.core.AnnotationController;
import org.opendroidpdf.core.DocumentContentController;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.SignatureBooleanCallback;
import org.opendroidpdf.core.SignatureController;
import org.opendroidpdf.core.SignatureController.SignatureJob;
import org.opendroidpdf.core.SignatureStringCallback;
import org.opendroidpdf.core.WidgetBooleanCallback;
import org.opendroidpdf.core.WidgetCompletionCallback;
import org.opendroidpdf.core.WidgetController;
import org.opendroidpdf.core.WidgetAreasCallback;
import org.opendroidpdf.core.WidgetPassClickCallback;
import org.opendroidpdf.app.annotation.AnnotationUiController;
import org.opendroidpdf.app.annotation.InkUndoController;
import org.opendroidpdf.app.drawing.InkController;
import org.opendroidpdf.app.widget.WidgetAreasLoader;
import org.opendroidpdf.SelectionActionRouter;
import org.opendroidpdf.widget.WidgetUiController;

import android.annotation.TargetApi;
import org.opendroidpdf.TextProcessor;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import java.util.Objects;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.util.Log;
import java.util.ArrayDeque;
import java.util.ArrayList;


public class MuPDFPageView extends PageView implements MuPDFView {
private static final String TAG = "MuPDFPageView";
    private static final boolean LOG_UNDO = true; // temporary instrumentation

private final FilePicker.FilePickerSupport mFilePickerSupport;
private final MuPdfController muPdfController;
    private final AnnotationController annotationController;
    private final AnnotationUiController annotationUiController;
    private final InkController inkController;
    private final WidgetController widgetController;
    private final SignatureController signatureController;
    private final PageHitRouter pageHitRouter;
    private final SelectionActionRouter selectionRouter;
    private WidgetController.WidgetJob mPassClickJob;
	private RectF mWidgetAreas[];
    // Widget area loading now handled by WidgetAreasLoader
    private final WidgetUiController widgetUiController;
    private WidgetAreasLoader widgetAreasLoader;
	private Runnable changeReporter;
    private final org.opendroidpdf.app.signature.SignatureFlowController signatureFlow;
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager =
            new org.opendroidpdf.app.annotation.AnnotationSelectionManager();
    private final org.opendroidpdf.AnnotationHitHelper annotationHitHelper =
            new org.opendroidpdf.AnnotationHitHelper(selectionManager);
    
public MuPDFPageView(Context context, FilePicker.FilePickerSupport filePickerSupport, MuPdfController controller, ViewGroup parent) {
        super(context, parent, new DocumentContentController(Objects.requireNonNull(controller, "MuPdfController required")));
		mFilePickerSupport = filePickerSupport;
		muPdfController = controller;
        annotationController = new AnnotationController(muPdfController);
        annotationUiController = new AnnotationUiController(annotationController);
		widgetController = new WidgetController(muPdfController);
        signatureController = new SignatureController(muPdfController);
        signatureFlow = new org.opendroidpdf.app.signature.SignatureFlowController(
                context,
                signatureController,
                (cb) -> {
                    FilePicker picker = new FilePicker(mFilePickerSupport) {
                        @Override void onPick(Uri uri) { cb.onPick(uri); }
                    };
                    picker.pick();
                },
                () -> { if (changeReporter != null) changeReporter.run(); }
        );
        inkController = new InkController(new InkHost(), muPdfController);
        widgetUiController = new WidgetUiController(new org.opendroidpdf.app.widget.WidgetUiBridge(context, widgetController));
        widgetAreasLoader = new WidgetAreasLoader(widgetController);
        pageHitRouter = new PageHitRouter(new HitHost());
        selectionRouter = new SelectionActionRouter(selectionManager, annotationUiController, new SelectionHost());

        // Signature UI now handled by SignatureFlowController
	}

    private class InkHost implements InkController.Host {
        @Override public DrawingController drawingController() { return MuPDFPageView.this.getDrawingController(); }
        @Override public MuPDFReaderView parentReader() { return (MuPDFReaderView) mParent; }
        @Override public int pageNumber() { return mPageNumber; }
        @Override public void requestFullRedraw() { requestFullRedrawAfterNextAnnotationLoad(); }
        @Override public void loadAnnotations() { MuPDFPageView.this.loadAnnotations(); }
        @Override public void discardRenderedPage() { MuPDFPageView.this.discardRenderedPage(); }
        @Override public void redraw(boolean updateHq) { MuPDFPageView.this.redraw(updateHq); }
    }

    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager.Host selectionHost =
            new org.opendroidpdf.app.annotation.AnnotationSelectionManager.Host() {
                @Override public void setItemSelectBox(RectF rect) { MuPDFPageView.this.setItemSelectBox(rect); }
            };

    private class SelectionHost implements SelectionActionRouter.Host {
        @Override public Annotation[] annotations() { return mAnnotations; }
        @Override public int pageNumber() { return mPageNumber; }
        @Override public org.opendroidpdf.app.annotation.AnnotationSelectionManager.Host selectionHost() { return selectionHost; }
        @Override public void requestFullRedrawAfterNextAnnotationLoad() { MuPDFPageView.this.requestFullRedrawAfterNextAnnotationLoad(); }
        @Override public void loadAnnotations() { MuPDFPageView.this.loadAnnotations(); }
        @Override public void discardRenderedPage() { MuPDFPageView.this.discardRenderedPage(); }
        @Override public void redraw(boolean updateHq) { MuPDFPageView.this.redraw(updateHq); }
        @Override public void setModeDrawing() { ((MuPDFReaderView)mParent).setMode(MuPDFReaderView.Mode.Drawing); }
        @Override public void processSelectedText(TextProcessor processor) { MuPDFPageView.this.processSelectedText(processor); }
        @Override public void deselectText() { MuPDFPageView.this.deselectText(); }
        @Override public void setDraw(PointF[][] arcs) { MuPDFPageView.this.setDraw(arcs); }
        @Override public Context getContext() { return MuPDFPageView.this.getContext(); }
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
        @Override public void selectAnnotation(int index, RectF bounds) { selectionManager.select(index, bounds, selectionHost); }
        @Override public void onTextAnnotationTapped(Annotation annotation) { ((MuPDFReaderView)mParent).addTextAnnotFromUserInput(annotation); }

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

    public void setChangeReporter(Runnable reporter) {
        changeReporter = reporter;
        widgetUiController.setChangeReporter(() -> { if (changeReporter != null) changeReporter.run(); });
    }

    public Hit passClickEvent(MotionEvent e) { return pageHitRouter.passClick(e); }

    public Hit clickWouldHit(MotionEvent e) { return pageHitRouter.wouldHit(e); }
    


    @TargetApi(11)
    public boolean copySelection() { return selectionRouter.copySelection(); }

    public boolean markupSelection(final Annotation.Type type) { return selectionRouter.markupSelection(type); }

    @Override
    public void deleteSelectedAnnotation() { selectionRouter.deleteSelectedAnnotation(); }

    public void editSelectedAnnotation() { selectionRouter.editSelectedAnnotation(); }

    public Annotation.Type selectedAnnotationType() { return selectionRouter.selectedAnnotationType(); }
    public boolean selectedAnnotationIsEditable() { return selectionRouter.selectedAnnotationIsEditable(); }
    public void deselectAnnotation() { selectionRouter.deselectAnnotation(); }

    private static int countPoints(PointF[][] arcs) {
        if (arcs == null) {
            return 0;
        }
        int count = 0;
        for (PointF[] arc : arcs) {
            if (arc == null) {
                continue;
            }
            count += arc.length;
        }
        return count;
    }


    @Override
    public boolean saveDraw() { 
        return inkController.saveDraw();
    }

    @Override
    public void undoDraw() { inkController.undoDraw(); }

    @Override
    public boolean canUndo() { return inkController.canUndo(); }


    private void drawPage(Bitmap bm, int sizeX, int sizeY,
                          int patchX, int patchY, int patchWidth, int patchHeight, MuPDFCore.Cookie cookie) {
        muPdfController.drawPage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
    }

    // Wait (best-effort) for the asynchronous ink-commit task to finish so that
    // subsequent export/print includes the accepted stroke. Safe to call off the UI thread.
    public void awaitInkCommit(long timeoutMs) {
        // Ink commits run synchronously; retained for legacy callers that awaited AsyncTasks.
    }
    
    private void updatePage(Bitmap bm, int sizeX, int sizeY,
                            int patchX, int patchY, int patchWidth, int patchHeight, MuPDFCore.Cookie cookie) {
        muPdfController.updatePage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
    }

	@Override
	protected CancellableTaskDefinition<PatchInfo, PatchInfo> getRenderTask(PatchInfo patchInfo) {
		return new MuPDFCancellableTaskDefinition<PatchInfo, PatchInfo>(muPdfController.rawRepository()) {
            @Override
			public PatchInfo doInBackground(MuPDFCore.Cookie cookie, PatchInfo... v) {
				PatchInfo patchInfo = v[0];
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
				return patchInfo;
			}			
		};
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
            
        PointF start = new PointF(annot.left, getHeight()/getScale()-annot.top);
        PointF end = new PointF(annot.right, getHeight()/getScale()-annot.bottom);
        PointF[] quadPoints = new PointF[]{start, end};
        annotationUiController.addTextAnnotation(
                mPageNumber,
                quadPoints,
                annot.text,
                this::loadAnnotations);
	}

	@Override
	public void setPage(final int page, PointF size) {
        inkController.clear();
        org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
        widgetUiController.setPageNumber(page);

        widgetAreasLoader.load(page, new WidgetAreasCallback() {
            @Override public void onResult(RectF[] areas) { mWidgetAreas = areas; }
        });

		super.setPage(page, size);
        loadAnnotations();//Must be done after super.setPage() otherwise page number is wrong!
	}

	public void setScale(float scale) {
            // This type of view scales automatically to fit the size
            // determined by the parent view groups during layout
	}

    @Override
    public void releaseResources() {
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
