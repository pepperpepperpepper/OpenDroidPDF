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
import org.opendroidpdf.app.widget.WidgetAreasLoader;
import org.opendroidpdf.SelectionActionRouter;
import org.opendroidpdf.widget.WidgetUiController;
import org.opendroidpdf.app.reader.ReaderComposition;

import android.annotation.TargetApi;
import org.opendroidpdf.TextProcessor;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import java.util.Objects;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import org.opendroidpdf.BuildConfig;


public class MuPDFPageView extends PageView implements MuPDFView {
private static final String TAG = "MuPDFPageView";

private final FilePicker.FilePickerSupport mFilePickerSupport;
private final MuPdfController muPdfController;
    private final AnnotationController annotationController;
    private final AnnotationUiController annotationUiController;
    private final InkController inkController;
    private final WidgetController widgetController;
    private final PageHitRouter pageHitRouter;
    private final SelectionActionRouter selectionRouter;
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

    // When true, the current erase gesture is editing an existing ink annotation
    // (loaded into DrawingController) and should auto-commit on erase end.
    private boolean erasingExistingInkAnnotation = false;
    private long lastEraseInkHitAttemptUptimeMs = 0L;
    
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
        inkController = new InkController(new InkHost(), muPdfController);
        widgetUiController = composition.newWidgetUiController();
        widgetAreasLoader = composition.newWidgetAreasLoader();
        pageHitRouter = new PageHitRouter(new HitHost());
        this.selectionManager = composition.selectionManager();
        this.selectionUiBridge = new SelectionUiBridge(this, (MuPDFReaderView) mParent, selectionManager);
        annotationHitHelper = new org.opendroidpdf.AnnotationHitHelper(selectionUiBridge.selectionManager());
        selectionRouter = new SelectionActionRouter(selectionUiBridge.selectionManager(), annotationUiController, selectionUiBridge.selectionRouterHost());

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

    @Override
    public void startErase(final float x, final float y) {
        lastEraseInkHitAttemptUptimeMs = 0L;
        if (BuildConfig.DEBUG) {
            float s = 1f;
            try { s = getScale(); } catch (Throwable ignore) {}
            int l = 0, t = 0;
            try { l = getLeft(); t = getTop(); } catch (Throwable ignore) {}
            float dx = s != 0f ? (x - l) / s : x;
            float dy = s != 0f ? (y - t) / s : y;
            android.util.Log.d(TAG, "startErase x=" + x + " y=" + y
                    + " scale=" + s + " view=[" + l + "," + t + "] docRel=[" + dx + "," + dy + "]"
                    + " drawingSize=" + getDrawingSize()
                    + " erasingExisting=" + erasingExistingInkAnnotation
                    + " mode=" + (mParent instanceof MuPDFReaderView ? ((MuPDFReaderView) mParent).getMode() : "n/a"));
        }
        // If there is no pending ink, but the user starts erasing on top of an existing
        // ink annotation, load that annotation's arcs into the DrawingController so the
        // eraser can affect committed ink, not just pending strokes.
        maybeBeginErasingExistingInkAt(x, y);
        super.startErase(x, y);
    }

    @Override
    public void continueErase(final float x, final float y) {
        // If the user starts erasing in empty space and then swipes across an existing ink
        // annotation, begin the "edit ink then erase" flow as soon as we hit it.
        //
        // Avoid doing a full JNI annotations scan on every MOVE: throttle hit attempts to
        // keep erasing responsive on slower devices / heavily annotated pages.
        long now = SystemClock.uptimeMillis();
        if (now - lastEraseInkHitAttemptUptimeMs >= 80L) {
            lastEraseInkHitAttemptUptimeMs = now;
            maybeBeginErasingExistingInkAt(x, y);
        }
        super.continueErase(x, y);
    }

