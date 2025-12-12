package org.opendroidpdf.app.toolbar;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

/**
 * Process-wide cache for toolbar-related boolean state that is expensive or
 * unsafe to compute during menu preparation (e.g., walking the view tree).
 *
 * Currently only tracks whether an undo operation is available. Controllers
 * update this eagerly when their stacks mutate; the toolbar queries the cache
 * during onPrepareOptionsMenu.
 */
public final class ToolbarStateCache {
    public interface Listener {
        void onToolbarStatePossiblyChanged();
    }

    private static final ToolbarStateCache INSTANCE = new ToolbarStateCache();

    public static ToolbarStateCache get() { return INSTANCE; }

    private volatile boolean canUndo;
    private @Nullable Listener listener;

    private ToolbarStateCache() {}

    public boolean getCanUndo() { return canUndo; }

    @MainThread
    public void setCanUndo(boolean value) {
        boolean changed = (this.canUndo != value);
        this.canUndo = value;
        if (changed && listener != null) {
            try { listener.onToolbarStatePossiblyChanged(); } catch (Throwable ignore) {}
        }
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }
}

