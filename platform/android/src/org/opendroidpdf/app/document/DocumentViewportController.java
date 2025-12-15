package org.opendroidpdf.app.document;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.services.RecentFilesService;

/**
 * Collects viewport + recent-files operations so OpenDroidPDFActivity can delegate
 * and shrink. This is a thin wrapper around ViewportHelper/RecentFilesController.
 */
public final class DocumentViewportController {
    public interface Host {
        @NonNull Context getContext();
        @NonNull SharedPreferences getSharedPreferences(@NonNull String name, int mode);
        @Nullable MuPDFReaderView getDocView();
        @Nullable RecentFilesService getRecentFilesService();
        @Nullable Uri getCoreUri();
    }

    private final Host host;

    public DocumentViewportController(@NonNull Host host) {
        this.host = host;
    }

    public void restoreViewport() {
        MuPDFReaderView doc = host.getDocView();
        RecentFilesService recent = host.getRecentFilesService();
        Uri uri = host.getCoreUri();
        if (uri == null) return;
        SharedPreferences prefs = host.getSharedPreferences(org.opendroidpdf.SettingsActivity.SHARED_PREFERENCES_STRING,
                Context.MODE_MULTI_PROCESS);
        ViewportHelper.restoreViewport(doc, recent, prefs, uri);
    }

    public void setViewport(@NonNull Uri uri) {
        MuPDFReaderView doc = host.getDocView();
        RecentFilesService recent = host.getRecentFilesService();
        SharedPreferences prefs = host.getSharedPreferences(org.opendroidpdf.SettingsActivity.SHARED_PREFERENCES_STRING,
                Context.MODE_MULTI_PROCESS);
        ViewportHelper.setViewport(doc, recent, prefs, uri);
    }

    public void setViewport(int page, float normalizedScale, float nx, float ny) {
        MuPDFReaderView doc = host.getDocView();
        RecentFilesService recent = host.getRecentFilesService();
        ViewportHelper.setViewport(doc, recent, page, normalizedScale, nx, ny);
    }

    public void saveViewportAndRecentFiles(@Nullable Uri uri) {
        if (uri == null) return;
        MuPDFReaderView doc = host.getDocView();
        RecentFilesService recent = host.getRecentFilesService();
        SharedPreferences prefs = host.getSharedPreferences(org.opendroidpdf.SettingsActivity.SHARED_PREFERENCES_STRING,
                Context.MODE_MULTI_PROCESS);
        ViewportHelper.saveViewportAndRecentFiles(doc, recent, prefs, uri);
    }

    public void saveViewport(@Nullable Uri uri) {
        if (uri == null) return;
        MuPDFReaderView doc = host.getDocView();
        RecentFilesService recent = host.getRecentFilesService();
        SharedPreferences prefs = host.getSharedPreferences(org.opendroidpdf.SettingsActivity.SHARED_PREFERENCES_STRING,
                Context.MODE_MULTI_PROCESS);
        ViewportHelper.saveViewport(doc, recent, prefs, uri);
    }

    public void saveRecentFiles(@NonNull SharedPreferences prefs,
                                @NonNull SharedPreferences.Editor edit,
                                @Nullable Uri uri) {
        RecentFilesService recent = host.getRecentFilesService();
        if (uri == null) return;
        ViewportHelper.saveRecentFiles(recent, prefs, edit, uri);
    }

    public void cancelRenderThumbnailJob() {
        RecentFilesService recent = host.getRecentFilesService();
        ViewportHelper.cancelRenderThumbnailJob(recent);
    }
}
