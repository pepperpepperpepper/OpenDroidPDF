package org.opendroidpdf.app.document;

import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.services.RecentFilesService;
import org.opendroidpdf.app.services.recent.ViewportSnapshot;

/** Lightweight wrappers to centralize viewport + recent-files calls. */
public final class ViewportHelper {
    private ViewportHelper() {}

    @Nullable
    public static ViewportSnapshot snapshot(@Nullable MuPDFReaderView docView) {
        if (docView == null) return null;
        return new ViewportSnapshot(
                docView.getSelectedItemPosition(),
                docView.getNormalizedScale(),
                docView.getNormalizedXScroll(),
                docView.getNormalizedYScroll());
    }

    public static void applySnapshot(@Nullable MuPDFReaderView docView,
                                     @Nullable ViewportSnapshot snapshot) {
        if (docView == null || snapshot == null) return;
        docView.setDisplayedViewIndex(snapshot.page());
        docView.setNormalizedScale(snapshot.normalizedScale());
        docView.setNormalizedScroll(snapshot.normalizedXScroll(), snapshot.normalizedYScroll());
    }

    public static void saveViewport(@Nullable MuPDFReaderView docView,
                                    @Nullable RecentFilesService recent,
                                    @Nullable String docId) {
        if (docView == null || recent == null || docId == null) return;
        ViewportSnapshot snapshot = snapshot(docView);
        if (snapshot != null) recent.saveViewport(docId, snapshot);
    }

    public static void cancelThumbnailJob(@Nullable RecentFilesService recent) {
        if (recent != null) recent.cancelThumbnailJob();
    }
}
