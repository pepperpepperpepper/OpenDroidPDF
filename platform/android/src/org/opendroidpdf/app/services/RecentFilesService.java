package org.opendroidpdf.app.services;

import androidx.annotation.Nullable;

import org.opendroidpdf.app.services.recent.RecentEntry;
import org.opendroidpdf.app.services.recent.ViewportSnapshot;

import java.util.List;

/**
 * Service boundary for recent-files tracking and viewport persistence.
 * Android/UI-free: callers pass POJOs (RecentEntry/ViewportSnapshot),
 * not Views, Uris, or SharedPreferences.
 */
public interface RecentFilesService {
    void shutdown();

    void recordRecent(RecentEntry entry);

    List<RecentEntry> listRecents();

    void saveViewport(String docId, ViewportSnapshot viewport);

    @Nullable ViewportSnapshot restoreViewport(String docId);

    void cancelThumbnailJob();
}
