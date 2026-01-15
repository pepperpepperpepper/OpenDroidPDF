package org.opendroidpdf.app.annotation;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.toolbar.ToolbarStateCache;

import java.util.ArrayDeque;

/**
 * Small, page-scoped undo/redo stack for embedded text annotation operations.
 *
 * <p>Sidecar annotation undo/redo lives in {@link org.opendroidpdf.app.sidecar.SidecarAnnotationSession}
 * and is exposed through {@link org.opendroidpdf.app.drawing.InkController}.</p>
 */
public final class TextAnnotationUndoController {

    public interface Op {
        void undo();
        void redo();
    }

    private final ArrayDeque<Op> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Op> redoStack = new ArrayDeque<>();
    private long lastMutationUptimeMs = 0L;

    public boolean hasUndo() { return !undoStack.isEmpty(); }

    public boolean hasRedo() { return !redoStack.isEmpty(); }

    public long lastMutationUptimeMs() { return lastMutationUptimeMs; }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        bump();
        syncToolbar();
    }

    public void push(@NonNull Op op) {
        if (op == null) return;
        undoStack.push(op);
        redoStack.clear();
        bump();
        syncToolbar();
    }

    public boolean undoLast() {
        Op op = undoStack.poll();
        if (op == null) return false;
        op.undo();
        redoStack.push(op);
        bump();
        syncToolbar();
        return true;
    }

    public boolean redoLast() {
        Op op = redoStack.poll();
        if (op == null) return false;
        op.redo();
        undoStack.push(op);
        bump();
        syncToolbar();
        return true;
    }

    private void bump() {
        lastMutationUptimeMs = SystemClock.uptimeMillis();
    }

    private void syncToolbar() {
        ToolbarStateCache.get().setTextUndoRedo(hasUndo(), hasRedo());
    }
}

