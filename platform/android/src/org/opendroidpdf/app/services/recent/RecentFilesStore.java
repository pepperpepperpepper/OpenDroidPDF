package org.opendroidpdf.app.services.recent;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Persistence boundary for recent entries and viewport snapshots.
 * Implementations may use SharedPreferences, a database, etc.
 */
public interface RecentFilesStore {
    List<RecentEntry> loadRecents();
    void persistRecents(List<RecentEntry> entries);

    void saveViewport(String docId, ViewportSnapshot snapshot);
    @Nullable ViewportSnapshot loadViewport(String docId);
}
