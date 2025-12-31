package org.opendroidpdf.app.document;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Immutable snapshot of the currently open document's identity and basic state.
 *
 * This is intentionally small and UI-friendly; complex view state (viewport, selection, etc.)
 * should live in their dedicated owners/controllers.
 */
public final class DocumentState {
    @Nullable private final Uri uri;
    @NonNull private final String displayName;
    private final int pageCount;
    private final boolean hasUnsavedChanges;
    private final boolean canSaveToCurrentUri;
    @NonNull private final DocumentOrigin origin;

    public DocumentState(@Nullable Uri uri,
                         @NonNull String displayName,
                         int pageCount,
                         boolean hasUnsavedChanges,
                         boolean canSaveToCurrentUri,
                         @NonNull DocumentOrigin origin) {
        this.uri = uri;
        this.displayName = displayName;
        this.pageCount = pageCount;
        this.hasUnsavedChanges = hasUnsavedChanges;
        this.canSaveToCurrentUri = canSaveToCurrentUri;
        this.origin = origin;
    }

    @Nullable public Uri uri() { return uri; }
    @NonNull public String displayName() { return displayName; }
    public int pageCount() { return pageCount; }
    public boolean hasUnsavedChanges() { return hasUnsavedChanges; }
    public boolean canSaveToCurrentUri() { return canSaveToCurrentUri; }
    @NonNull public DocumentOrigin origin() { return origin; }

    public static DocumentState empty(@NonNull String appName) {
        return new DocumentState(null, appName, 0, false, false, DocumentOrigin.NATIVE);
    }
}
