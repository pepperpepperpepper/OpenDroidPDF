package org.opendroidpdf.app.services;

import androidx.annotation.Nullable;

import org.opendroidpdf.SearchTaskManager;

/**
 * Default SearchService backed by the existing SearchTaskManager lifecycle.
 */
public class SearchServiceImpl implements SearchService {
    @Nullable
    private SearchTaskManager manager;

    @Override
    public void attachManager(@Nullable SearchTaskManager manager) {
        // Stop any previous search to avoid leaks when switching documents.
        if (this.manager != null && this.manager != manager) {
            this.manager.stop();
        }
        this.manager = manager;
    }

    @Override
    public void clearManager() {
        attachManager(null);
    }

    @Override
    public void start(String query, int direction, int startPage) {
        if (manager != null) {
            manager.start(query, direction, startPage);
        }
    }

    @Override
    public void stop() {
        if (manager != null) {
            manager.stop();
        }
    }

    @Override
    public boolean hasActiveManager() {
        return manager != null;
    }

    @Override
    @Nullable
    public SearchTaskManager manager() {
        return manager;
    }
}
