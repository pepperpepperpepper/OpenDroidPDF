package org.opendroidpdf.app.services.recent;

import androidx.annotation.Nullable;

/**
 * POJO describing a recent document entry plus optional viewport snapshot and thumbnail string.
 */
public final class RecentEntry {
    private final String docId;
    private final String uriString;
    private final String displayName;
    private final long lastOpenedEpochMs;
    private final int lastPage;
    @Nullable private final ViewportSnapshot viewport;
    @Nullable private final String thumbnailString;

    public RecentEntry(String docId,
                       String uriString,
                       String displayName,
                       long lastOpenedEpochMs,
                       int lastPage,
                       @Nullable ViewportSnapshot viewport,
                       @Nullable String thumbnailString) {
        this.docId = docId;
        this.uriString = uriString;
        this.displayName = displayName;
        this.lastOpenedEpochMs = lastOpenedEpochMs;
        this.lastPage = lastPage;
        this.viewport = viewport;
        this.thumbnailString = thumbnailString;
    }

    public String docId() { return docId; }
    public String uriString() { return uriString; }
    public String displayName() { return displayName; }
    public long lastOpenedEpochMs() { return lastOpenedEpochMs; }
    public int lastPage() { return lastPage; }
    @Nullable public ViewportSnapshot viewport() { return viewport; }
    @Nullable public String thumbnailString() { return thumbnailString; }

    public RecentEntry withThumbnail(@Nullable String thumb) {
        return new RecentEntry(docId, uriString, displayName, lastOpenedEpochMs, lastPage, viewport, thumb);
    }
}
