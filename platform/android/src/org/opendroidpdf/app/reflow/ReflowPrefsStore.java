package org.opendroidpdf.app.reflow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Persistence for per-document reflow preferences. */
public interface ReflowPrefsStore {
    @NonNull ReflowPrefsSnapshot load(@NonNull String docId);
    void save(@NonNull String docId, @NonNull ReflowPrefsSnapshot snapshot);

    /**
     * Captures the exact layout profile under which annotations were created for this document.
     *
     * <p>Theme is included for convenience, but callers should treat it as paint-only.</p>
     */
    @Nullable ReflowAnnotatedLayout loadAnnotatedLayoutOrNull(@NonNull String docId);
    void saveAnnotatedLayout(@NonNull String docId, @NonNull ReflowAnnotatedLayout layout);
}
