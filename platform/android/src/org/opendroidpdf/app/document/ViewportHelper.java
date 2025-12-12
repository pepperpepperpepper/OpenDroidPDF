package org.opendroidpdf.app.document;

import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;

/** Lightweight wrappers to centralize viewport + recent-files calls. */
public final class ViewportHelper {
    private ViewportHelper() {}

    public static void restoreViewport(@Nullable MuPDFReaderView docView,
                                       @Nullable RecentFilesController recent,
                                       SharedPreferences prefs,
                                       @Nullable Uri coreUri) {
        if (docView == null || recent == null || coreUri == null) return;
        recent.restoreViewport(docView, prefs, coreUri);
    }

    public static void setViewport(@Nullable MuPDFReaderView docView,
                                   @Nullable RecentFilesController recent,
                                   SharedPreferences prefs,
                                   @Nullable Uri uri) {
        if (docView == null || recent == null || uri == null) return;
        recent.restoreViewport(docView, prefs, uri);
    }

    public static void setViewport(@Nullable MuPDFReaderView docView,
                                   @Nullable RecentFilesController recent,
                                   int page, float normalizedScale, float nx, float ny) {
        if (docView == null || recent == null) return;
        recent.setViewport(docView, page, normalizedScale, nx, ny);
    }

    public static void saveRecentFiles(@Nullable RecentFilesController recent,
                                       SharedPreferences prefs,
                                       SharedPreferences.Editor edit,
                                       @Nullable Uri uri) {
        if (recent == null || uri == null) return;
        recent.saveRecentFiles(prefs, edit, uri);
    }

    public static void cancelRenderThumbnailJob(@Nullable RecentFilesController recent) {
        if (recent != null) recent.cancelRenderThumbnailJob();
    }

    public static void saveViewportAndRecentFiles(@Nullable MuPDFReaderView docView,
                                                  @Nullable RecentFilesController recent,
                                                  SharedPreferences prefs,
                                                  @Nullable Uri uri) {
        if (docView == null || recent == null || uri == null) return;
        recent.saveViewportAndRecentFiles(docView, prefs, uri);
    }

    public static void saveViewport(@Nullable MuPDFReaderView docView,
                                    @Nullable RecentFilesController recent,
                                    SharedPreferences prefs,
                                    @Nullable Uri uri) {
        if (docView == null || recent == null || uri == null) return;
        SharedPreferences.Editor edit = prefs.edit();
        recent.saveViewport(docView, edit, uri.toString());
        edit.apply();
    }
}