    @Override
    public void finishErase(final float x, final float y) {
        super.finishErase(x, y);
        if (erasingExistingInkAnnotation) {
            try {
                // If anything remains after erasing, commit it immediately so the user sees the result.
                if (getDrawingSize() > 0) {
                    saveDraw();
                } else {
                    cancelDraw();
                }
            } catch (Throwable ignore) {
                // Avoid crashing during erase; leaving the doc in a consistent state is best-effort.
            } finally {
                erasingExistingInkAnnotation = false;
            }
        }
    }

    private void maybeBeginErasingExistingInkAt(final float x, final float y) {
        if (erasingExistingInkAnnotation) return;
        if (getDrawingSize() != 0) return;

        // Ensure we have a fresh annotation list for hit-testing (ink commits can race the
        // async annotation loader, leaving mAnnotations stale/null).
        final Annotation[] annotations;
        try {
            annotations = getAnnotations();
            mAnnotations = annotations;
        } catch (Throwable ignore) {
            return; // Best-effort: fall back to erasing pending strokes only.
        }
        if (annotations == null || annotations.length == 0) return;

        // Convert the touch point to page doc coordinates (same space as annotation bounds/arcs).
        final float scale;
        try {
            scale = getScale();
        } catch (Throwable ignore) {
            return;
        }
        if (scale == 0f) return;
        final float docRelX = (x - getLeft()) / scale;
        final float docRelY = (y - getTop()) / scale;

        // Prefer arc-based hit-testing: some ink annotations can have oversized/incorrect Rects
        // (especially across thickness/color changes), which makes rect-based Hit.InkAnnotation
        // selection latch to the wrong annotation and "only erase the last stroke".
        final float hitRadiusDoc = approxHitRadiusDoc(scale);
        final int inkIndex = findInkAnnotationHitIndex(annotations, docRelX, docRelY, hitRadiusDoc);
        if (inkIndex < 0) {
            if (BuildConfig.DEBUG) {
                logInkHitDebug(annotations, docRelX, docRelY, hitRadiusDoc);
            }
            return;
        }

        final Annotation target = annotations[inkIndex];
        if (target == null) return;

        try {
            if (BuildConfig.DEBUG) {
                android.util.Log.d(TAG, "begin erase ink idx=" + inkIndex
                        + " obj=" + target.objectNumber
                        + " totalAnnots=" + annotations.length
                        + " rect=[" + target.left + "," + target.top + "][" + target.right + "," + target.bottom + "]"
                        + " arcs=" + (target.arcs != null ? target.arcs.length : -1)
                        + " rDoc=" + hitRadiusDoc);
            }

            // Load the ink arcs into the drawing controller so the eraser modifies the stroke geometry.
            if (target.arcs != null && target.arcs.length > 0) {
                setDraw(target.arcs);
            } else {
                // If we can't edit geometry, fall back to deleting the whole annotation.
                setDraw(null);
            }

            // Delete the original ink annotation immediately so the underlying render doesn't "fight"
            // the in-progress overlay erase. Do this synchronously to avoid index drift.
            try {
                if (target.objectNumber >= 0) {
                    muPdfController.deleteAnnotationByObjectNumber(mPageNumber, target.objectNumber);
                } else {
                    muPdfController.deleteAnnotation(mPageNumber, inkIndex);
                }
            } catch (Throwable t) {
                if (BuildConfig.DEBUG) android.util.Log.w(TAG, "begin erase: deleteAnnotation failed idx=" + inkIndex, t);
                // If we fail to delete, don't enter the "editing existing ink" flow.
                setDraw(null);
                return;
            }

            // Force a redraw without the deleted annotation; the overlay continues to render the
            // editable ink so the user sees a stable stroke while erasing.
            requestFullRedrawAfterNextAnnotationLoad();
            discardRenderedPage();
            loadAnnotations();
            redraw(false);

            if (mParent instanceof MuPDFReaderView) {
                ((MuPDFReaderView) mParent).setMode(MuPDFReaderView.Mode.Erasing);
            }
            erasingExistingInkAnnotation = getDrawingSize() > 0;
        } catch (Throwable t) {
            if (BuildConfig.DEBUG) android.util.Log.w(TAG, "maybeBeginErasingExistingInkAt failed", t);
            // Best-effort: fall back to erasing pending strokes only.
        }
    }

