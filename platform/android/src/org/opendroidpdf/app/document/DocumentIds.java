package org.opendroidpdf.app.document;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Canonical document identifier derivation.
 *
 * <p>Today this is intentionally {@code uri.toString()} to preserve compatibility with the
 * existing persisted recent/viewport keys. All callers should route docId derivation through
 * this class so we can evolve the scheme later without split-brain IDs.</p>
 */
public final class DocumentIds {
    private DocumentIds() {}

    @NonNull
    public static String fromUri(@NonNull Uri uri) {
        return uri.toString();
    }

    @Nullable
    public static String fromUriOrNull(@Nullable Uri uri) {
        return uri != null ? uri.toString() : null;
    }
}

