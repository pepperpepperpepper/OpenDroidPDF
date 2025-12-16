package org.opendroidpdf.app.services.search;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

/** Session API exposed to UI/toolbars; hides SearchTaskManager internals. */
public interface SearchSession {
    void start(SearchRequest request);
    void stop();
    boolean isActive();
    void setListener(@Nullable SearchListener listener);

    // --- Query state (single source of truth for search UI) ---
    @NonNull CharSequence latestQuery();
    void setLatestQuery(@NonNull CharSequence query);

    @NonNull CharSequence lastSubmittedQuery();
    void setLastSubmittedQuery(@NonNull CharSequence query);
}
