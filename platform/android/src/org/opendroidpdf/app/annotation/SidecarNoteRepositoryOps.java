package org.opendroidpdf.app.annotation;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

final class SidecarNoteRepositoryOps {
    @Nullable
    SidecarNote tryUpdateNoteBounds(@NonNull SidecarAnnotationSession sidecar,
                                    int page,
                                    @NonNull String noteId,
                                    @NonNull RectF boundsDoc,
                                    boolean markUserResized) {
        return tryUpdateNoteBounds(sidecar, page, noteId, boundsDoc, markUserResized, "Failed to update sidecar note bounds");
    }

    @Nullable
    SidecarNote tryUpdateNoteBoundsAutoFit(@NonNull SidecarAnnotationSession sidecar,
                                           int page,
                                           @NonNull String noteId,
                                           @NonNull RectF boundsDoc) {
        return tryUpdateNoteBounds(sidecar, page, noteId, boundsDoc, false, "Failed to auto-fit sidecar note bounds after edit");
    }

    @Nullable
    private SidecarNote tryUpdateNoteBounds(@NonNull SidecarAnnotationSession sidecar,
                                            int page,
                                            @NonNull String noteId,
                                            @NonNull RectF boundsDoc,
                                            boolean markUserResized,
                                            @NonNull String logMessage) {
        try {
            SidecarNote updated = sidecar.updateNoteBounds(page, noteId, boundsDoc, markUserResized);
            if (updated == null || updated.bounds == null) return null;
            return updated;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", logMessage, t);
            return null;
        }
    }

    @Nullable
    SidecarNote tryUpdateNoteText(@NonNull SidecarAnnotationSession sidecar,
                                  int page,
                                  @NonNull String noteId,
                                  @Nullable String text) {
        try {
            SidecarNote updated = sidecar.updateNoteText(page, noteId, text);
            if (updated == null || updated.bounds == null) return null;
            return updated;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note text", t);
            return null;
        }
    }

    @Nullable
    SidecarNote tryUpdateNoteStyle(@NonNull SidecarAnnotationSession sidecar,
                                   int page,
                                   @NonNull String noteId,
                                   int textColorArgb,
                                   float fontSize) {
        try {
            SidecarNote updated = sidecar.updateNoteStyle(page, noteId, textColorArgb, fontSize);
            if (updated == null || updated.bounds == null) return null;
            return updated;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note style", t);
            return null;
        }
    }

    @Nullable
    SidecarNote tryUpdateNoteBackground(@NonNull SidecarAnnotationSession sidecar,
                                        int page,
                                        @NonNull String noteId,
                                        int backgroundColorArgb,
                                        float opacity) {
        try {
            SidecarNote updated = sidecar.updateNoteBackground(page, noteId, backgroundColorArgb, opacity);
            if (updated == null || updated.bounds == null) return null;
            return updated;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note background", t);
            return null;
        }
    }

    @Nullable
    SidecarNote tryUpdateNoteBorder(@NonNull SidecarAnnotationSession sidecar,
                                    int page,
                                    @NonNull String noteId,
                                    int borderColorArgb,
                                    float widthPt,
                                    boolean dashed,
                                    float radiusPt) {
        try {
            SidecarNote updated = sidecar.updateNoteBorder(page, noteId, borderColorArgb, widthPt, dashed, radiusPt);
            if (updated == null || updated.bounds == null) return null;
            return updated;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note border", t);
            return null;
        }
    }

    @Nullable
    SidecarNote tryUpdateNoteLocks(@NonNull SidecarAnnotationSession sidecar,
                                   int page,
                                   @NonNull String noteId,
                                   boolean lockPositionSize,
                                   boolean lockContents) {
        try {
            SidecarNote updated = sidecar.updateNoteLocks(page, noteId, lockPositionSize, lockContents);
            if (updated == null || updated.bounds == null) return null;
            return updated;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note locks", t);
            return null;
        }
    }

    @Nullable
    SidecarNote tryUpdateNoteFontFamily(@NonNull SidecarAnnotationSession sidecar,
                                        int page,
                                        @NonNull String noteId,
                                        int fontFamily) {
        try {
            SidecarNote updated = sidecar.updateNoteFontFamily(page, noteId, fontFamily);
            if (updated == null || updated.bounds == null) return null;
            return updated;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note font family", t);
            return null;
        }
    }

