package org.opendroidpdf;

import org.opendroidpdf.MuPDFCore.Cookie;
import org.opendroidpdf.core.AnnotationCallback;
import org.opendroidpdf.core.AnnotationController;
// Removed direct job management; see AnnotationActions
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
import org.opendroidpdf.app.annotation.InkUndoController;
import org.opendroidpdf.app.annotation.AnnotationActions;
import org.opendroidpdf.app.annotation.AnnotationEditController;
import org.opendroidpdf.app.widget.WidgetAreasLoader;
import org.opendroidpdf.app.selection.TextSelectionActions;

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

private int lastHitAnnotation = 0;
    
private final FilePicker.FilePickerSupport mFilePickerSupport;
private final MuPdfController muPdfController;
private final AnnotationController annotationController;
private final WidgetController widgetController;
private final SignatureController signatureController;
private final InkUndoController inkUndoController;
    private WidgetController.WidgetJob mPassClickJob;
	private RectF mWidgetAreas[];
	private int mSelectedAnnotationIndex = -1;
    // Widget area loading now handled by WidgetAreasLoader
    private org.opendroidpdf.app.widget.WidgetUiBridge widgetUi;
    private AnnotationActions annotationActions;
    private AnnotationEditController annotationEditController;
    private WidgetAreasLoader widgetAreasLoader;
    private TextSelectionActions textSelectionActions;
	private Runnable changeReporter;
    private final org.opendroidpdf.app.signature.SignatureFlowController signatureFlow;
    