    private static float approxHitRadiusDoc(float scale) {
        // Choose a ~screen-space radius then convert to doc units.
        // Keeping this decoupled from preferences avoids needing PageView's private EditorPreferences.
        final float desiredPx = 36f;
        final float safeScale = Math.max(0.1f, Math.abs(scale));
        return desiredPx / safeScale;
    }

    private static int findInkAnnotationHitIndex(Annotation[] annotations, float docRelX, float docRelY, float radiusDoc) {
        if (annotations == null || annotations.length == 0) return -1;
        final float r = Math.max(1f, radiusDoc);
        final PointF p = new PointF(docRelX, docRelY);

        int bestIndex = -1;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < annotations.length; i++) {
            Annotation a = annotations[i];
            if (a == null) continue;
            if (a.type != Annotation.Type.INK) continue;

            // Prefer arc geometry when present; fall back to bounds.
            if (a.arcs != null && a.arcs.length > 0) {
                float dist = distanceToInkArcs(a.arcs, p);
                if (dist <= r && dist < bestDist) {
                    bestDist = dist;
                    bestIndex = i;
                }
            } else if (a.contains(docRelX, docRelY)) {
                // With no arcs, we can't compute proximity; accept rect hit.
                return i;
            }
        }

        return bestIndex;
    }

    private static float distanceToInkArcs(PointF[][] arcs, PointF p) {
        if (arcs == null || p == null) return Float.MAX_VALUE;
        float best = Float.MAX_VALUE;
        for (PointF[] arc : arcs) {
            if (arc == null || arc.length == 0) continue;
            PointF prev = null;
            for (PointF pt : arc) {
                if (pt == null) continue;
                float d = PointFMath.distance(pt, p);
                if (d < best) best = d;
                if (prev != null) {
                    // Approximate: distance to the infinite line segment, which is good enough for hit-testing.
                    float dl = PointFMath.pointToLineDistance(prev, pt, p);
                    if (dl < best) best = dl;
                }
                prev = pt;
            }
        }
        return best;
    }

    private static void logInkHitDebug(Annotation[] annotations, float docRelX, float docRelY, float radiusDoc) {
        if (annotations == null) {
            android.util.Log.d(TAG, "erase-hit: annotations=null at [" + docRelX + "," + docRelY + "] r=" + radiusDoc);
            return;
        }
        int inkCount = 0;
        for (Annotation a : annotations) {
            if (a != null && a.type == Annotation.Type.INK) inkCount++;
        }
        android.util.Log.d(TAG, "erase-hit: no ink hit at [" + docRelX + "," + docRelY + "] r=" + radiusDoc
                + " totalAnnots=" + annotations.length + " inkAnnots=" + inkCount);
        if (inkCount == 0) return;

        final PointF p = new PointF(docRelX, docRelY);
        int logged = 0;
        for (int i = 0; i < annotations.length && logged < 6; i++) {
            Annotation a = annotations[i];
            if (a == null || a.type != Annotation.Type.INK) continue;
            float dist = distanceToInkArcs(a.arcs, p);
            String arcSample = "";
            try {
                if (a.arcs != null && a.arcs.length > 0 && a.arcs[0] != null && a.arcs[0].length > 0) {
                    PointF first = a.arcs[0][0];
                    PointF last = a.arcs[0][a.arcs[0].length - 1];
                    arcSample = " arc0_first=[" + (first != null ? first.x + "," + first.y : "null")
                            + "] arc0_last=[" + (last != null ? last.x + "," + last.y : "null") + "]";
                }
            } catch (Throwable ignore) {}
            android.util.Log.d(TAG, "erase-hit: ink idx=" + i
                    + " rect=[" + a.left + "," + a.top + "][" + a.right + "," + a.bottom + "]"
                    + " arcs=" + (a.arcs != null ? a.arcs.length : -1)
                    + " minDist=" + dist
                    + arcSample);
            logged++;
        }
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
        erasingExistingInkAnnotation = false;
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
        erasingExistingInkAnnotation = false;
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