    @Nullable
    SidecarNote tryUpdateNoteFontStyleFlags(@NonNull SidecarAnnotationSession sidecar,
                                            int page,
                                            @NonNull String noteId,
                                            int styleFlags) {
        try {
            SidecarNote updated = sidecar.updateNoteFontStyleFlags(page, noteId, styleFlags);
            if (updated == null || updated.bounds == null) return null;
            return updated;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note font style flags", t);
            return null;
        }
    }

    @Nullable
    SidecarNote tryUpdateNoteParagraph(@NonNull SidecarAnnotationSession sidecar,
                                       int page,
                                       @NonNull String noteId,
                                       float lineHeight,
                                       float textIndentPt) {
        try {
            SidecarNote updated = sidecar.updateNoteParagraph(page, noteId, lineHeight, textIndentPt);
            if (updated == null || updated.bounds == null) return null;
            return updated;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note paragraph", t);
            return null;
        }
    }

    @Nullable
    SidecarNote tryUpdateNoteRotation(@NonNull SidecarAnnotationSession sidecar,
                                      int page,
                                      @NonNull String noteId,
                                      int rotationDegrees) {
        try {
            SidecarNote updated = sidecar.updateNoteRotation(page, noteId, rotationDegrees);
            if (updated == null || updated.bounds == null) return null;
            return updated;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note rotation", t);
            return null;
        }
    }

    @Nullable
    SidecarNote tryAddNote(@NonNull SidecarAnnotationSession sidecar,
                           int page,
                           @NonNull RectF boundsDoc,
                           @Nullable String text,
                           long createdAtMs) {
        try {
            return sidecar.addNote(page, boundsDoc, text, createdAtMs);
        } catch (Throwable t) {
            return null;
        }
    }

    void bestEffortApplyNoteSnapshot(@NonNull SidecarAnnotationSession sidecar,
                                     int page,
                                     @NonNull String id,
                                     @NonNull SidecarNote note,
                                     @NonNull RectF boundsDoc) {
        try { sidecar.updateNoteStyle(page, id, note.color, note.fontSize); } catch (Throwable ignore) {}
        try { sidecar.updateNoteFontFamily(page, id, note.fontFamily); } catch (Throwable ignore) {}
        try { sidecar.updateNoteFontStyleFlags(page, id, note.fontStyleFlags); } catch (Throwable ignore) {}
        try { sidecar.updateNoteParagraph(page, id, note.lineHeight, note.textIndentPt); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBackground(page, id, note.backgroundColor, note.backgroundOpacity); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBorder(page, id, note.borderColor, note.borderWidthPt, note.borderStyle != 0, note.borderRadiusPt); } catch (Throwable ignore) {}
        try { sidecar.updateNoteLocks(page, id, note.lockPositionSize, note.lockContents); } catch (Throwable ignore) {}
        try { sidecar.updateNoteRotation(page, id, note.rotationDeg); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBounds(page, id, boundsDoc, note.userResized); } catch (Throwable ignore) {}
    }

    void bestEffortApplyPayload(@NonNull SidecarAnnotationSession sidecar,
                                int page,
                                @NonNull String id,
                                @NonNull TextAnnotationClipboard.Payload payload,
                                @NonNull RectF boundsDoc) {
        try { sidecar.updateNoteStyle(page, id, payload.textColorArgb, payload.fontSizePt); } catch (Throwable ignore) {}
        try { sidecar.updateNoteFontFamily(page, id, payload.fontFamily); } catch (Throwable ignore) {}
        try { sidecar.updateNoteFontStyleFlags(page, id, payload.fontStyleFlags); } catch (Throwable ignore) {}
        try { sidecar.updateNoteParagraph(page, id, payload.lineHeight, payload.textIndentPt); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBackground(page, id, payload.backgroundColorArgb, payload.backgroundOpacity); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBorder(page, id, payload.borderColorArgb, payload.borderWidthPt, payload.borderDashed, payload.borderRadiusPt); } catch (Throwable ignore) {}
        try { sidecar.updateNoteLocks(page, id, payload.lockPositionSize, payload.lockContents); } catch (Throwable ignore) {}
        try { sidecar.updateNoteRotation(page, id, payload.rotationDeg); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBounds(page, id, boundsDoc, payload.userResized); } catch (Throwable ignore) {}
    }
}

