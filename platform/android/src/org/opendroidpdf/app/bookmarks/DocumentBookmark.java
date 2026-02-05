package org.opendroidpdf.app.bookmarks;

import androidx.annotation.NonNull;

public final class DocumentBookmark {
    @NonNull public final String id;
    public final int pageIndex;
    @NonNull public final String title;
    public final long createdAtEpochMs;

    public DocumentBookmark(@NonNull String id, int pageIndex, @NonNull String title, long createdAtEpochMs) {
        if (id == null) throw new IllegalArgumentException("id required");
        if (title == null) throw new IllegalArgumentException("title required");
        this.id = id;
        this.pageIndex = Math.max(0, pageIndex);
        this.title = title;
        this.createdAtEpochMs = createdAtEpochMs;
    }
}

