package org.opendroidpdf.app.bookmarks;

import androidx.annotation.NonNull;

import java.util.List;

public interface BookmarksStore {
    @NonNull List<DocumentBookmark> list(@NonNull String docId);
    @NonNull DocumentBookmark insert(@NonNull String docId, int pageIndex, @NonNull String title);
    void rename(@NonNull String docId, @NonNull String bookmarkId, @NonNull String newTitle);
    void delete(@NonNull String docId, @NonNull String bookmarkId);
    void migrateDocId(@NonNull String legacyDocId, @NonNull String newDocId);
}

