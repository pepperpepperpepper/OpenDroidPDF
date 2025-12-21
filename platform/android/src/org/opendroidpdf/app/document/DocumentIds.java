package org.opendroidpdf.app.document;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Canonical document identifier derivation.
 *
 * <p><b>Compatibility:</b> prior releases used {@code uri.toString()} as the persisted docId
 * (recents/viewports). Newer code should prefer a content-derived id (see
 * {@link DocumentIdentityResolver}) and treat this class as the legacy id provider.</p>
 */
public final class DocumentIds {
    private DocumentIds() {}

    @NonNull
    public static String fromUri(@NonNull Uri uri) {
        return uri.toString();
    }

    @NonNull
    public static String legacyFromUri(@NonNull Uri uri) {
        return uri.toString();
    }

    @Nullable
    public static String fromUriOrNull(@Nullable Uri uri) {
        return uri != null ? uri.toString() : null;
    }
}
