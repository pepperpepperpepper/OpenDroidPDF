package org.opendroidpdf.app.overlay;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.CancellableTaskDefinition;
import org.opendroidpdf.MuPDFCancellableTaskDefinition;
import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.PatchInfo;
import org.opendroidpdf.core.MuPdfController;

/**
 * Owns MuPDF-backed page patch rendering (full redraw + incremental update) so PageView subclasses
 * don’t need to embed rendering logic.
 */
public final class MuPdfPatchRenderer {
    private static final String TAG = "MuPdfPatchRenderer";

    private final MuPdfController muPdfController;

    public MuPdfPatchRenderer(MuPdfController muPdfController) {
        this.muPdfController = muPdfController;
    }

    public CancellableTaskDefinition<PatchInfo, PatchInfo> newRenderTask(final int pageNumber) {
        return new MuPDFCancellableTaskDefinition<PatchInfo, PatchInfo>(muPdfController.rawRepository()) {
            @Override
            public PatchInfo doInBackground(MuPDFCore.Cookie cookie, PatchInfo... v) {
                PatchInfo patchInfo = v[0];
                if (pageNumber < 0) {
                    Log.w(TAG, "render patch skipped: invalid page=" + pageNumber);
                    return patchInfo;
                }

                Log.d(TAG, "render patch page=" + pageNumber
                        + " complete=" + patchInfo.completeRedraw
                        + " view=" + patchInfo.viewArea.width() + "x" + patchInfo.viewArea.height()
                        + " patch=" + patchInfo.patchArea.width() + "x" + patchInfo.patchArea.height());
                if (patchInfo.viewArea.width() <= 0 || patchInfo.viewArea.height() <= 0
                        || patchInfo.patchArea.width() <= 0 || patchInfo.patchArea.height() <= 0) {
                    Log.w(TAG, "render patch skipped invalid dims page=" + pageNumber
                            + " viewArea=" + patchInfo.viewArea + " patchArea=" + patchInfo.patchArea);
                }

                // Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
                // is not incremented when drawing.
                //Careful: We must not let the native code draw to a bitmap that is alreay set to the view. The view might redraw itself (this can even happen without draw() or onDraw() beeing called) and then immediately appear with the new content of the bitmap. This leads to flicker if the view would have to be moved before showing the new content. This is avoided by the ReaderView providing one of two bitmaps in a smart way such that v[0].patchBm is always set to the one not currently set.
                if (patchInfo.completeRedraw) {
                    patchInfo.patchBm.eraseColor(0xFFFFFFFF);
                    drawPage(pageNumber,
                            patchInfo.patchBm, patchInfo.viewArea.width(), patchInfo.viewArea.height(),
                            patchInfo.patchArea.left, patchInfo.patchArea.top,
                            patchInfo.patchArea.width(), patchInfo.patchArea.height(),
                            cookie);
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        patchInfo.patchBm.eraseColor(0);
                    }
                    updatePage(pageNumber,
                            patchInfo.patchBm, patchInfo.viewArea.width(), patchInfo.viewArea.height(),
                            patchInfo.patchArea.left, patchInfo.patchArea.top,
                            patchInfo.patchArea.width(), patchInfo.patchArea.height(),
                            cookie);
                }

                if (looksUniform(patchInfo.patchBm)) {
                    Log.w(TAG, "Rendered uniform patch page=" + pageNumber
                            + " complete=" + patchInfo.completeRedraw
                            + " view=" + patchInfo.viewArea.width() + "x" + patchInfo.viewArea.height()
                            + " patch=" + patchInfo.patchArea.width() + "x" + patchInfo.patchArea.height());
                } else if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Patch ok page=" + pageNumber
                            + " view=" + patchInfo.viewArea.width() + "x" + patchInfo.viewArea.height()
                            + " patch=" + patchInfo.patchArea.width() + "x" + patchInfo.patchArea.height());
                }
                return patchInfo;
            }
        };
    }

    private void drawPage(int pageNumber, Bitmap bm, int sizeX, int sizeY,
                          int patchX, int patchY, int patchWidth, int patchHeight, MuPDFCore.Cookie cookie) {
        if (pageNumber < 0) {
            Log.w(TAG, "drawPage() skipped invalid page=" + pageNumber);
            return;
        }
        try {
            muPdfController.drawPage(bm, pageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
        } catch (Throwable t) {
            Log.e(TAG, "drawPage() failed page=" + pageNumber + " area=" + patchWidth + "x" + patchHeight
                    + " view=" + sizeX + "x" + sizeY, t);
            throw t;
        }
    }

    private void updatePage(int pageNumber, Bitmap bm, int sizeX, int sizeY,
                            int patchX, int patchY, int patchWidth, int patchHeight, MuPDFCore.Cookie cookie) {
        if (pageNumber < 0) {
            Log.w(TAG, "updatePage() skipped invalid page=" + pageNumber);
            return;
        }
        try {
            muPdfController.updatePage(bm, pageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
        } catch (Throwable t) {
            Log.e(TAG, "updatePage() failed page=" + pageNumber + " area=" + patchWidth + "x" + patchHeight
                    + " view=" + sizeX + "x" + sizeY, t);
            throw t;
        }
    }

    private static boolean looksUniform(Bitmap bm) {
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
}

