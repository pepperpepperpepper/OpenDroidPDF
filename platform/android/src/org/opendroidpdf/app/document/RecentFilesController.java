package org.opendroidpdf.app.document;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.net.Uri;
import android.view.Display;
import android.view.WindowManager;

import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.PdfThumbnailManager;
import org.opendroidpdf.RecentFile;
import org.opendroidpdf.RecentFilesList;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Extracted from OpenDroidPDFActivity to manage recent-files bookkeeping,
 * viewport persistence, and background thumbnail generation.
 */
public final class RecentFilesController {
    private final Context context;
    private final MuPdfRepository repository; // may be null until a doc is open
    private final MuPdfController controller; // may be null until a doc is open

    // Background render state moved out of the Activity
    private kotlinx.coroutines.Job renderThumbnailJob = null;
    private MuPDFCore.Cookie renderThumbnailCookie = null;

    public RecentFilesController(Context context,
                                 MuPdfRepository repository,
                                 MuPdfController controller) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.controller = controller;
    }

    public void shutdown() {
        cancelRenderThumbnailJob();
    }

    public void saveViewport(MuPDFReaderView docView, SharedPreferences.Editor edit, String path) {
        if (docView == null || edit == null || path == null) return;
        edit.putInt("page"+path, docView.getSelectedItemPosition());
        edit.putFloat("normalizedscale"+path, docView.getNormalizedScale());
        edit.putFloat("normalizedxscroll"+path, docView.getNormalizedXScroll());
        edit.putFloat("normalizedyscroll"+path, docView.getNormalizedYScroll());
    }

    public void setViewport(MuPDFReaderView docView, int page, float normalizedscale, float normalizedxscroll, float normalizedyscroll) {
        if (docView == null) return;
        docView.setDisplayedViewIndex(page);
        docView.setNormalizedScale(normalizedscale);
        docView.setNormalizedScroll(normalizedxscroll, normalizedyscroll);
    }

    public void restoreViewport(MuPDFReaderView docView, SharedPreferences prefs, Uri uri) {
        if (docView == null || prefs == null || uri == null) return;
        setViewport(
                docView,
                prefs.getInt("page"+uri.toString(), 0),
                prefs.getFloat("normalizedscale"+uri.toString(), 0.0f),
                prefs.getFloat("normalizedxscroll"+uri.toString(), 0.0f),
                prefs.getFloat("normalizedyscroll"+uri.toString(), 0.0f));
    }

    public void saveViewportAndRecentFiles(MuPDFReaderView docView,
                                           SharedPreferences prefs,
                                           Uri uri) {
        if (uri == null || prefs == null) return;
        SharedPreferences.Editor edit = prefs.edit();
        saveRecentFiles(prefs, edit, uri);
        saveViewport(docView, edit, uri.toString());
        edit.apply();
    }

    public void saveRecentFiles(SharedPreferences prefs,
                                SharedPreferences.Editor edit,
                                Uri uri) {
        if (prefs == null || edit == null || uri == null || repository == null) return;

        // Read, update, and persist the recent-files list.
        final RecentFilesList recentFilesList = new RecentFilesList(context, prefs);
        final RecentFile recentFile = new RecentFile(uri.toString(), repository.getDocumentName());
        recentFilesList.push(recentFile);
        recentFilesList.writeTo(edit);
        edit.apply();

        // Generate a thumbnail in the background.
        cancelRenderThumbnailJob();
        if (controller == null) return;

        final PdfThumbnailManager thumbnailManager = new PdfThumbnailManager(context, controller);

        // Precompute target size using current display metrics.
        int bmWidth;
        int bmHeight;
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm != null ? wm.getDefaultDisplay() : null;
        if (display != null) {
            if (android.os.Build.VERSION.SDK_INT < 13) {
                bmWidth = Math.min(display.getWidth(), display.getHeight());
            } else {
                Point size = new Point();
                display.getSize(size);
                bmWidth = Math.min(size.x, size.y);
            }
        } else {
            bmWidth = 400; // safe fallback
        }
        bmHeight = (int)((float)bmWidth*0.5);

        renderThumbnailCookie = repository != null ? repository.newRenderCookie() : null;
        final int targetW = bmWidth;
        final int targetH = bmHeight;
        final RecentFile targetRecent = recentFile;
        final MuPDFCore.Cookie cookie = renderThumbnailCookie;
        renderThumbnailJob = AppCoroutines.launchIo(AppCoroutines.ioScope(), new Runnable() {
            @Override public void run() {
                try {
                    String thumb = thumbnailManager.generate(targetW, targetH, cookie);
                    if (thumb != null && cookie != null && !cookie.aborted()) {
                        targetRecent.setThumbnailString(thumb);
                        SharedPreferences.Editor e2 = prefs.edit();
                        recentFilesList.writeTo(e2);
                        e2.apply();
                    }
                } catch (Throwable ignore) {
                } finally {
                    if (cookie != null) {
                        try { cookie.destroy(); } catch (Throwable ignore) {}
                    }
                }
            }
        });
    }

    public void cancelRenderThumbnailJob() {
        if (renderThumbnailJob != null) {
            renderThumbnailJob.cancel(null);
            renderThumbnailJob = null;
        }
        if (renderThumbnailCookie != null) {
            try { renderThumbnailCookie.abort(); } catch (Throwable ignore) {}
            try { renderThumbnailCookie.destroy(); } catch (Throwable ignore) {}
            renderThumbnailCookie = null;
        }
    }
}
