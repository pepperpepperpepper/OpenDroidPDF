package org.opendroidpdf.app.document;

import android.net.Uri;
import android.util.Log;

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
 * Collects viewport + recent-files operations so the host activity can delegate
 * and shrink. This is a thin wrapper around ViewportHelper/RecentFilesController.
 */
public final class DocumentViewportController {
    private static final String TAG = "DocViewportCtrl";

    public interface Host {
        @Nullable MuPDFReaderView getDocView();
        @Nullable RecentFilesService getRecentFilesService();
        @Nullable Uri getCoreUri();
        @Nullable MuPdfRepository getRepository();
        @NonNull DocumentType getCurrentDocumentType();
        @Nullable SidecarAnnotationProvider getSidecarAnnotationProviderOrNull();
        @Nullable DocumentIdentity currentDocumentIdentityOrNull();
        @Nullable String getUserFacingDocumentDisplayNameOrNull();
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
        MuPdfRepository repo = host.getRepository();
        DocumentIdentity ident = host.currentDocumentIdentityOrNull();
        String docId = ident != null ? ident.docId() : DocumentIds.fromUri(uri);
        ViewportSnapshot snapshot = recent.restoreViewport(docId);
        if (snapshot == null && ident != null && !ident.docId().equals(ident.legacyDocId())) {
            // Migration: older versions keyed viewports by uriString. Copy on first access.
            snapshot = recent.restoreViewport(ident.legacyDocId());
            if (snapshot != null) {
                recent.saveViewport(docId, snapshot);
            }
        }
        if (snapshot == null) return;

        // Reflow pagination is layout-dependent.
        if (host.getCurrentDocumentType() == DocumentType.EPUB) {
            String activeLayout = currentReflowLayoutProfileIdOrNull();
            String savedLayout = snapshot.layoutProfileId();
            boolean knownLayoutMismatch = activeLayout != null && savedLayout != null && !activeLayout.equals(savedLayout);

            // Prefer a stable MuPDF reflow location (chapter/page) whenever we have one.
            // This avoids relying on layout-profile ids being ready at restore time (cold start),
            // and still handles true relayouts because page numbers can change while locations stay stable.
            long loc = snapshot.reflowLocation();
            if (doc != null && repo != null && loc != -1L) {
                int pageFromLoc = repo.pageNumberFromLocation(loc);
                if (pageFromLoc >= 0) {
                    boolean paginationChanged = pageFromLoc != snapshot.page();
                    if (knownLayoutMismatch || paginationChanged) {
                        Log.i(TAG, "Reflow restore by location page=" + pageFromLoc +
                                (knownLayoutMismatch ? " (layout mismatch)" : " (pagination changed)") +
                                " savedLayout=" + savedLayout + " activeLayout=" + activeLayout);
                        doc.setDisplayedViewIndex(pageFromLoc);
                        return;
                    }

                    // No mismatch detected and page agrees with location; keep the full snapshot restore.
                    snapshot = new ViewportSnapshot(
                            pageFromLoc,
                            snapshot.normalizedScale(),
                            snapshot.normalizedXScroll(),
                            snapshot.normalizedYScroll(),
                            snapshot.docProgress01(),
                            snapshot.layoutProfileId(),
                            loc);
                }
            }

            if (knownLayoutMismatch) {

                // Prefer approximate restore using a document-wide progress fraction so the user
                // doesn't get dumped at the start after a relayout/orientation change.
                float progress = snapshot.docProgress01();
                if (doc != null && progress >= 0f) {
                    int approx = ViewportHelper.approximatePageIndexFromProgress01(doc, progress);
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
        MuPdfRepository repo = host.getRepository();
        if (doc == null || recent == null) return;

        ViewportSnapshot snapshot = ViewportHelper.snapshot(doc);
        if (snapshot == null) return;
        if (host.getCurrentDocumentType() == DocumentType.EPUB) {
            float p = ViewportHelper.computeDocProgress01(doc, snapshot);
            if (p >= 0f) snapshot = snapshot.withDocProgress01(p);
            String layoutProfileId = currentReflowLayoutProfileIdOrNull();
            if (layoutProfileId != null) snapshot = snapshot.withLayoutProfileId(layoutProfileId);
            if (repo != null) {
                long loc = repo.locationFromPageNumber(snapshot.page());
                if (loc != -1L) snapshot = snapshot.withReflowLocation(loc);
            }
        }
        DocumentIdentity ident = host.currentDocumentIdentityOrNull();
        String docId = ident != null ? ident.docId() : DocumentIds.fromUri(uri);
        recent.saveViewport(docId, snapshot);
    }

    public void recordRecent(@Nullable Uri uri) {
        RecentFilesService recent = host.getRecentFilesService();
        MuPdfRepository repo = host.getRepository();
        if (recent == null || uri == null || repo == null) return;
        DocumentIdentity ident = host.currentDocumentIdentityOrNull();
        String docId = ident != null ? ident.docId() : DocumentIds.fromUri(uri);
        String uriString = uri.toString();
        MuPDFReaderView doc = host.getDocView();
        ViewportSnapshot vp = ViewportHelper.snapshot(doc);
        if (vp != null) {
            if (host.getCurrentDocumentType() == DocumentType.EPUB && doc != null) {
                float p = ViewportHelper.computeDocProgress01(doc, vp);
                if (p >= 0f) vp = vp.withDocProgress01(p);
                String layoutProfileId = currentReflowLayoutProfileIdOrNull();
                if (layoutProfileId != null) vp = vp.withLayoutProfileId(layoutProfileId);
                long loc = repo.locationFromPageNumber(vp.page());
                if (loc != -1L) vp = vp.withReflowLocation(loc);
            }
        }
        int lastPage = vp != null ? vp.page() : 0;
        String displayName = host.getUserFacingDocumentDisplayNameOrNull();
        if (displayName == null || displayName.isEmpty()) displayName = repo.getDocumentName();
        RecentEntry entry = new RecentEntry(
                docId,
                uriString,
                displayName,
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
}
