package org.opendroidpdf.app.sidecar;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.reflow.ReflowPrefsSnapshot;
import org.opendroidpdf.app.reflow.ReflowPrefsStore;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SidecarNoteOps {
    private final String docId;
    @Nullable private final String layoutProfileId;
    private final SidecarAnnotationStore store;
    private final SidecarAnnotationUndo undo;
    @Nullable private final ReflowPrefsStore reflowPrefsStore;
    @Nullable private final ReflowPrefsSnapshot reflowPrefsSnapshot;

    private final Map<Integer, List<SidecarNote>> noteCache = new HashMap<>();

    SidecarNoteOps(@NonNull String docId,
                   @Nullable String layoutProfileId,
                   @NonNull SidecarAnnotationStore store,
                   @NonNull SidecarAnnotationUndo undo,
                   @Nullable ReflowPrefsStore reflowPrefsStore,
                   @Nullable ReflowPrefsSnapshot reflowPrefsSnapshot) {
        this.docId = docId;
        this.layoutProfileId = layoutProfileId;
        this.store = store;
        this.undo = undo;
        this.reflowPrefsStore = reflowPrefsStore;
        this.reflowPrefsSnapshot = reflowPrefsSnapshot;
    }

    void clearCache() { noteCache.clear(); }

    @NonNull
    List<SidecarNote> notesForPage(int pageIndex) {
        List<SidecarNote> cached = noteCache.get(pageIndex);
        if (cached != null) return cached;
        List<SidecarNote> loaded = store.listNotes(docId, pageIndex, layoutProfileId);
        List<SidecarNote> ro = Collections.unmodifiableList(loaded);
        noteCache.put(pageIndex, ro);
        return ro;
    }

    @NonNull
    SidecarNote addNote(int pageIndex,
                        @NonNull RectF bounds,
                        @Nullable String text,
                        long createdAtEpochMs) {
        float fontSize = (bounds.height()) * 0.18f;
        fontSize = Math.max(10.0f, Math.min(18.0f, fontSize));
        SidecarNote note = new SidecarNote(
                UUID.randomUUID().toString(),
                pageIndex,
                layoutProfileId,
                new RectF(bounds),
                text,
                createdAtEpochMs,
                SidecarNote.DEFAULT_COLOR,
                SidecarNote.DEFAULT_FONT_FAMILY,
                fontSize);
        store.insertNote(docId, note);
        SidecarReflowUtils.recordAnnotatedLayoutIfPossible(docId, layoutProfileId, reflowPrefsStore, reflowPrefsSnapshot);
        List<SidecarNote> current = new ArrayList<>(notesForPage(pageIndex));
        current.add(note);
        noteCache.put(pageIndex, Collections.unmodifiableList(current));
        return note;
    }

    @Nullable
    SidecarNote removeNote(int pageIndex, @NonNull String noteId) {
        List<SidecarNote> current = new ArrayList<>(notesForPage(pageIndex));
        SidecarNote removed = null;
        for (int i = 0; i < current.size(); i++) {
            SidecarNote n = current.get(i);
            if (n != null && noteId.equals(n.id)) {
                removed = n;
                current.remove(i);
                break;
            }
        }
        if (removed != null) {
            store.deleteNote(docId, noteId);
            noteCache.put(pageIndex, Collections.unmodifiableList(current));
        }
        return removed;
    }

    @Nullable
    SidecarNote updateNoteBounds(int pageIndex, @NonNull String noteId, @NonNull RectF bounds) {
        return updateNoteBounds(pageIndex, noteId, bounds, false);
    }

    @Nullable
    SidecarNote updateNoteBounds(int pageIndex, @NonNull String noteId, @NonNull RectF bounds, boolean markUserResized) {
        if (bounds == null) return null;
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        boolean userResized = prior.userResized || markUserResized;
        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    SidecarNote updateNoteText(int pageIndex, @NonNull String noteId, @Nullable String text) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    SidecarNote updateNoteStyle(int pageIndex, @NonNull String noteId, int color, float fontSize) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                color,
                prior.fontFamily,
                prior.fontStyleFlags,
                fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    SidecarNote updateNoteFontFamily(int pageIndex, @NonNull String noteId, int fontFamily) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        int fam = fontFamily;
        if (fam < 0 || fam > 2) fam = SidecarNote.DEFAULT_FONT_FAMILY;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                fam,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    SidecarNote updateNoteFontStyleFlags(int pageIndex, @NonNull String noteId, int fontStyleFlags) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        int flags = fontStyleFlags & 0x0F;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                flags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    SidecarNote updateNoteParagraph(int pageIndex, @NonNull String noteId, float lineHeight, float textIndentPt) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                lineHeight,
                textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    SidecarNote updateNoteBackground(int pageIndex, @NonNull String noteId, int backgroundColor, float backgroundOpacity) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        // Clamp opacity to a sane range; color is stored as-is (ARGB).
        float opacity = Math.max(0.0f, Math.min(1.0f, backgroundOpacity));
        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                backgroundColor,
                opacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    SidecarNote updateNoteBorder(int pageIndex,
                                @NonNull String noteId,
                                int borderColor,
                                float borderWidthPt,
                                boolean dashed,
                                float borderRadiusPt) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        float width = Math.max(0.0f, Math.min(24.0f, borderWidthPt));
        float radius = Math.max(0.0f, Math.min(48.0f, borderRadiusPt));
        int style = dashed ? 1 : 0;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                borderColor,
                width,
                style,
                radius,
                prior.lockPositionSize,
                prior.lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    SidecarNote updateNoteLocks(int pageIndex,
                               @NonNull String noteId,
                               boolean lockPositionSize,
                               boolean lockContents) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                lockPositionSize,
                lockContents,
                prior.rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    @Nullable
    SidecarNote updateNoteRotation(int pageIndex,
                                  @NonNull String noteId,
                                  int rotationDeg) {
        SidecarNote prior = findNote(pageIndex, noteId);
        if (prior == null) return null;

        if (rotationDeg < 0 || rotationDeg >= 360) {
            rotationDeg %= 360;
            if (rotationDeg < 0) rotationDeg += 360;
        }
        int snapped = ((rotationDeg + 45) / 90) * 90;
        if (snapped >= 360) snapped = 0;
        rotationDeg = snapped;

        SidecarNote updated = new SidecarNote(
                prior.id,
                prior.pageIndex,
                prior.layoutProfileId,
                new RectF(prior.bounds),
                prior.text,
                prior.createdAtEpochMs,
                prior.color,
                prior.fontFamily,
                prior.fontStyleFlags,
                prior.fontSize,
                prior.lineHeight,
                prior.textIndentPt,
                prior.userResized,
                prior.backgroundColor,
                prior.backgroundOpacity,
                prior.borderColor,
                prior.borderWidthPt,
                prior.borderStyle,
                prior.borderRadiusPt,
                prior.lockPositionSize,
                prior.lockContents,
                rotationDeg);
        store.insertNote(docId, updated);
        putNoteInCache(updated);
        recordUndoNoteUpdated(prior, updated);
        return updated;
    }

    void restoreNote(@NonNull SidecarNote note) {
        store.insertNote(docId, note);
        putNoteInCache(note);
    }

    private void recordUndoNoteUpdated(@NonNull SidecarNote prior, @NonNull SidecarNote updated) {
        undo.pushDual(
                () -> restoreNote(prior),
                () -> restoreNote(updated)
        );
    }

    @Nullable
    private SidecarNote findNote(int pageIndex, @NonNull String noteId) {
        List<SidecarNote> current = notesForPage(pageIndex);
        if (current == null || current.isEmpty()) return null;
        for (SidecarNote n : current) {
            if (n != null && noteId.equals(n.id)) return n;
        }
        return null;
    }

    private void putNoteInCache(@NonNull SidecarNote note) {
        List<SidecarNote> current = new ArrayList<>(notesForPage(note.pageIndex));
        boolean replaced = false;
        for (int i = 0; i < current.size(); i++) {
            SidecarNote n = current.get(i);
            if (n != null && note.id.equals(n.id)) {
                current.set(i, note);
                replaced = true;
                break;
            }
        }
        if (!replaced) current.add(note);
        noteCache.put(note.pageIndex, Collections.unmodifiableList(current));
    }
}

