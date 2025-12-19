package org.opendroidpdf.app.document;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.services.RecentFilesService;
import org.opendroidpdf.app.services.recent.RecentEntry;
import org.opendroidpdf.app.services.recent.ViewportSnapshot;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Collects viewport + recent-files operations so OpenDroidPDFActivity can delegate
 * and shrink. This is a thin wrapper around ViewportHelper/RecentFilesController.
 */
public final class DocumentViewportController {
    public interface Host {
        @Nullable MuPDFReaderView getDocView();
        @Nullable RecentFilesService getRecentFilesService();
        @Nullable Uri getCoreUri();
        @Nullable MuPdfRepository getRepository();
    }

    private final Host host;

    public DocumentViewportController(@NonNull Host host) {
        this.host = host;
    }

    public void restoreViewport() {
        MuPDFReaderView doc = host.getDocView();
        RecentFilesService recent = host.getRecentFilesService();
        Uri uri = host.getCoreUri();
        if (uri == null || recent == null) return;
        ViewportSnapshot snapshot = recent.restoreViewport(DocumentIds.fromUri(uri));
        ViewportHelper.applySnapshot(doc, snapshot);
    }

    public void setViewport(int page, float normalizedScale, float nx, float ny) {
        MuPDFReaderView doc = host.getDocView();
        ViewportHelper.applySnapshot(doc, new ViewportSnapshot(page, normalizedScale, nx, ny));
    }

    public void saveViewportAndRecentFiles(@Nullable Uri uri) {
        saveViewport(uri);
        recordRecent(uri);
    }

    public void saveViewport(@Nullable Uri uri) {
        if (uri == null) return;
        MuPDFReaderView doc = host.getDocView();
        RecentFilesService recent = host.getRecentFilesService();
        ViewportHelper.saveViewport(doc, recent, DocumentIds.fromUri(uri));
    }

    public void recordRecent(@Nullable Uri uri) {
        RecentFilesService recent = host.getRecentFilesService();
        MuPdfRepository repo = host.getRepository();
        if (recent == null || uri == null || repo == null) return;
        String docId = DocumentIds.fromUri(uri);
        String uriString = uri.toString();
        MuPDFReaderView doc = host.getDocView();
        ViewportSnapshot vp = ViewportHelper.snapshot(doc);
        int lastPage = vp != null ? vp.page() : 0;
        RecentEntry entry = new RecentEntry(
                docId,
                uriString,
                repo.getDocumentName(),
                System.currentTimeMillis(),
                lastPage,
                vp,
                null);
        recent.recordRecent(entry);
    }

    public void cancelRenderThumbnailJob() {
        RecentFilesService recent = host.getRecentFilesService();
        ViewportHelper.cancelThumbnailJob(recent);
    }
}
