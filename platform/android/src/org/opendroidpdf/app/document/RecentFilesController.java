package org.opendroidpdf.app.document;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.PdfThumbnailManager;
import org.opendroidpdf.app.services.RecentFilesService;
import org.opendroidpdf.app.services.recent.RecentEntry;
import org.opendroidpdf.app.services.recent.RecentFilesStore;
import org.opendroidpdf.app.services.recent.ViewportSnapshot;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import androidx.annotation.Nullable;

/**
 * Extracted from OpenDroidPDFActivity to manage recent-files bookkeeping,
 * viewport persistence, and background thumbnail generation.
 */
public final class RecentFilesController implements RecentFilesService {
    private final Context context;
    private final MuPdfRepository repository; // may be null until a doc is open
    private final MuPdfController controller; // may be null until a doc is open
    private final RecentFilesStore store;

    // Background render state moved out of the Activity
    private kotlinx.coroutines.Job renderThumbnailJob = null;
    private MuPDFCore.Cookie renderThumbnailCookie = null;

    public RecentFilesController(Context context,
                                 MuPdfRepository repository,
                                 MuPdfController controller,
                                 RecentFilesStore store) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.controller = controller;
        this.store = store;
    }

    @Override
    public void shutdown() {
        cancelRenderThumbnailJob();
    }

    @Override
    public void recordRecent(RecentEntry entry) {
        if (entry == null || store == null) return;
        List<RecentEntry> recents = new ArrayList<>(store.loadRecents());
        // De-dup by canonical docId, but also by URI for compatibility with older entries that
        // used uriString as their docId.
        Iterator<RecentEntry> it = recents.iterator();
        while (it.hasNext()) {
            RecentEntry existing = it.next();
            if (existing == null) continue;
            if (existing.docId().equals(entry.docId()) || existing.uriString().equals(entry.uriString())) {
                it.remove();
            }
        }
        recents.add(0, entry);
        store.persistRecents(recents);
        maybeRenderThumbnail(entry);
    }

    @Override
    public List<RecentEntry> listRecents() {
        return store != null ? store.loadRecents() : new ArrayList<RecentEntry>(0);
    }

    @Override
    public void saveViewport(String docId, ViewportSnapshot viewport) {
        if (store == null || docId == null || viewport == null) return;
        store.saveViewport(docId, viewport);
    }

    @Override
    public @Nullable ViewportSnapshot restoreViewport(String docId) {
        if (store == null || docId == null) return null;
        return store.loadViewport(docId);
    }

    @Override
    public void cancelThumbnailJob() {
        cancelRenderThumbnailJob();
    }

    private void maybeRenderThumbnail(final RecentEntry entry) {
        cancelRenderThumbnailJob();
        if (controller == null || repository == null || entry == null) return;

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

        renderThumbnailCookie = repository.newRenderCookie();
        final int targetW = bmWidth;
        final int targetH = bmHeight;
        final MuPDFCore.Cookie cookie = renderThumbnailCookie;
        renderThumbnailJob = AppCoroutines.launchIo(AppCoroutines.ioScope(), new Runnable() {
            @Override public void run() {
                try {
                    String thumb = thumbnailManager.generate(targetW, targetH, cookie);
                    if (thumb != null && cookie != null && !cookie.aborted()) {
                        RecentEntry updated = entry.withThumbnail(thumb);
                        List<RecentEntry> recents = new ArrayList<>(store.loadRecents());
                        // replace matching docId with updated
                        for (int i = 0; i < recents.size(); i++) {
                            if (recents.get(i).docId().equals(updated.docId())) {
                                recents.set(i, updated);
                                break;
                            }
                        }
                        store.persistRecents(recents);
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

    private void cancelRenderThumbnailJob() {
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
