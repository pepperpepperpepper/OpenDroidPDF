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

    /** 0..1 when available, -1 otherwise. Useful for reflow relayout restore. */
    public static float computeDocProgress01(@Nullable MuPDFReaderView docView,
                                             @Nullable ViewportSnapshot snapshot) {
        if (docView == null || snapshot == null) return -1f;

        android.widget.Adapter adapter = docView.getAdapter();
        int count = adapter != null ? adapter.getCount() : 0;
        if (count <= 0) return -1f;
        if (count == 1) return 0f;

        float withinPage = snapshot.normalizedYScroll();
        if (withinPage < 0f) withinPage = 0f;
        if (withinPage > 0.999f) withinPage = 0.999f;

        int denom = Math.max(1, count - 1);
        float p = (snapshot.page() + withinPage) / (float) denom;
        if (p < 0f) return 0f;
        if (p > 1f) return 1f;
        return p;
    }

    public static int approximatePageIndexFromProgress01(@Nullable MuPDFReaderView docView, float progress01) {
        if (docView == null) return -1;
        android.widget.Adapter adapter = docView.getAdapter();
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
