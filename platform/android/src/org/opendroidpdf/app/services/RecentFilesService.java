package org.opendroidpdf.app.services;

import android.content.SharedPreferences;
import android.net.Uri;

import org.opendroidpdf.MuPDFReaderView;

/**
 * Service boundary for recent-files tracking and viewport persistence. Keeps
 * callers oblivious to the underlying controller implementation.
 */
public interface RecentFilesService {
    void shutdown();
    void saveViewport(MuPDFReaderView docView, SharedPreferences.Editor edit, String path);
    void setViewport(MuPDFReaderView docView, int page, float normalizedscale, float normalizedxscroll, float normalizedyscroll);
    void restoreViewport(MuPDFReaderView docView, SharedPreferences prefs, Uri uri);
    void saveViewportAndRecentFiles(MuPDFReaderView docView, SharedPreferences prefs, Uri uri);
    void saveRecentFiles(SharedPreferences prefs, SharedPreferences.Editor edit, Uri uri);
    void cancelRenderThumbnailJob();
}
