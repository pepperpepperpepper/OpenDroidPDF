package org.opendroidpdf.app.sidecar;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.util.ArrayList;
import java.util.List;

final class SidecarAnnotationUndoOps {
    private SidecarAnnotationUndoOps() {
    }

    static void recordUndoInkAdded(@NonNull SidecarAnnotationUndo undo,
                                   @NonNull SidecarAnnotationSession session,
                                   int pageIndex,
                                   @NonNull List<SidecarInkStroke> inserted) {
        if (inserted.isEmpty()) return;
        ArrayList<String> ids = new ArrayList<>();
        for (SidecarInkStroke s : inserted) {
            if (s != null && s.id != null) ids.add(s.id);
        }
        if (ids.isEmpty()) return;
        undo.pushDual(
                () -> {
                    for (String id : ids) {
                        if (id == null) continue;
                        session.removeInkStroke(pageIndex, id);
                    }
                },
                () -> {
                    for (SidecarInkStroke s : inserted) {
                        if (s == null) continue;
                        session.restoreInkStroke(s);
                    }
                }
        );
    }

    static void recordUndoInkDeleted(@NonNull SidecarAnnotationUndo undo,
                                     @NonNull SidecarAnnotationSession session,
                                     int pageIndex,
                                     @NonNull List<SidecarInkStroke> removed) {
        if (removed.isEmpty()) return;
        ArrayList<String> ids = new ArrayList<>();
        for (SidecarInkStroke s : removed) {
            if (s != null && s.id != null) ids.add(s.id);
        }
        if (ids.isEmpty()) return;
        undo.pushDual(
                () -> {
                    for (SidecarInkStroke s : removed) {
                        if (s == null) continue;
                        session.restoreInkStroke(s);
                    }
                },
                () -> {
                    for (String id : ids) {
                        if (id == null) continue;
                        session.removeInkStroke(pageIndex, id);
                    }
                }
        );
    }

    static void recordUndoInkUpdated(@NonNull SidecarAnnotationUndo undo,
                                     @NonNull SidecarAnnotationSession session,
                                     int pageIndex,
                                     @NonNull List<SidecarInkStroke> original,
                                     @NonNull List<SidecarInkStroke> updated) {
        if (original.isEmpty() || updated.isEmpty()) return;
        undo.pushDual(
                () -> session.upsertInkStrokes(pageIndex, original),
                () -> session.upsertInkStrokes(pageIndex, updated)
        );
    }

    static void recordUndoInkReplaced(@NonNull SidecarAnnotationUndo undo,
                                      @NonNull SidecarAnnotationSession session,
                                      int pageIndex,
                                      @NonNull SidecarInkStroke original,
                                      @NonNull List<SidecarInkStroke> inserted) {
        ArrayList<String> insertedIds = new ArrayList<>();
        for (SidecarInkStroke s : inserted) {
            if (s != null && s.id != null) insertedIds.add(s.id);
        }
        undo.pushDual(
                () -> {
                    for (String id : insertedIds) {
                        if (id == null) continue;
                        session.removeInkStroke(pageIndex, id);
                    }
                    session.restoreInkStroke(original);
                },
                () -> {
                    if (original.id != null) {
                        session.removeInkStroke(pageIndex, original.id);
                    }
                    for (SidecarInkStroke s : inserted) {
                        if (s == null) continue;
                        session.restoreInkStroke(s);
                    }
                }
        );
    }

    static void recordUndoHighlightAdded(@NonNull SidecarAnnotationUndo undo,
                                         @NonNull SidecarAnnotationSession session,
                                         @NonNull SidecarHighlight highlight) {
        undo.pushDual(
                () -> session.removeHighlight(highlight.pageIndex, highlight.id),
                () -> session.restoreHighlight(highlight)
        );
    }

    static void recordUndoHighlightDeleted(@NonNull SidecarAnnotationUndo undo,
                                           @NonNull SidecarAnnotationSession session,
                                           @NonNull SidecarHighlight highlight) {
        undo.pushDual(
                () -> session.restoreHighlight(highlight),
                () -> session.removeHighlight(highlight.pageIndex, highlight.id)
        );
    }

    static void recordUndoNoteAdded(@NonNull SidecarAnnotationUndo undo,
                                    @NonNull SidecarAnnotationSession session,
                                    @NonNull SidecarNote note) {
        undo.pushDual(
                () -> session.removeNote(note.pageIndex, note.id),
                () -> session.restoreNote(note)
        );
    }

    static void recordUndoNoteDeleted(@NonNull SidecarAnnotationUndo undo,
                                      @NonNull SidecarAnnotationSession session,
                                      @NonNull SidecarNote note) {
        undo.pushDual(
                () -> session.restoreNote(note),
                () -> session.removeNote(note.pageIndex, note.id)
        );
    }
}
