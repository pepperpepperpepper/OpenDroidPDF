package org.opendroidpdf.app.document;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;
import org.opendroidpdf.core.MuPdfRepository;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Exports a PDF document with sidecar-backed annotations embedded as real PDF annotations.
 *
 * <p>This preserves vector/text content. It is preferred over {@link FlattenedPdfExporter} for PDFs
 * in sidecar mode (e.g., PDFs opened read-only).</p>
 */
final class SidecarPdfEmbedExporter {
    private static final int NOTE_ICON_COLOR = 0xFFFFD54F; // keep consistent with SidecarAnnotationRenderer

    private SidecarPdfEmbedExporter() {}

    static Uri export(@NonNull Context context,
                      @NonNull MuPdfRepository repo,
                      @NonNull SidecarAnnotationProvider sidecar,
                      @NonNull String baseName) throws Exception {
        Context appContext = context.getApplicationContext();
        Uri sourceUri = repo.getDocumentUri();
        if (sourceUri == null) {
            throw new IllegalStateException("document uri is not available");
        }

        File outFile = newExportFile(appContext, baseName);
        OpenDroidPDFCore core = null;
        try {
            core = new OpenDroidPDFCore(appContext, sourceUri);
            MuPdfRepository embedRepo = new MuPdfRepository(core);
            int pageCount = Math.max(0, embedRepo.getPageCount());

            boolean[] touched = new boolean[pageCount];

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                // Ink
                List<SidecarInkStroke> strokes = safe(sidecar.inkStrokesForPage(pageIndex));
                if (!strokes.isEmpty()) {
                    for (SidecarInkStroke s : strokes) {
                        if (s == null || s.points == null || s.points.length < 2) continue;
                        setInkStyle(core, s.color, s.thickness);
                        core.addInkAnnotation(pageIndex, new PointF[][]{s.points});
                        touched[pageIndex] = true;
                    }
                }

                // Highlights/underline/strikeout
                List<SidecarHighlight> highlights = safe(sidecar.highlightsForPage(pageIndex));
                if (!highlights.isEmpty()) {
                    for (SidecarHighlight h : highlights) {
                        if (h == null || h.type == null || h.quadPoints == null || h.quadPoints.length < 4) continue;
                        setMarkupStyle(core, h.type, h.color);
                        core.addMarkupAnnotation(pageIndex, h.quadPoints, h.type);
                        touched[pageIndex] = true;
                    }
                }

                // Notes
                List<SidecarNote> notes = safe(sidecar.notesForPage(pageIndex));
                if (!notes.isEmpty()) {
                    PointF pageSize = null;
                    try {
                        pageSize = embedRepo.getPageSize(pageIndex);
                    } catch (Throwable ignore) {
                    }
                    final float pageHeight = (pageSize != null) ? pageSize.y : 0f;

                    for (SidecarNote n : notes) {
                        if (n == null || n.bounds == null) continue;
                        PointF[] rectTwoPoints = noteRectPdfCoords(n.bounds, pageHeight);
                        if (rectTwoPoints == null) continue;
                        setTextNoteStyle(core);
                        core.addTextAnnotation(pageIndex, rectTwoPoints, n.text != null ? n.text : "");
                        touched[pageIndex] = true;
                    }
                }
            }

            // Ensure appearance streams are up-to-date before saving the new file.
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                if (!touched[pageIndex]) continue;
                try {
                    embedRepo.refreshAnnotationAppearance(pageIndex);
                } catch (Throwable ignore) {
                }
            }

            // Native saveAs returns 1 on success, 0 on failure.
            if (core.saveAs(outFile.getAbsolutePath()) == 0) {
                throw new IOException("native saveAs failed: " + outFile);
            }
            return FileProvider.getUriForFile(appContext, "org.opendroidpdf.fileprovider", outFile);
        } finally {
            if (core != null) {
                try { core.onDestroy(); } catch (Throwable ignore) {}
            }
        }
    }

    private static void setInkStyle(@NonNull OpenDroidPDFCore core, int color, float thickness) {
        core.setInkColor(rgb01(Color.red(color)), rgb01(Color.green(color)), rgb01(Color.blue(color)));
        core.setInkThickness(thickness);
    }

    private static void setMarkupStyle(@NonNull OpenDroidPDFCore core, @NonNull Annotation.Type type, int color) {
        float r = rgb01(Color.red(color));
        float g = rgb01(Color.green(color));
        float b = rgb01(Color.blue(color));
        switch (type) {
            case UNDERLINE:
                core.setUnderlineColor(r, g, b);
                break;
            case STRIKEOUT:
                core.setStrikeoutColor(r, g, b);
                break;
            case HIGHLIGHT:
            default:
                core.setHighlightColor(r, g, b);
                break;
        }
    }

    private static void setTextNoteStyle(@NonNull OpenDroidPDFCore core) {
        core.setTextAnnotIconColor(
                rgb01(Color.red(NOTE_ICON_COLOR)),
                rgb01(Color.green(NOTE_ICON_COLOR)),
                rgb01(Color.blue(NOTE_ICON_COLOR)));
    }

    private static float rgb01(int channel255) {
        return Math.max(0f, Math.min(1f, channel255 / 255f));
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
        if (safe.toLowerCase().endsWith(".pdf")) {
            safe = safe.substring(0, safe.length() - 4);
        }

        String fileName = safe + "_annotated_" + System.currentTimeMillis() + ".pdf";
        return new File(dir, fileName);
    }

    private static <T> List<T> safe(List<T> list) {
        return list != null ? list : java.util.Collections.emptyList();
    }

    /**
     * Converts a doc-relative (top-left origin) note bounds to the 2-point rect expected by
     * MuPDF's embedded text annotation API (PDF coords: bottom-left origin).
     */
    private static PointF[] noteRectPdfCoords(@NonNull RectF boundsDoc, float pageHeightDoc) {
        if (pageHeightDoc <= 0f) return null;
        float left = boundsDoc.left;
        float right = boundsDoc.right;
        float topDoc = boundsDoc.top;
        float bottomDoc = boundsDoc.bottom;

        float startY = pageHeightDoc - topDoc;
        float endY = pageHeightDoc - bottomDoc;

        return new PointF[]{
                new PointF(left, startY),
                new PointF(right, endY),
        };
    }
}

