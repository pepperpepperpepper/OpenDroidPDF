package org.opendroidpdf.app.assistant;

import androidx.annotation.Nullable;

/**
 * In-memory handoff between the document viewer and Assistant UI.
 *
 * <p>We avoid passing large text blobs through Intent extras (Binder limits) by stashing the
 * current context snapshot in-process.</p>
 */
public final class AssistantContextStore {
    private static volatile @Nullable AssistantContextSnapshot current;

    private AssistantContextStore() {}

    public static void set(@Nullable AssistantContextSnapshot snapshot) {
        current = snapshot;
    }

    @Nullable
    public static AssistantContextSnapshot get() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}

