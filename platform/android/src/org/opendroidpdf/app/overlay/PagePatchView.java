package org.opendroidpdf.app.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.widget.ImageView;

import kotlinx.coroutines.Job;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.CancellableTaskDefinition;
import org.opendroidpdf.PatchInfo;

// ImageView that renders either the full-page bitmap or the hi‑res patch asynchronously.
public class PagePatchView extends ImageView {

    public interface Host {
        void removeBusyIndicator();
        CancellableTaskDefinition<PatchInfo, PatchInfo> getRenderTask(PatchInfo patchInfo);
    }

    private final Host host;
    private Rect area;
    private Rect patchArea;
    private Job drawPatchJob;
    private CancellableTaskDefinition<PatchInfo, PatchInfo> drawPatchTask;
    private Bitmap bitmap;

    public PagePatchView(Context context, Host host) {
        super(context);
        this.host = host;
        setScaleType(ImageView.ScaleType.MATRIX);
    }

    @Override
    public boolean isOpaque() { return true; }

    public void setArea(Rect area) { this.area = area; }
    public Rect getArea() { return area; }

    public void setPatchArea(Rect patchArea) { this.patchArea = patchArea; }
    public Rect getPatchArea() { return patchArea; }

    public void reset() {
        cancelRenderInBackground();
        setArea(null);
        setPatchArea(null);
        setImageBitmap(null);
        setImageDrawable(null);
        invalidate();
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        super.setImageBitmap(bitmap);
    }

    public Bitmap getImageBitmap() { return bitmap; }

    public void renderInBackground(PatchInfo patchInfo) {
        // If we are already rendering / have rendered the area there is nothing to do
        if (getArea() == patchInfo.viewArea) return;

        // Stop the drawing of previous patch if still going
        cancelRenderInBackground();

        setPatchArea(null);
        drawPatchTask = host.getRenderTask(patchInfo);
        final PatchInfo[] resultHolder = new PatchInfo[1];
        drawPatchJob = AppCoroutines.launchIo(AppCoroutines.ioScope(), new Runnable() {
            @Override public void run() {
                try {
                    PatchInfo result = drawPatchTask.doInBackground(patchInfo);
                    resultHolder[0] = result;
                } catch (Throwable ignore) {
                } finally {
                    AppCoroutines.launchMain(AppCoroutines.mainScope(), new Runnable() {
                        @Override public void run() {
                            try {
                                if (resultHolder[0] != null) {
                                    PatchInfo pi = resultHolder[0];
                                    host.removeBusyIndicator();
                                    setArea(pi.viewArea);
                                    setPatchArea(pi.patchArea);
                                    setImageBitmap(pi.patchBm);
                                    requestLayout();
                                } else {
                                    host.removeBusyIndicator();
                                }
                            } finally {
                                if (drawPatchTask != null) {
                                    drawPatchTask.doCleanup();
                                    drawPatchTask = null;
                                }
                                drawPatchJob = null;
                            }
                        }
                    });
                }
            }
        });
    }

    public void cancelRenderInBackground() {
        if (drawPatchJob != null) {
            drawPatchJob.cancel(null);
            drawPatchJob = null;
        }
        if (drawPatchTask != null) {
            try { drawPatchTask.doCancel(); } catch (Throwable ignore) {}
            try { drawPatchTask.doCleanup(); } catch (Throwable ignore) {}
            drawPatchTask = null;
        }
    }
}
