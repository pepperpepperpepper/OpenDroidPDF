package org.opendroidpdf.app.document;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.pdf.PdfDocument;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.app.overlay.SidecarAnnotationRenderer;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.core.MuPdfRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Exports the current document into a new PDF by rendering each page to a bitmap and
 * (optionally) compositing the sidecar overlay on top.
 *
 * <p>This is used for EPUB and for PDFs that cannot be modified in-place (sidecar mode).</p>
 */
final class FlattenedPdfExporter {
    private static final int DEFAULT_DPI = 180; // v1: balance quality vs memory
    private static final int MAX_PAGE_DIM_PX = 2400; // defensive clamp against OOM

    private FlattenedPdfExporter() {}

    static android.net.Uri export(@NonNull Context context,
                                  @NonNull MuPdfRepository repo,
                                  @Nullable SidecarAnnotationProvider sidecar,
                                  @NonNull String baseName) throws Exception {
        Context appContext = context.getApplicationContext();
        File outFile = newExportFile(appContext, baseName);

        float baseScale = DEFAULT_DPI / 72f;
        SidecarAnnotationRenderer overlayRenderer = (sidecar != null) ? new SidecarAnnotationRenderer() : null;

        PdfDocument pdf = new PdfDocument();
        try (OutputStream os = new FileOutputStream(outFile, false)) {
            int pageCount = Math.max(0, repo.getPageCount());
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                PointF pageSize = repo.getPageSize(pageIndex);
                if (pageSize == null) {
                    continue;
                }

                float scale = clampScaleForPage(pageSize, baseScale);
                int w = Math.max(1, (int) Math.ceil(pageSize.x * scale));
                int h = Math.max(1, (int) Math.ceil(pageSize.y * scale));

                Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(0xFFFFFFFF);
                MuPDFCore.Cookie cookie = repo.newRenderCookie();
                try {
                    repo.drawPage(bitmap, pageIndex, w, h, 0, 0, w, h, cookie);
                } finally {
                    cookie.destroy();
                }

                if (overlayRenderer != null && sidecar != null) {
                    Canvas overlayCanvas = new Canvas(bitmap);
                    // Flattened export should include full note text, regardless of UI “sticky note” mode.
                    overlayRenderer.draw(overlayCanvas, scale, pageIndex, sidecar, false);
                }

                PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(w, h, pageIndex + 1).create();
                PdfDocument.Page page = pdf.startPage(info);
                try {
                    page.getCanvas().drawBitmap(bitmap, 0, 0, null);
                } finally {
                    pdf.finishPage(page);
                    bitmap.recycle();
                }
            }
            pdf.writeTo(os);
        } finally {
            pdf.close();
        }

        return FileProvider.getUriForFile(appContext, "org.opendroidpdf.fileprovider", outFile);
    }

    private static File newExportFile(@NonNull Context appContext, @NonNull String baseName) {
        File dir = new File(appContext.getCacheDir(), "tmpfiles");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        String safe = (baseName == null || baseName.trim().isEmpty()) ? "document" : baseName.trim();
        safe = safe.replace('/', '_').replace('\\', '_');
        safe = safe.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (safe.length() > 64) safe = safe.substring(0, 64);

        // Always export as a PDF and avoid colliding with the original name.
        if (safe.toLowerCase().endsWith(".epub")) {
            safe = safe.substring(0, safe.length() - 5);
        } else if (safe.toLowerCase().endsWith(".pdf")) {
            safe = safe.substring(0, safe.length() - 4);
        }

        String fileName = safe + "_annotated_" + System.currentTimeMillis() + ".pdf";
        return new File(dir, fileName);
    }

    private static float clampScaleForPage(@NonNull PointF pageSize, float desiredScale) {
        float scale = desiredScale;
        float w = pageSize.x * scale;
        float h = pageSize.y * scale;
        float max = Math.max(w, h);
        if (max > MAX_PAGE_DIM_PX && max > 0f) {
            scale = scale * (MAX_PAGE_DIM_PX / max);
        }
        return Math.max(0.1f, scale);
    }
}
