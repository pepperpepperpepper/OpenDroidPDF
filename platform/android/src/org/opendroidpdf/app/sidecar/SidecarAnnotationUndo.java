package org.opendroidpdf.app.sidecar;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;

final class SidecarAnnotationUndo {
    interface UndoOp {
        void undo();
        void redo();
    }

    private static final class DualOp implements UndoOp {
        private final Runnable undo;
        private final Runnable redo;

        DualOp(@NonNull Runnable undo, @NonNull Runnable redo) {
            this.undo = undo;
            this.redo = redo;
        }

        @Override public void undo() { undo.run(); }
        @Override public void redo() { redo.run(); }
    }

    private final ArrayDeque<UndoOp> undoStack = new ArrayDeque<>();
    private final ArrayDeque<UndoOp> redoStack = new ArrayDeque<>();

    void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    void push(@NonNull UndoOp op) {
        undoStack.push(op);
        redoStack.clear();
    }

    void pushDual(@NonNull Runnable undo, @NonNull Runnable redo) {
        push(new DualOp(undo, redo));
    }

    boolean hasUndo() { return !undoStack.isEmpty(); }

    boolean hasRedo() { return !redoStack.isEmpty(); }

    boolean undoLast() {
        UndoOp op = undoStack.poll();
        if (op == null) return false;
        op.undo();
        redoStack.push(op);
        return true;
    }

    boolean redoLast() {
        UndoOp op = redoStack.poll();
        if (op == null) return false;
        op.redo();
        undoStack.push(op);
        return true;
    }
}

