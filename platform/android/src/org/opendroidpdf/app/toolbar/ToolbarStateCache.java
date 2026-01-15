package org.opendroidpdf.app.toolbar;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

/**
 * Process-wide cache for toolbar-related boolean state that is expensive or
 * unsafe to compute during menu preparation (e.g., walking the view tree).
 *
 * Tracks whether undo/redo operations are available. Controllers update this
 * eagerly when their stacks mutate; the toolbar queries the cache during
 * onPrepareOptionsMenu.
 */
public final class ToolbarStateCache {
    public interface Listener {
        void onToolbarStatePossiblyChanged();
    }

    private static final ToolbarStateCache INSTANCE = new ToolbarStateCache();

    public static ToolbarStateCache get() { return INSTANCE; }

    private @Nullable Listener listener;

    private ToolbarStateCache() {}

    private volatile boolean canUndoInk;
    private volatile boolean canUndoText;
    private volatile boolean canRedoInk;
    private volatile boolean canRedoText;
    private volatile boolean canUndo;
    private volatile boolean canRedo;

    public boolean getCanUndo() { return canUndo; }

    public boolean getCanRedo() { return canRedo; }

    @MainThread
    public void setInkUndoRedo(boolean canUndo, boolean canRedo) {
        this.canUndoInk = canUndo;
        this.canRedoInk = canRedo;
        recompute();
    }

    @MainThread
    public void setTextUndoRedo(boolean canUndo, boolean canRedo) {
        this.canUndoText = canUndo;
        this.canRedoText = canRedo;
        recompute();
    }

    @MainThread
    private void recompute() {
        boolean nextUndo = canUndoInk || canUndoText;
        boolean nextRedo = canRedoInk || canRedoText;
        boolean changed = (this.canUndo != nextUndo) || (this.canRedo != nextRedo);
        this.canUndo = nextUndo;
        this.canRedo = nextRedo;
        if (changed && listener != null) {
            try { listener.onToolbarStatePossiblyChanged(); } catch (Throwable ignore) {}
        }
    }

    public void setListener(@Nullable Listener l) { this.listener = l; }
}
