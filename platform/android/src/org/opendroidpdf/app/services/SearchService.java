package org.opendroidpdf.app.services;

import androidx.annotation.Nullable;

import org.opendroidpdf.SearchTaskManager;

/**
 * Boundary around document search so UI/toolbars depend on a small contract
 * rather than the concrete SearchTaskManager implementation.
 */
public interface SearchService {
    void attachManager(@Nullable SearchTaskManager manager);
    void clearManager();
    void start(String query, int direction, int startPage);
    void stop();
    boolean hasActiveManager();
    @Nullable SearchTaskManager manager();
}
