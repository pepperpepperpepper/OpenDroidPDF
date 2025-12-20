package org.opendroidpdf.app.document;

import android.net.Uri;
import android.util.Log;
import android.widget.Adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.services.RecentFilesService;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.services.recent.RecentEntry;
import org.opendroidpdf.app.services.recent.ViewportSnapshot;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Collects viewport + recent-files operations so OpenDroidPDFActivity can delegate
 * and shrink. This is a thin wrapper around ViewportHelper/RecentFilesController.
 */
public final class DocumentViewportController {
    private static final String TAG = "DocumentViewportController";

    public interface Host {
        @Nullable MuPDFReaderView getDocView();
        @Nullable RecentFilesService getRecentFilesService();
        @Nullable Uri getCoreUri();
        @Nullable MuPdfRepository getRepository();
        @NonNull DocumentType getCurrentDocumentType();
        @Nullable SidecarAnnotationProvider getSidecarAnnotationProviderOrNull();
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
        if (snapshot == null) return;

        // Reflow pagination is layout-dependent.
        if (host.getCurrentDocumentType() == DocumentType.EPUB) {
            String activeLayout = currentReflowLayoutProfileIdOrNull();
            String savedLayout = snapshot.layoutProfileId();
            if (activeLayout != null && savedLayout != null && !activeLayout.equals(savedLayout)) {
                // Prefer approximate restore using a document-wide progress fraction so the user
                // doesn't get dumped at the start after a relayout/orientation change.
                float progress = snapshot.docProgress01();
                if (doc != null && progress >= 0f) {
                    int approx = approximatePageIndexFromProgress01(doc, progress);
                    if (approx >= 0) {
                        Log.i(TAG, "Reflow layout mismatch; restoring approx page=" + approx +
                                " (p=" + progress + ") saved=" + savedLayout + " active=" + activeLayout);
                        doc.setDisplayedViewIndex(approx);
                        return;
                    }
                }

                Log.i(TAG, "Skipping viewport restore due to reflow layout mismatch saved=" + savedLayout + " active=" + activeLayout);
                return;
            }
        }

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
        if (doc == null || recent == null) return;

        ViewportSnapshot snapshot = ViewportHelper.snapshot(doc);
        if (snapshot == null) return;
        if (host.getCurrentDocumentType() == DocumentType.EPUB) {
            snapshot = snapshot.withDocProgress01(computeDocProgress01(doc, snapshot));
            String layoutProfileId = currentReflowLayoutProfileIdOrNull();
            if (layoutProfileId != null) snapshot = snapshot.withLayoutProfileId(layoutProfileId);
        }
        recent.saveViewport(DocumentIds.fromUri(uri), snapshot);
    }

    public void recordRecent(@Nullable Uri uri) {
        RecentFilesService recent = host.getRecentFilesService();
        MuPdfRepository repo = host.getRepository();
        if (recent == null || uri == null || repo == null) return;
        String docId = DocumentIds.fromUri(uri);
        String uriString = uri.toString();
        MuPDFReaderView doc = host.getDocView();
        ViewportSnapshot vp = ViewportHelper.snapshot(doc);
        if (vp != null) {
            if (host.getCurrentDocumentType() == DocumentType.EPUB && doc != null) {
                vp = vp.withDocProgress01(computeDocProgress01(doc, vp));
                String layoutProfileId = currentReflowLayoutProfileIdOrNull();
                if (layoutProfileId != null) vp = vp.withLayoutProfileId(layoutProfileId);
            }
        }
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

    @Nullable
    private String currentReflowLayoutProfileIdOrNull() {
        if (host.getCurrentDocumentType() != DocumentType.EPUB) return null;
        SidecarAnnotationProvider provider = host.getSidecarAnnotationProviderOrNull();
        if (provider instanceof SidecarAnnotationSession) {
            return ((SidecarAnnotationSession) provider).layoutProfileId();
        }
        return null;
    }

    private static float computeDocProgress01(@NonNull MuPDFReaderView doc, @NonNull ViewportSnapshot snapshot) {
        Adapter adapter = doc.getAdapter();
        int count = adapter != null ? adapter.getCount() : 0;
        if (count <= 1) return 0f;

        float withinPage = snapshot.normalizedYScroll();
        if (withinPage < 0f) withinPage = 0f;
        if (withinPage > 0.999f) withinPage = 0.999f;

        int denom = Math.max(1, count - 1);
        float p = (snapshot.page() + withinPage) / (float) denom;
        if (p < 0f) return 0f;
        if (p > 1f) return 1f;
        return p;
    }

    private static int approximatePageIndexFromProgress01(@NonNull MuPDFReaderView doc, float progress01) {
        Adapter adapter = doc.getAdapter();
        int count = adapter != null ? adapter.getCount() : 0;
        if (count <= 0) return -1;
        if (count == 1) return 0;

        float p = progress01;
        if (p < 0f) p = 0f;
        if (p > 1f) p = 1f;

        int denom = Math.max(1, count - 1);
        int page = Math.round(p * denom);
        if (page < 0) page = 0;
        if (page >= count) page = count - 1;
        return page;
    }
}
