package org.opendroidpdf.app.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import androidx.appcompat.widget.AppCompatImageView;

import kotlinx.coroutines.Job;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.CancellableTaskDefinition;
import org.opendroidpdf.PatchInfo;
import org.opendroidpdf.BuildConfig;
import java.util.concurrent.atomic.AtomicLong;

// ImageView that renders either the full-page bitmap or the hi‑res patch asynchronously.
public class PagePatchView extends AppCompatImageView {

    public interface Host {
        void removeBusyIndicator();
        CancellableTaskDefinition<PatchInfo, PatchInfo> getRenderTask(PatchInfo patchInfo);
        boolean isPageReady();
        /** Optional callback when the first patch bitmap is set. */
        default void onFirstPatchRendered(Bitmap bitmap) {}
    }

    private final Host host;
    private Rect area;
    private Rect patchArea;
    private Job drawPatchJob;
    private CancellableTaskDefinition<PatchInfo, PatchInfo> drawPatchTask;
    private Bitmap bitmap;
    private Bitmap inFlightBitmap;
    private boolean hasNotifiedFirstPatch = false;
    private final AtomicLong renderGeneration = new AtomicLong(0L);
    private volatile long activeGeneration = 0L;

    public PagePatchView(Context context, Host host) {
        super(context);
        this.host = host;
        setScaleType(ScaleType.MATRIX);
        // Ensure we always draw (for debug overlays) and the surface is opaque.
        setWillNotDraw(false);
        setBackgroundColor(Color.WHITE);
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
        if (BuildConfig.DEBUG) {
            logBitmapEvent("reset", bitmap);
            android.util.Log.d("PagePatchView", "reset caller", new Exception());
        }
        // Keep the last bitmap displayed to avoid flashing blank while re-rendering.
        invalidate();
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        if (BuildConfig.DEBUG) logBitmapEvent("setImageBitmap", bitmap);
        maybeMarkBlank(bitmap);
        super.setImageBitmap(bitmap);
        if (!hasNotifiedFirstPatch && bitmap != null) {
            hasNotifiedFirstPatch = true;
            host.onFirstPatchRendered(bitmap);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // DEBUG overlay: paint a small thumbnail of the current patch in the top-left corner so
        // emulator captures prove that rendered content is reaching the view surface.
        if (!BuildConfig.DEBUG) return;
        if (bitmap == null || bitmap.isRecycled()) return;

        // Draw the full bitmap stretched into the view so on-screen captures show the content
        // even if the ImageView's normal path is being over-painted elsewhere.
        Rect fullDst = new Rect(0, 0, getWidth(), getHeight());
        Rect fullSrc = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        canvas.drawBitmap(bitmap, fullSrc, fullDst, null);

        final int thumbSize = Math.min(240, Math.min(bitmap.getWidth(), bitmap.getHeight()));
        if (thumbSize > 0) {
            Rect src = new Rect(0, 0, thumbSize, thumbSize);
            Rect dst = new Rect(16, 16, 16 + thumbSize, 16 + thumbSize);

            canvas.drawBitmap(bitmap, src, dst, null);

            Paint p = new Paint();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4f);
            p.setColor(0xFFFF3B30); // red border
            canvas.drawRect(dst, p);

            p.setStyle(Paint.Style.FILL);
            p.setTextSize(32f);
            p.setColor(Color.BLACK);
            canvas.drawText("DBG", dst.left + 12, dst.top + 40, p);
        }
    }

    private void logBitmapEvent(String label, Bitmap bm) {
        if (bm == null) {
            android.util.Log.d("PagePatchView", label + " bitmap=null");
            return;
        }
        android.util.Log.d("PagePatchView", label + " bitmap=" + bm.getWidth() + "x" + bm.getHeight()
                + " hasAlpha=" + bm.hasAlpha() + " config=" + bm.getConfig());
    }

    public Bitmap getImageBitmap() { return bitmap; }