public MuPDFPageView(Context context, FilePicker.FilePickerSupport filePickerSupport, MuPdfController controller, ViewGroup parent) {
        super(context, parent, new DocumentContentController(Objects.requireNonNull(controller, "MuPdfController required")));
		mFilePickerSupport = filePickerSupport;
		muPdfController = controller;
        annotationController = new AnnotationController(muPdfController);
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
        inkUndoController = new InkUndoController(new InkUndoHost(), muPdfController, TAG, LOG_UNDO);
        widgetUi = new org.opendroidpdf.app.widget.WidgetUiBridge(context, widgetController);
        annotationActions = new AnnotationActions(annotationController);
        annotationEditController = new AnnotationEditController();
        widgetAreasLoader = new WidgetAreasLoader(widgetController);
        textSelectionActions = new TextSelectionActions();

        // Signature UI now handled by SignatureFlowController
	}

    private class InkUndoHost implements InkUndoController.Host {
        @Override
        public int pageNumber() {
            return mPageNumber;
        }

        @Override
        public void onInkStackMutated() {
            requestFullRedrawAfterNextAnnotationLoad();
            loadAnnotations();
            discardRenderedPage();
            redraw(false);
            org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
        }
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

    private void invokeTextDialog(String text) { widgetUi.showTextDialog(text); }
    // Debug-only entry points used by DebugActionsController via MuPDFReaderView
    public void debugShowTextWidgetDialog() { widgetUi.showTextDialog(""); }
    public void debugShowChoiceWidgetDialog() { widgetUi.showChoiceDialog(new String[]{"One","Two","Three"}); }

    private void invokeChoiceDialog(final String [] options) { widgetUi.showChoiceDialog(options); }

    private void invokeSignatureCheckingDialog() {
        signatureFlow.checkFocusedSignature();
    }

    private void invokeSigningDialog() { signatureFlow.showSigningDialog(); }

    private void warnNoSignatureSupport() { signatureFlow.showNoSignatureSupport(); }

    public void setChangeReporter(Runnable reporter) {
        changeReporter = reporter;
        if (widgetUi != null) widgetUi.setChangeReporter(() -> { if (changeReporter != null) changeReporter.run(); });
    }

    public Hit passClickEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        float scale = getScale();
		final float docRelX = (x - getLeft())/scale;
		final float docRelY = (y - getTop())/scale;
		boolean hit = false;
		int i;

		if (mLinks != null)
            for (LinkInfo l: mLinks)
                if (l.rect.contains(docRelX, docRelY))
                {
                    deselectAnnotation();
                    switch(l.type())
                    {
                        case Internal:
                            return Hit.LinkInternal;
                        case External:
                            return Hit.LinkExternal;
                        case Remote:
                            return Hit.LinkRemote;
                    }
                }
                
		if (mAnnotations != null) {
            for (i = 0; i < mAnnotations.length; i++)
            {
                    //If multiple annotations overlap, make sure we
                    //return a different annotation as hit each
                    //time we are called 
                int j = (i+lastHitAnnotation+1) % mAnnotations.length;
                if (mAnnotations[j].contains(docRelX, docRelY))
                {
                    hit = true;
                    i = lastHitAnnotation = j;
                    break;
                }
            }
            if (hit) {
                switch (mAnnotations[i].type) {
                    case HIGHLIGHT:
                    case UNDERLINE:
                    case SQUIGGLY:
                    case STRIKEOUT:
                        mSelectedAnnotationIndex = i;
                        setItemSelectBox(mAnnotations[i]);
                        return Hit.Annotation;
                    case INK:
                        mSelectedAnnotationIndex = i;
                        setItemSelectBox(mAnnotations[i]);
                        return Hit.InkAnnotation;
                    case TEXT:
                    case FREETEXT:
                        mSelectedAnnotationIndex = i;
                        setItemSelectBox(mAnnotations[i]);
                        ((MuPDFReaderView)mParent).addTextAnnotFromUserInput(mAnnotations[i]);
                        return Hit.TextAnnotation;
                }
            }
		}
		deselectAnnotation();
		
		if (!widgetController.javascriptSupported())
			return Hit.Nothing;
		
		if (mWidgetAreas != null) {
			for (i = 0; i < mWidgetAreas.length && !hit; i++)
				if (mWidgetAreas[i].contains(docRelX, docRelY))
					hit = true;
		}

		if (hit) {
            if (mPassClickJob != null) {
                mPassClickJob.cancel();
            }
            mPassClickJob = widgetController.passClickAsync(
                    mPageNumber,
                    docRelX,
                    docRelY,
                    new WidgetPassClickCallback() {
                        @Override
                        public void onResult(PassClickResult result) {
                            if (result.changed && changeReporter != null) {
                                changeReporter.run();
                            }

                            result.acceptVisitor(new PassClickResultVisitor() {
                                @Override
                                public void visitText(PassClickResultText result) {
                                    invokeTextDialog(result.text);
                                }

                                @Override
                                public void visitChoice(PassClickResultChoice result) {
                                    invokeChoiceDialog(result.options);
                                }

                                @Override
                                public void visitSignature(PassClickResultSignature result) {
                                    switch (result.state) {
                                        case NoSupport:
                                            warnNoSignatureSupport();
                                            break;
                                        case Unsigned:
                                            invokeSigningDialog();
                                            break;
                                        case Signed:
                                            invokeSignatureCheckingDialog();
                                            break;
                                    }
                                }
                            });
                        }
                    });
			return Hit.Widget;
		}

		return Hit.Nothing;
	}


    public Hit clickWouldHit(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        float scale = getScale();
		final float docRelX = (x - getLeft())/scale;
		final float docRelY = (y - getTop())/scale;
		boolean hit = false;
		int i;

		if (mLinks != null)
            for (LinkInfo l: mLinks)
                if (l.rect.contains(docRelX, docRelY))
                {
                    switch(l.type())
                    {
                        case Internal:
                            return Hit.LinkInternal;
                        case External:
                            return Hit.LinkExternal;
                        case Remote:
                            return Hit.LinkRemote;
                    }
                }
                
		if (mAnnotations != null) {
            for (i = 0; i < mAnnotations.length; i++)
            {
                    //If multiple annotations overlap, make sure we
                    //return a different annotation as hit each
                    //time we are called 
                int j = (i+lastHitAnnotation) % mAnnotations.length;
                if (mAnnotations[j].contains(docRelX, docRelY))
                {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                switch (mAnnotations[i].type) {
                    case HIGHLIGHT:
                    case UNDERLINE:
                    case SQUIGGLY:
                    case STRIKEOUT:
                        return Hit.Annotation;
                    case INK:
                        return Hit.InkAnnotation;
                    case TEXT:
                    case FREETEXT:
                        return Hit.TextAnnotation;
                }
            }
		}
                
		if (!widgetController.javascriptSupported())
			return Hit.Nothing;
		if (mWidgetAreas != null) {
			for (i = 0; i < mWidgetAreas.length && !hit; i++)
				if (mWidgetAreas[i].contains(docRelX, docRelY))
					hit = true;
		}
		if (hit) {
			return Hit.Widget;
		}
		return Hit.Nothing;
	}
    


    @TargetApi(11)
    public boolean copySelection() {
        return textSelectionActions.copySelection(new org.opendroidpdf.app.selection.TextSelectionActions.Host() {
            @Override public void processSelectedText(TextProcessor processor) { MuPDFPageView.this.processSelectedText(processor); }
            @Override public void deselectText() { MuPDFPageView.this.deselectText(); }
            @Override public Context getContext() { return MuPDFPageView.this.getContext(); }
        });
    }

    public boolean markupSelection(final Annotation.Type type) {
        return textSelectionActions.markupSelection(
                new org.opendroidpdf.app.selection.TextSelectionActions.Host() {
                    @Override public void processSelectedText(TextProcessor processor) { MuPDFPageView.this.processSelectedText(processor); }
                    @Override public void deselectText() { MuPDFPageView.this.deselectText(); }
                    @Override public Context getContext() { return MuPDFPageView.this.getContext(); }
                },
                type,
                (quadArray, t, onComplete) -> annotationActions.addMarkupAnnotation(mPageNumber, quadArray, t, () -> { loadAnnotations(); onComplete.run(); })
        );
    }

    @Override
    public void deleteSelectedAnnotation() {
        if (mSelectedAnnotationIndex != -1) {
            final int targetIndex = mSelectedAnnotationIndex;
            annotationActions.deleteAnnotation(
                    mPageNumber,
                    targetIndex,
                    () -> {
                        requestFullRedrawAfterNextAnnotationLoad();
                        loadAnnotations();
                        discardRenderedPage();
                        redraw(false);
                    }
            );

            deselectAnnotation();
		}
	}


    public void editSelectedAnnotation() {
        if (mSelectedAnnotationIndex == -1 || mAnnotations == null) return;
        final Annotation annot = mAnnotations[mSelectedAnnotationIndex];
        annotationEditController.editIfSupported(annot, new AnnotationEditController.Host() {
            @Override public void setDraw(PointF[][] arcs) { MuPDFPageView.this.setDraw(arcs); }
            @Override public void setModeDrawing() { ((MuPDFReaderView)mParent).setMode(MuPDFReaderView.Mode.Drawing); }
            @Override public void deleteSelectedAnnotation() { MuPDFPageView.this.deleteSelectedAnnotation(); }
        });
    }


    public Annotation.Type selectedAnnotationType() {
        return mAnnotations[mSelectedAnnotationIndex].type; 
    }
    
    
    public boolean selectedAnnotationIsEditable() {
        if (mSelectedAnnotationIndex != -1) {
            if(mAnnotations[mSelectedAnnotationIndex].arcs != null || mAnnotations[mSelectedAnnotationIndex].text != null)
                return true;
            else
                return false;
        }
        else
            return false;
    }
    
    
    public void deselectAnnotation() {
		mSelectedAnnotationIndex = -1;
		setItemSelectBox(null);
	}

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
		PointF[][] path = getDraw();
		if (path == null)
            return false;
        if (LOG_UNDO) {
            Log.d(TAG, "[undo] saveDraw begin page=" + mPageNumber
                    + " viewHash=" + System.identityHashCode(this)
                    + " pendingPoints=" + countPoints(path));
        }

            //Copy the overlay to the Hq view to prevent flickering,
            //the Hq view is then anyway rendered again...
        super.saveDraw();

        cancelDraw();

        // Synchronously commit the ink annotation so that immediate export/print
        // includes the accepted strokes without racing an async task.
        try {
            muPdfController.addInkAnnotation(mPageNumber, path);
            muPdfController.markDocumentDirty();
            // Force a tiny render to update annotation appearance streams prior to any export.
            try {
                android.graphics.Bitmap onePx = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
                MuPDFCore.Cookie cookie = muPdfController.newRenderCookie();
                muPdfController.drawPage(onePx, mPageNumber, /*pageW*/1, /*pageH*/1, /*patchX*/0, /*patchY*/0, /*patchW*/1, /*patchH*/1, cookie);
                cookie.destroy();
            } catch (Throwable ignoreInner) {}
            loadAnnotations();
            // Snapshot after MuPDF normalises the annotation so undo matches committed strokes.
            inkUndoController.recordCommittedInkForUndo(path);
            org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
        } catch (Throwable ignore) {
        }
        if (LOG_UNDO) {
            Log.d(TAG, "[undo] saveDraw end page=" + mPageNumber
                    + " stackSize=" + inkUndoController.stackSize()
                    + " viewHash=" + System.identityHashCode(this));
        }

        return true;
    }

    @Override
    public void undoDraw() {
        if (super.canUndo()) {
            super.undoDraw();
            org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
            return;
        }
        if (inkUndoController.undoLast()) {
            org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
            return;
        }
    }

    @Override
    public boolean canUndo() {
        return super.canUndo() || inkUndoController.hasUndo();
    }

    public void recordCommittedInkForUndo(PointF[][] arcs) {
        inkUndoController.recordCommittedInkForUndo(arcs);
    }


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
        annotationActions.addTextAnnotation(
                mPageNumber,
                quadPoints,
                annot.text,
                this::loadAnnotations);
	}

	@Override
	public void setPage(final int page, PointF size) {
        inkUndoController.clear();
        org.opendroidpdf.app.toolbar.ToolbarStateCache.get().setCanUndo(canUndo());
        if (widgetUi != null) widgetUi.setPageNumber(page);

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

        if (widgetUi != null) widgetUi.release();

        // Release signature controller jobs
        signatureFlow.release();

        if (annotationActions != null) annotationActions.release();

		super.releaseResources();
	}
}
