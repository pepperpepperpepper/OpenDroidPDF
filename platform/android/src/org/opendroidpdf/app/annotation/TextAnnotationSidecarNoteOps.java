package org.opendroidpdf.app.annotation;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.R;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

final class TextAnnotationSidecarNoteOps {
    private final TextAnnotationPageDelegate router;
    private final TextAnnotationPageDelegate.Host host;

    TextAnnotationSidecarNoteOps(@NonNull TextAnnotationPageDelegate router,
                                 @NonNull TextAnnotationPageDelegate.Host host) {
        this.router = router;
        this.host = host;
    }

    boolean addTextAnnotationFromUi(@NonNull Annotation annot) {
        if (annot == null) return false;
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;

        float scale = host.scale();
        if (scale <= 0f) return false;
        float docH = host.viewHeightPx() / scale;
        final PointF[] quadPoints = TextAnnotationQuadPoints.fromBounds(true,
                annot.left,
                annot.top,
                annot.right,
                annot.bottom,
                docH);
        try {
            host.annotationUiController().addTextAnnotation(host.pageNumber(), quadPoints, annot.text, () -> {
                try { host.invalidateOverlay(); } catch (Throwable ignore) {}
                try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
            });
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    boolean commitSidecarNoteBounds(@NonNull String noteId, @NonNull RectF boundsDoc, boolean markUserResized) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        if (noteId == null || noteId.trim().isEmpty()) return false;
        if (boundsDoc == null) return false;

        SidecarNote prior = router.sidecarNoteById(noteId);
        if (prior != null && prior.lockPositionSize) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_position_size, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteBounds(host.pageNumber(), noteId, boundsDoc, markUserResized);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note bounds", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        try { host.sidecarSelectionController().updateSelectionBounds(noteId, updated.bounds); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    boolean updateSelectedSidecarNoteText(@Nullable String text) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;

        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        if (sel.id == null || sel.id.trim().isEmpty()) return false;
        SidecarNote prior = router.sidecarNoteById(sel.id);
        if (prior != null && prior.lockContents) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteText(host.pageNumber(), sel.id, text);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note text", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        // Acrobat-ish behavior: auto-fit/grow the sidecar note bounds as text is edited until the
        // user explicitly resizes the box (then respect width and only grow height).
        if (!updated.lockPositionSize) {
            RectF desiredBoundsDoc = computeAutoFitBoundsForSidecarNoteTextUpdate(updated, text);
            if (desiredBoundsDoc != null) {
                try {
                    SidecarNote fitted = sidecar.updateNoteBounds(host.pageNumber(), sel.id, desiredBoundsDoc, false);
                    if (fitted != null && fitted.bounds != null) {
                        updated = fitted;
                    }
                } catch (Throwable t) {
                    android.util.Log.e("MuPDFPageView", "Failed to auto-fit sidecar note bounds after edit", t);
                }
            }
        }

        try { host.sidecarSelectionController().updateSelectionBounds(sel.id, updated.bounds); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    /**
     * Updates a sidecar note's text by id (selection-independent).
     *
     * <p>Inline editing uses this so commits remain stable even if selection state changes
     * during focus transitions.</p>
     */
    boolean updateSidecarNoteTextById(@NonNull String noteId, @Nullable String text) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        if (noteId == null || noteId.trim().isEmpty()) return false;

        SidecarNote prior = router.sidecarNoteById(noteId);
        if (prior != null && prior.lockContents) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteText(host.pageNumber(), noteId, text);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note text", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        // Acrobat-ish behavior: auto-fit/grow the sidecar note bounds as text is edited until the
        // user explicitly resizes the box (then respect width and only grow height).
        if (!updated.lockPositionSize) {
            RectF desiredBoundsDoc = computeAutoFitBoundsForSidecarNoteTextUpdate(updated, text);
            if (desiredBoundsDoc != null) {
                try {
                    SidecarNote fitted = sidecar.updateNoteBounds(host.pageNumber(), noteId, desiredBoundsDoc, false);
                    if (fitted != null && fitted.bounds != null) {
                        updated = fitted;
                    }
                } catch (Throwable t) {
                    android.util.Log.e("MuPDFPageView", "Failed to auto-fit sidecar note bounds after edit", t);
                }
            }
        }

        try {
            SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
            if (sel != null && sel.kind == SidecarSelectionController.Kind.NOTE && noteId.equals(sel.id)) {
                host.sidecarSelectionController().updateSelectionBounds(noteId, updated.bounds);
            }
        } catch (Throwable ignore) {
        }
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    boolean applyTextStyleToSelectedTextAnnotation(float fontSize, int colorIndex) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        if (sel.id == null || sel.id.trim().isEmpty()) return false;
        SidecarNote prior = router.sidecarNoteById(sel.id);
        if (prior != null && prior.lockContents) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteStyle(host.pageNumber(), sel.id, ColorPalette.getHex(colorIndex), fontSize);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note style", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        try { host.sidecarSelectionController().updateSelectionBounds(sel.id, updated.bounds); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    boolean applyTextBackgroundToSelectedTextAnnotation(int colorIndex, float opacity) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        if (sel.id == null || sel.id.trim().isEmpty()) return false;
        SidecarNote prior = router.sidecarNoteById(sel.id);
        if (prior != null && prior.lockContents) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteBackground(host.pageNumber(), sel.id, ColorPalette.getHex(colorIndex), opacity);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note background", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        try { host.sidecarSelectionController().updateSelectionBounds(sel.id, updated.bounds); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    boolean applyTextBorderToSelectedTextAnnotation(int colorIndex, float widthPt, boolean dashed, float radiusPt) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        if (sel.id == null || sel.id.trim().isEmpty()) return false;
        SidecarNote prior = router.sidecarNoteById(sel.id);
        if (prior != null && prior.lockContents) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteBorder(host.pageNumber(), sel.id, ColorPalette.getHex(colorIndex), widthPt, dashed, radiusPt);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note border", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        try { host.sidecarSelectionController().updateSelectionBounds(sel.id, updated.bounds); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    boolean applyTextLocksToSelectedTextAnnotation(boolean lockPositionSize, boolean lockContents) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        if (sel.id == null || sel.id.trim().isEmpty()) return false;

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteLocks(host.pageNumber(), sel.id, lockPositionSize, lockContents);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note locks", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        try { host.sidecarSelectionController().updateSelectionBounds(sel.id, updated.bounds); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    boolean selectedTextAnnotationLockPositionSizeOrDefault() {
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        SidecarNote n = router.sidecarNoteById(sel.id);
        return n != null && n.lockPositionSize;
    }

    boolean selectedTextAnnotationLockContentsOrDefault() {
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        SidecarNote n = router.sidecarNoteById(sel.id);
        return n != null && n.lockContents;
    }

    float selectedTextAnnotationFontSizeOrDefault(float fallbackPt) {
        return fallbackPt;
    }

    int selectedTextAnnotationFontFamilyOrDefault(int fallbackFamily) {
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return fallbackFamily;
        SidecarNote n = router.sidecarNoteById(sel.id);
        if (n == null) return fallbackFamily;
        return TextFontFamily.normalize(n.fontFamily);
    }

    int selectedTextAnnotationStyleFlagsOrDefault(int fallbackFlags) {
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return fallbackFlags;
        SidecarNote n = router.sidecarNoteById(sel.id);
        if (n == null) return fallbackFlags;
        return TextStyleFlags.normalize(n.fontStyleFlags);
    }

    float selectedTextAnnotationLineHeightOrDefault(float fallback) {
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return fallback;
        SidecarNote n = router.sidecarNoteById(sel.id);
        if (n == null) return fallback;
        return n.lineHeight > 0f ? n.lineHeight : fallback;
    }

    float selectedTextAnnotationTextIndentPtOrDefault(float fallback) {
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return fallback;
        SidecarNote n = router.sidecarNoteById(sel.id);
        if (n == null) return fallback;
        return n.textIndentPt;
    }

    int selectedTextAnnotationRotationDegOrDefault() {
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return 0;
        SidecarNote n = router.sidecarNoteById(sel.id);
        if (n == null) return 0;
        int rot = n.rotationDeg;
        if (rot < 0 || rot >= 360) {
            rot %= 360;
            if (rot < 0) rot += 360;
        }
        return rot;
    }

    boolean applyTextFontFamilyToSelectedTextAnnotation(int fontFamily) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        if (sel.id == null || sel.id.trim().isEmpty()) return false;
        SidecarNote prior = router.sidecarNoteById(sel.id);
        if (prior != null && prior.lockContents) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }
        SidecarNote updated;
        try {
            updated = sidecar.updateNoteFontFamily(host.pageNumber(), sel.id, fontFamily);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note font family", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    boolean applyTextStyleFlagsToSelectedTextAnnotation(int styleFlags) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        if (sel.id == null || sel.id.trim().isEmpty()) return false;
        SidecarNote prior = router.sidecarNoteById(sel.id);
        if (prior != null && prior.lockContents) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteFontStyleFlags(host.pageNumber(), sel.id, styleFlags);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note font style flags", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    boolean applyTextParagraphToSelectedTextAnnotation(float lineHeight, float textIndentPt) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        if (sel.id == null || sel.id.trim().isEmpty()) return false;
        SidecarNote prior = router.sidecarNoteById(sel.id);
        if (prior != null && prior.lockContents) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteParagraph(host.pageNumber(), sel.id, lineHeight, textIndentPt);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note paragraph", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    boolean applyTextRotationToSelectedTextAnnotation(int rotationDegrees) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        SidecarSelectionController.Selection sel = host.sidecarSelectionController().selectionOrNull();
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        if (sel.id == null || sel.id.trim().isEmpty()) return false;
        SidecarNote prior = router.sidecarNoteById(sel.id);
        if (prior != null && prior.lockContents) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        SidecarNote updated;
        try {
            updated = sidecar.updateNoteRotation(host.pageNumber(), sel.id, rotationDegrees);
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update sidecar note rotation", t);
            return false;
        }
        if (updated == null || updated.bounds == null) return false;

        try { host.sidecarSelectionController().updateSelectionBounds(sel.id, updated.bounds); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    boolean duplicateSelectedTextAnnotation() {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        return duplicateSelectedSidecarNote(sidecar);
    }

    boolean copySelectedTextAnnotationToClipboard() {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;

        SidecarSelectionController.Selection sel = null;
        try { sel = host.sidecarSelectionController().selectionOrNull(); } catch (Throwable ignore) { sel = null; }
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;
        SidecarNote note = router.sidecarNoteById(sel.id);
        if (note == null || note.bounds == null) return false;

        String text = note.text != null ? note.text : "";
        TextAnnotationClipboard.set(new TextAnnotationClipboard.Payload(
                TextAnnotationClipboard.Kind.SIDECAR_NOTE,
                new RectF(note.bounds),
                text,
                note.fontSize,
                note.lineHeight,
                note.textIndentPt,
                note.fontFamily,
                note.fontStyleFlags,
                0 /* alignment */,
                note.rotationDeg,
                note.color,
                note.backgroundColor,
                note.backgroundOpacity,
                note.borderColor,
                note.borderWidthPt,
                note.borderStyle != 0,
                note.borderRadiusPt,
                note.lockPositionSize,
                note.lockContents,
                note.userResized));

        TextAnnotationPageDelegate.copyPlainTextToSystemClipboard(host.context(), text);
        return true;
    }

    boolean pasteFromClipboard(@NonNull TextAnnotationClipboard.Payload payload) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return false;
        if (payload == null) return false;
        return pasteSidecarFromClipboard(sidecar, payload);
    }

    private boolean duplicateSelectedSidecarNote(@NonNull SidecarAnnotationSession sidecar) {
        SidecarSelectionController.Selection sel = null;
        try { sel = host.sidecarSelectionController().selectionOrNull(); } catch (Throwable ignore) { sel = null; }
        if (sel == null || sel.kind != SidecarSelectionController.Kind.NOTE) return false;

        SidecarNote note = router.sidecarNoteById(sel.id);
        if (note == null || note.bounds == null) return false;

        float scale = host.scale();
        if (scale <= 0f) return false;
        float docW = host.viewWidthPx() / scale;
        float docH = host.viewHeightPx() / scale;

        RectF nextBounds = TextAnnotationPageDelegate.offsetAndClampDocBounds(host.resources(), scale, docW, docH, new RectF(note.bounds));
        if (nextBounds == null) return false;

        SidecarNote created;
        try {
            created = sidecar.addNote(host.pageNumber(), nextBounds, note.text, System.currentTimeMillis());
        } catch (Throwable t) {
            return false;
        }
        if (created == null || created.id == null || created.id.trim().isEmpty()) return false;

        final String id = created.id;
        final int page = host.pageNumber();
        try { sidecar.updateNoteStyle(page, id, note.color, note.fontSize); } catch (Throwable ignore) {}
        try { sidecar.updateNoteFontFamily(page, id, note.fontFamily); } catch (Throwable ignore) {}
        try { sidecar.updateNoteFontStyleFlags(page, id, note.fontStyleFlags); } catch (Throwable ignore) {}
        try { sidecar.updateNoteParagraph(page, id, note.lineHeight, note.textIndentPt); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBackground(page, id, note.backgroundColor, note.backgroundOpacity); } catch (Throwable ignore) {}
        try {
            sidecar.updateNoteBorder(page, id, note.borderColor, note.borderWidthPt, note.borderStyle != 0, note.borderRadiusPt);
        } catch (Throwable ignore) {}
        try { sidecar.updateNoteLocks(page, id, note.lockPositionSize, note.lockContents); } catch (Throwable ignore) {}
        try { sidecar.updateNoteRotation(page, id, note.rotationDeg); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBounds(page, id, nextBounds, note.userResized); } catch (Throwable ignore) {}

        try { host.sidecarSelectionController().selectNoteById(id); } catch (Throwable ignore) {}
        host.invalidateOverlay();
        return true;
    }

    private boolean pasteSidecarFromClipboard(@NonNull SidecarAnnotationSession sidecar,
                                             @NonNull TextAnnotationClipboard.Payload payload) {
        float scale = host.scale();
        if (scale <= 0f) return false;
        float docW = host.viewWidthPx() / scale;
        float docH = host.viewHeightPx() / scale;

        int offsetSteps = TextAnnotationClipboard.nextPasteIndex();
        RectF nextBounds = TextAnnotationPageDelegate.offsetAndClampDocBoundsWithSteps(host.resources(), scale, docW, docH, payload.boundsDoc, offsetSteps);
        if (nextBounds == null) return false;

        SidecarNote created;
        try {
            created = sidecar.addNote(host.pageNumber(), nextBounds, payload.text, System.currentTimeMillis());
        } catch (Throwable t) {
            return false;
        }
        if (created == null || created.id == null || created.id.trim().isEmpty()) return false;

        final String id = created.id;
        final int page = host.pageNumber();

        try { sidecar.updateNoteStyle(page, id, payload.textColorArgb, payload.fontSizePt); } catch (Throwable ignore) {}
        try { sidecar.updateNoteFontFamily(page, id, payload.fontFamily); } catch (Throwable ignore) {}
        try { sidecar.updateNoteFontStyleFlags(page, id, payload.fontStyleFlags); } catch (Throwable ignore) {}
        try { sidecar.updateNoteParagraph(page, id, payload.lineHeight, payload.textIndentPt); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBackground(page, id, payload.backgroundColorArgb, payload.backgroundOpacity); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBorder(page, id, payload.borderColorArgb, payload.borderWidthPt, payload.borderDashed, payload.borderRadiusPt); } catch (Throwable ignore) {}
        try { sidecar.updateNoteLocks(page, id, payload.lockPositionSize, payload.lockContents); } catch (Throwable ignore) {}
        try { sidecar.updateNoteRotation(page, id, payload.rotationDeg); } catch (Throwable ignore) {}
        try { sidecar.updateNoteBounds(page, id, nextBounds, payload.userResized); } catch (Throwable ignore) {}

        try { host.sidecarSelectionController().selectNoteById(id); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        try { host.inkController().refreshUndoState(); } catch (Throwable ignore) {}
        return true;
    }

    @Nullable
    private RectF computeAutoFitBoundsForSidecarNoteTextUpdate(@NonNull SidecarNote note, @Nullable String nextText) {
        if (host.sidecarSessionOrNull() == null) return null;
        if (note == null || note.bounds == null) return null;
        if (nextText == null || nextText.trim().isEmpty()) return null;
        float scale = host.scale();
        if (scale <= 0f) return null;
        float pageDocWidth = host.viewWidthPx() / scale;
        float pageDocHeight = host.viewHeightPx() / scale;
        if (pageDocWidth <= 0f || pageDocHeight <= 0f) return null;
        boolean allowWidthGrow = !note.userResized;
        // Sidecar notes store font sizes in doc units already; use a base dpi of 72 so the
        // FreeText fitter's pt->doc conversion becomes a no-op.
        return FreeTextBoundsFitter.compute(
                host.resources(),
                scale,
                pageDocWidth,
                pageDocHeight,
                note.bounds,
                nextText,
                note.fontSize,
                72,
                allowWidthGrow,
                false);
    }
}