    public void renderInBackground(PatchInfo patchInfo) {
        if (patchInfo == null || patchInfo.viewArea == null || patchInfo.patchBm == null) {
            return;
        }
        if (!host.isPageReady()) return;
        // Skip rendering if page number is still unset (setPage not called yet).
        // We encode invalid page via PatchInfo.completeRedraw=false and sentinel in caller, but
        // safest is to just bail when viewArea is empty or page not ready.
        if (patchInfo.viewArea.width() <= 0 || patchInfo.viewArea.height() <= 0) {
            return;
        }
        if (!patchInfo.completeRedraw && (patchInfo.patchArea == null
                || patchInfo.patchArea.width() <= 0 || patchInfo.patchArea.height() <= 0)) {
            return;
        }

        // If we are already rendering / have rendered the area there is nothing to do
        if (getArea() == patchInfo.viewArea) return;

        // Stop the drawing of previous patch if still going
        cancelRenderInBackground();

        setPatchArea(null);
        final long generation = renderGeneration.incrementAndGet();
        activeGeneration = generation;

        inFlightBitmap = patchInfo.patchBm;
        final CancellableTaskDefinition<PatchInfo, PatchInfo> task = host.getRenderTask(patchInfo);
        drawPatchTask = task;
        final PatchInfo[] resultHolder = new PatchInfo[1];
        drawPatchJob = AppCoroutines.launchIo(AppCoroutines.ioScope(), new Runnable() {
            @Override public void run() {
                try {
                    PatchInfo result = task.doInBackground(patchInfo);
                    resultHolder[0] = result;
                } catch (Throwable ignore) {
                } finally {
                    post(new Runnable() {
                        @Override public void run() {
                            try {
                                if (resultHolder[0] != null && activeGeneration == generation) {
                                    PatchInfo pi = resultHolder[0];
                                    host.removeBusyIndicator();
                                    setArea(pi.viewArea);
                                    setPatchArea(pi.patchArea);
                                    setImageBitmap(pi.patchBm);
                                    requestLayout();
                                    inFlightBitmap = null;
                                } else {
                                    host.removeBusyIndicator();
                                }
                            } finally {
                                try { task.doCleanup(); } catch (Throwable ignore) {}
                                if (drawPatchTask == task) {
                                    drawPatchTask = null;
                                    drawPatchJob = null;
                                }
                                if (inFlightBitmap == piForCleanup(resultHolder)) {
                                    inFlightBitmap = null;
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    private static Bitmap piForCleanup(PatchInfo[] holder) {
        if (holder == null || holder.length == 0) return null;
        PatchInfo pi = holder[0];
        return pi != null ? pi.patchBm : null;
    }

    public void cancelRenderInBackground() {
        // Invalidate any queued UI apply from the previous render job.
        activeGeneration = renderGeneration.incrementAndGet();

        if (drawPatchJob != null) {
            drawPatchJob.cancel(null);
            drawPatchJob = null;
        }
        if (drawPatchTask != null) {
            // Abort the native Cookie when the in-flight render targets a bitmap that is not
            // currently displayed. This prevents concurrent renders into our 2-bitmap pool
            // (common during pinch-zoom) from racing and crashing native rendering.
            try {
                if (inFlightBitmap != null && inFlightBitmap != bitmap) {
                    drawPatchTask.doCancel();
                }
            } catch (Throwable ignore) {
            }
            drawPatchTask = null;
        }
        inFlightBitmap = null;
    }

    /**
     * Detect a completely uniform patch (common when rendering failed) and log it.
     * In debug builds, overlay a red watermark so the blank state is obvious.
     */
    private void maybeMarkBlank(Bitmap bm) {
        if (bm == null) return;
        if (!looksUniform(bm)) return;

        android.util.Log.w("PagePatchView", "Detected uniform (blank) patch: "
                + bm.getWidth() + "x" + bm.getHeight());

        // Silence the debug watermark to avoid altering screenshots; keep logging only.
    }

    private boolean looksUniform(Bitmap bm) {
        int w = bm.getWidth();
        int h = bm.getHeight();
        if (w == 0 || h == 0) return false;
        // Sample a small grid.
        int[] samples = new int[25];
        int idx = 0;
        for (int yi = 0; yi < 5; yi++) {
            for (int xi = 0; xi < 5; xi++) {
                int x = (int)((xi + 0.5f) * w / 5f);
                int y = (int)((yi + 0.5f) * h / 5f);
                samples[idx++] = bm.getPixel(Math.min(x, w - 1), Math.min(y, h - 1));
            }
        }
        int base = samples[0];
        // Allow tiny variance (e.g., due to JPEG/dithering)
        final int tol = 3;
        for (int i = 1; i < samples.length; i++) {
            int c = samples[i];
            int dr = Math.abs(((c >> 16) & 0xFF) - ((base >> 16) & 0xFF));
            int dg = Math.abs(((c >> 8) & 0xFF) - ((base >> 8) & 0xFF));
            int db = Math.abs((c & 0xFF) - (base & 0xFF));
            if (dr > tol || dg > tol || db > tol) {
                return false;
            }
        }
        return true;
    }
}
