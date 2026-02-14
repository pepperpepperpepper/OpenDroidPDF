package org.opendroidpdf.app.annotation;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.core.MuPdfController;

final class TextAnnotationEmbeddedFreeTextOps {
    private final TextAnnotationPageDelegate router;
    private final TextAnnotationPageDelegate.Host host;
    private final TextAnnotationUndoController undoController;

    private final EmbeddedFreeTextRepositoryOps repoOps = new EmbeddedFreeTextRepositoryOps();
    private final EmbeddedFreeTextUndo undoOps;

    TextAnnotationEmbeddedFreeTextOps(@NonNull TextAnnotationPageDelegate router,
                                      @NonNull TextAnnotationPageDelegate.Host host,
                                      @NonNull TextAnnotationUndoController undoController) {
        this.router = router;
        this.host = host;
        this.undoController = undoController;
        this.undoOps = new EmbeddedFreeTextUndo(host, undoController, repoOps);
    }

    boolean commitTextAnnotationRectByObjectNumber(long objectId, @NonNull RectF boundsDoc, boolean markUserResized) {
        if (host.sidecarSessionOrNull() != null) return false;
        if (objectId <= 0L || boundsDoc == null) return false;

        final MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        if (repoOps.isFreeTextPositionLockedOrDefault(controller, host.pageNumber(), objectId, false)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_position_size, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        final Annotation hint = EmbeddedFreeTextUndo.findAnnotationByObjectNumber(host.embeddedAnnotationsOrNull(), objectId);
        final EmbeddedFreeTextSnapshot before = undoOps.snapshotByObjectNumber(objectId, hint);

        RectF normalized = undoOps.normalizeTextAnnotationBoundsForCommit(boundsDoc);
        if (normalized == null) return false;

        repoOps.updateAnnotationRectByObjectNumber(controller, host.pageNumber(), objectId, normalized);
        if (markUserResized) {
            try {
                repoOps.setFreeTextUserResizedByObjectNumber(controller, host.pageNumber(), objectId, true);
            } catch (Throwable ignore) {
            }
        }
        controller.markDocumentDirty();

        host.requestFullRedrawAfterNextAnnotationLoad();
        host.discardRenderedPage();
        host.loadAnnotations();

        RectF updated = new RectF(normalized);
        try {
            host.selectionManager().selectByObjectNumber(objectId, updated, host.selectionUiBridge().selectionBoxHost());
        } catch (Throwable ignore) {
            host.setAnnotationSelectionBox(updated);
        }

        if (before != null) {
            boolean nextUserResized = before.userResized || markUserResized;
            EmbeddedFreeTextSnapshot after = before.withBounds(updated, nextUserResized);
            if (!before.equals(after)) {
                undoController.push(undoOps.snapshotOp(before, after, objectId));
            }
        }
        return true;
    }

    boolean deleteEmbeddedFreeTextByObjectNumberWithUndo(long objectId) {
        if (host.sidecarSessionOrNull() != null) return false;
        if (objectId <= 0L) return false;
        Annotation hint = EmbeddedFreeTextUndo.findAnnotationByObjectNumber(host.embeddedAnnotationsOrNull(), objectId);
        EmbeddedFreeTextSnapshot snapshot = undoOps.snapshotByObjectNumber(objectId, hint);
        if (snapshot == null) return false;
        undoOps.deleteEmbeddedFreeTextByObjectNumber(objectId);
        undoController.push(undoOps.presenceOp(snapshot, -1L, false));
        return true;
    }

    boolean addTextAnnotationFromUiWithUndo(@NonNull Annotation annot) {
        if (annot == null) return false;
        if (host.sidecarSessionOrNull() != null) return false;

        final MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        float scale = host.scale();
        if (scale <= 0f) return false;
        final float docH = host.viewHeightPx() / scale;

        final RectF desiredBounds = undoOps.normalizeTextAnnotationBoundsForCommit(new RectF(annot));
        if (desiredBounds == null) return false;

        final String text = annot.text != null ? annot.text : "";
        final int page = host.pageNumber();
        final PointF[] rectTwoPoints = TextAnnotationQuadPoints.fromBounds(false,
                desiredBounds.left,
                desiredBounds.top,
                desiredBounds.right,
                desiredBounds.bottom,
                docH);

        AppCoroutines.launchIo(AppCoroutines.ioScope(), () -> {
            EmbeddedFreeTextRepositoryOps.CreatedFreeText created =
                    repoOps.tryAddFreeTextAnnotation(controller, page, rectTwoPoints, desiredBounds, text);

            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                host.requestFullRedrawAfterNextAnnotationLoad();
                host.discardRenderedPage();
                host.loadAnnotations();

                if (created.objectId > 0L) {
                    RectF b = created.annotation != null ? new RectF(created.annotation) : new RectF(desiredBounds);
                    try { host.selectionManager().selectByObjectNumber(created.objectId, b, host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore2) {}

                    EmbeddedFreeTextSnapshot snapshot = undoOps.snapshotByObjectNumber(created.objectId, created.annotation);
                    if (snapshot != null) {
                        undoController.push(undoOps.presenceOp(snapshot, created.objectId, true));
                    }
                } else {
                    try { host.setAnnotationSelectionBox(new RectF(desiredBounds)); } catch (Throwable ignore2) {}
                }
                host.invalidateOverlay();
            });
        });

        return true;
    }

    boolean applyTextStyleToSelectedTextAnnotation(float fontSize, int colorIndex) {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = selectedEmbeddedFreeTextOrNull();
        if (annot == null) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        if (repoOps.isFreeTextContentsLockedOrDefault(controller, host.pageNumber(), objectId, false)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        final EmbeddedFreeTextSnapshot before = undoOps.snapshotByObjectNumber(objectId, annot);

        float r = ColorPalette.getR(colorIndex);
        float g = ColorPalette.getG(colorIndex);
        float b = ColorPalette.getB(colorIndex);

        repoOps.updateFreeTextStyleByObjectNumber(controller, host.pageNumber(), objectId, fontSize, r, g, b);
        controller.markDocumentDirty();

        host.requestFullRedrawAfterNextAnnotationLoad();
        host.discardRenderedPage();
        host.loadAnnotations();
        host.invalidateOverlay();

        if (before != null) {
            EmbeddedFreeTextSnapshot after = undoOps.snapshotByObjectNumber(objectId, annot);
            if (after != null && !before.equals(after)) {
                undoController.push(undoOps.snapshotOp(before, after, objectId));
            }
        }
        return true;
    }

    boolean applyTextBackgroundToSelectedTextAnnotation(int colorIndex, float opacity) {
        float r = ColorPalette.getR(colorIndex);
        float g = ColorPalette.getG(colorIndex);
        float b = ColorPalette.getB(colorIndex);
        return updateSelectedEmbeddedFreeTextWithUndoAndRedraw(
                "Failed to update FreeText background",
                true,
                true,
                (controller, page, objectId) -> repoOps.updateFreeTextBackgroundByObjectNumber(controller, page, objectId, r, g, b, opacity));
    }

    boolean applyTextBorderToSelectedTextAnnotation(int colorIndex, float widthPt, boolean dashed, float radiusPt) {
        float r = ColorPalette.getR(colorIndex);
        float g = ColorPalette.getG(colorIndex);
        float b = ColorPalette.getB(colorIndex);
        return updateSelectedEmbeddedFreeTextWithUndoAndRedraw(
                "Failed to update FreeText border",
                true,
                true,
                (controller, page, objectId) -> repoOps.updateFreeTextBorderByObjectNumber(controller, page, objectId, r, g, b, widthPt, dashed, radiusPt));
    }

    boolean applyTextLocksToSelectedTextAnnotation(boolean lockPositionSize, boolean lockContents) {
        return updateSelectedEmbeddedFreeTextWithUndoAndRedraw(
                "Failed to update FreeText locks",
                false,
                true,
                (controller, page, objectId) -> repoOps.updateFreeTextLocksByObjectNumber(controller, page, objectId, lockPositionSize, lockContents));
    }

    boolean selectedTextAnnotationLockPositionSizeOrDefault() {
        if (host.sidecarSessionOrNull() != null) return false;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return false;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        return repoOps.isFreeTextPositionLockedOrDefault(controller, host.pageNumber(), annot.objectNumber, false);
    }

    boolean selectedTextAnnotationLockContentsOrDefault() {
        if (host.sidecarSessionOrNull() != null) return false;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return false;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        return repoOps.isFreeTextContentsLockedOrDefault(controller, host.pageNumber(), annot.objectNumber, false);
    }

    int selectedTextAnnotationAlignmentOrDefault() {
        if (host.sidecarSessionOrNull() != null) return 0;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return 0;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return 0;
        return repoOps.getFreeTextAlignmentOrDefaultNormalized(controller, host.pageNumber(), annot.objectNumber, 0);
    }

    float selectedTextAnnotationFontSizeOrDefault(float fallbackPt) {
        if (host.sidecarSessionOrNull() != null) return fallbackPt;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallbackPt;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return fallbackPt;
        return repoOps.getFreeTextFontSizeOrDefaultValidated(controller, host.pageNumber(), annot.objectNumber, fallbackPt);
    }

    int selectedTextAnnotationFontFamilyOrDefault(int fallbackFamily) {
        if (host.sidecarSessionOrNull() != null) return fallbackFamily;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallbackFamily;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return fallbackFamily;
        return repoOps.getFreeTextFontFamilyOrDefaultNormalized(controller, host.pageNumber(), annot.objectNumber, fallbackFamily);
    }

    int selectedTextAnnotationStyleFlagsOrDefault(int fallbackFlags) {
        if (host.sidecarSessionOrNull() != null) return fallbackFlags;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallbackFlags;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return fallbackFlags;
        return repoOps.getFreeTextStyleFlagsOrDefaultNormalized(controller, host.pageNumber(), annot.objectNumber, fallbackFlags);
    }

    float selectedTextAnnotationLineHeightOrDefault(float fallback) {
        if (host.sidecarSessionOrNull() != null) return fallback;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallback;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return fallback;
        float[] p = repoOps.getFreeTextParagraphOrNull(controller, host.pageNumber(), annot.objectNumber);
        return (p != null && p.length >= 1) ? p[0] : fallback;
    }

    float selectedTextAnnotationTextIndentPtOrDefault(float fallback) {
        if (host.sidecarSessionOrNull() != null) return fallback;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallback;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return fallback;
        float[] p = repoOps.getFreeTextParagraphOrNull(controller, host.pageNumber(), annot.objectNumber);
        return (p != null && p.length >= 2) ? p[1] : fallback;
    }

    int selectedTextAnnotationRotationDegOrDefault() {
        if (host.sidecarSessionOrNull() != null) return 0;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return 0;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return 0;
        return repoOps.getFreeTextRotationOrDefaultNormalized(controller, host.pageNumber(), annot.objectNumber, 0);
    }

    boolean applyTextFontFamilyToSelectedTextAnnotation(int fontFamily) {
        return updateSelectedEmbeddedFreeTextWithUndoAndRedraw(
                "Failed to update FreeText font family",
                true,
                true,
                (controller, page, objectId) -> repoOps.updateFreeTextFontFamilyByObjectNumber(controller, page, objectId, fontFamily));
    }

    boolean applyTextStyleFlagsToSelectedTextAnnotation(int styleFlags) {
        return updateSelectedEmbeddedFreeTextWithUndoAndRedraw(
                "Failed to update FreeText style flags",
                true,
                true,
                (controller, page, objectId) -> repoOps.updateFreeTextStyleFlagsByObjectNumber(controller, page, objectId, styleFlags));
    }

    boolean applyTextParagraphToSelectedTextAnnotation(float lineHeight, float textIndentPt) {
        return updateSelectedEmbeddedFreeTextWithUndoAndRedraw(
                "Failed to update FreeText paragraph",
                true,
                true,
                (controller, page, objectId) -> repoOps.updateFreeTextParagraphByObjectNumber(controller, page, objectId, lineHeight, textIndentPt));
    }

    boolean applyTextAlignmentToSelectedTextAnnotation(int alignment) {
        return updateSelectedEmbeddedFreeTextWithUndoAndRedraw(
                "Failed to update FreeText alignment",
                true,
                true,
                (controller, page, objectId) -> repoOps.updateFreeTextAlignmentByObjectNumber(controller, page, objectId, alignment));
    }

    boolean applyTextRotationToSelectedTextAnnotation(int rotationDegrees) {
        return updateSelectedEmbeddedFreeTextWithUndoAndRedraw(
                "Failed to update FreeText rotation",
                true,
                true,
                (controller, page, objectId) -> repoOps.updateFreeTextRotationByObjectNumber(controller, page, objectId, rotationDegrees));
    }

    boolean fitSelectedTextAnnotationToText() {
        if (host.sidecarSessionOrNull() != null) return false;
        Annotation annot = selectedEmbeddedFreeTextOrNull();
        if (annot == null || annot.objectNumber <= 0L) return false;
        String text = annot.text;
        if (text == null || text.trim().isEmpty()) return false;

        float scale = host.scale();
        if (scale <= 0f) return false;
        float docW = host.viewWidthPx() / scale;
        float docH = host.viewHeightPx() / scale;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        float fontSizePt = repoOps.getFreeTextFontSizeOrDefaultRaw(controller, host.pageNumber(), annot.objectNumber, 12.0f);
        int baseDpi = repoOps.getBaseResolutionDpiOrDefault(controller, 160);

        RectF fitted = FreeTextBoundsFitter.compute(
                host.resources(),
                scale,
                docW,
                docH,
                new RectF(annot),
                text,
                fontSizePt,
                baseDpi,
                false,
                true);
        if (fitted == null) return false;
        return commitTextAnnotationRectByObjectNumber(annot.objectNumber, fitted, true);
    }

    boolean duplicateSelectedTextAnnotation() {
        if (host.sidecarSessionOrNull() != null) return false;
        return duplicateSelectedEmbeddedFreeText();
    }

    boolean copySelectedTextAnnotationToClipboard() {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;

        final org.opendroidpdf.app.preferences.TextStylePrefsSnapshot prefs;
        try {
            prefs = AppServices.get().textStylePreferences().get();
        } catch (Throwable t) {
            return false;
        }

        String text = annot.text != null ? annot.text : "";
        long objectId = annot.objectNumber;
        int page = host.pageNumber();

        float fontSizePt = prefs.fontSize;
        float lineHeight = prefs.lineHeight;
        float textIndentPt = prefs.textIndentPt;
        int fontFamily = prefs.fontFamily;
        int fontStyleFlags = prefs.fontStyleFlags;
        int alignment = 0;
        int rotationDeg = 0;
        boolean lockPos = false;
        boolean lockContents = false;
        boolean userResized = true;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller != null && objectId > 0L) {
            fontSizePt = repoOps.getFreeTextFontSizeOrDefaultRaw(controller, page, objectId, fontSizePt);
            fontFamily = repoOps.getFreeTextFontFamilyOrDefaultRaw(controller, page, objectId, fontFamily);
            fontStyleFlags = repoOps.getFreeTextStyleFlagsOrDefaultRaw(controller, page, objectId, fontStyleFlags);

            float[] p = repoOps.getFreeTextParagraphOrNull(controller, page, objectId);
            if (p != null && p.length >= 1) lineHeight = p[0];
            if (p != null && p.length >= 2) textIndentPt = p[1];

            alignment = repoOps.getFreeTextAlignmentOrDefaultRaw(controller, page, objectId, alignment);
            rotationDeg = repoOps.getFreeTextRotationOrDefaultRaw(controller, page, objectId, rotationDeg);

            int flags = repoOps.getFreeTextFlagsOrDefault(controller, page, objectId, 0);
            lockPos = (flags & EmbeddedFreeTextRepositoryOps.PDF_ANNOT_FLAG_LOCKED) != 0;
            lockContents = (flags & EmbeddedFreeTextRepositoryOps.PDF_ANNOT_FLAG_LOCKED_CONTENTS) != 0;

            userResized = repoOps.getFreeTextUserResizedOrDefault(controller, page, objectId, userResized);
        }

        int textColorArgb = ColorPalette.getHex(prefs.colorIndex);
        int bgColorArgb = ColorPalette.getHex(prefs.backgroundColorIndex);
        float bgOpacity = prefs.backgroundOpacity;
        int borderColorArgb = ColorPalette.getHex(prefs.borderColorIndex);
        float borderWidthPt = prefs.borderWidthPt;
        boolean borderDashed = prefs.borderStyle != 0;
        float borderRadiusPt = prefs.borderRadiusPt;

        TextAnnotationClipboard.set(new TextAnnotationClipboard.Payload(
                TextAnnotationClipboard.Kind.EMBEDDED_FREETEXT,
                new RectF(annot),
                text,
                fontSizePt,
                lineHeight,
                textIndentPt,
                fontFamily,
                fontStyleFlags,
                alignment,
                rotationDeg,
                textColorArgb,
                bgColorArgb,
                bgOpacity,
                borderColorArgb,
                borderWidthPt,
                borderDashed,
                borderRadiusPt,
                lockPos,
                lockContents,
                userResized));

        TextAnnotationPageDelegate.copyPlainTextToSystemClipboard(host.context(), text);
        return true;
    }

    boolean cutSelectedTextAnnotationToClipboard() {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;

        if (!copySelectedTextAnnotationToClipboard()) return false;
        TextAnnotationClipboard.setForCut(TextAnnotationClipboard.get());
        return deleteEmbeddedFreeTextByObjectNumberWithUndo(objectId);
    }

    boolean pasteFromClipboard(@NonNull TextAnnotationClipboard.Payload payload) {
        if (payload == null) return false;
        return pasteEmbeddedFromClipboard(payload);
    }

    void updateTextAnnotationContentsByObjectNumber(long objectNumber, @Nullable String text) {
        if (host.sidecarSessionOrNull() != null) return;
        if (objectNumber <= 0L) return;

        final MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return;

        if (repoOps.isFreeTextContentsLockedOrDefault(controller, host.pageNumber(), objectNumber, false)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return;
        }

        final int page = host.pageNumber();
        final String nextText = text != null ? text : "";

        final Annotation hint = EmbeddedFreeTextUndo.findAnnotationByObjectNumber(host.embeddedAnnotationsOrNull(), objectNumber);
        final EmbeddedFreeTextSnapshot before = undoOps.snapshotByObjectNumber(objectNumber, hint);
        final RectF priorBounds = before != null ? new RectF(before.boundsDoc) : (hint != null ? new RectF(hint) : null);

        final boolean lockPos =
                before != null
                        ? before.lockPositionSize
                        : repoOps.isFreeTextPositionLockedOrDefault(controller, page, objectNumber, false);

        boolean allowWidthGrow;
        if (before != null) {
            allowWidthGrow = !before.userResized;
        } else {
            boolean userResized = repoOps.getFreeTextUserResizedOrDefault(controller, page, objectNumber, true);
            allowWidthGrow = !userResized;
        }

        final RectF desiredBoundsDoc =
                (!lockPos && priorBounds != null)
                        ? computeAutoFitBoundsForEmbeddedFreeTextTextUpdate(page, objectNumber, priorBounds, nextText, allowWidthGrow)
                        : null;

        host.annotationUiController().updateTextAnnotationContentsByObjectNumber(page, objectNumber, nextText, () -> {
            final EmbeddedFreeTextSnapshot afterSnapshot =
                    before != null ? before.withTextAndMaybeBounds(nextText, desiredBoundsDoc) : null;

            AppCoroutines.launchIo(AppCoroutines.ioScope(), () -> {
                if (desiredBoundsDoc != null) {
                    try {
                        repoOps.updateAnnotationRectByObjectNumber(controller, page, objectNumber, desiredBoundsDoc);
                    } catch (Throwable ignore) {
                    }
                    try { controller.markDocumentDirty(); } catch (Throwable ignore) {}
                }

                AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                    host.requestFullRedrawAfterNextAnnotationLoad();
                    host.discardRenderedPage();
                    host.loadAnnotations();

                    RectF selectionBounds = desiredBoundsDoc != null ? desiredBoundsDoc : priorBounds;
                    if (selectionBounds != null) {
                        try {
                            host.selectionManager().selectByObjectNumber(objectNumber, new RectF(selectionBounds), host.selectionUiBridge().selectionBoxHost());
                        } catch (Throwable t) {
                            try { host.setAnnotationSelectionBox(new RectF(selectionBounds)); } catch (Throwable ignore) {}
                        }
                    }

                    host.invalidateOverlay();

                    if (before != null && afterSnapshot != null && !before.equals(afterSnapshot)) {
                        undoController.push(undoOps.snapshotOp(before, afterSnapshot, objectNumber));
                    }
                });
            });
        });
    }

    boolean embeddedFreeTextContentsLocked(long objectId) {
        return isEmbeddedFreeTextContentsLocked(objectId);
    }

    boolean embeddedFreeTextPositionLocked(long objectId) {
        return isEmbeddedFreeTextPositionLocked(objectId);
    }

    boolean isFreeTextPositionLocked(long objectId) {
        return isEmbeddedFreeTextPositionLocked(objectId);
    }

    private interface FreeTextUpdate {
        void run(@NonNull MuPdfController controller, int page, long objectId);
    }

    private boolean updateSelectedEmbeddedFreeTextWithUndoAndRedraw(@NonNull String logMessage,
                                                                    boolean guardContentsLocked,
                                                                    boolean restoreSelection,
                                                                    @NonNull FreeTextUpdate update) {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = selectedEmbeddedFreeTextOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return false;
        long objectId = annot.objectNumber;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        int page = host.pageNumber();

        if (guardContentsLocked && repoOps.isFreeTextContentsLockedOrDefault(controller, page, objectId, false)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        try {
            EmbeddedFreeTextSnapshot before = undoOps.snapshotByObjectNumber(objectId, annot);
            update.run(controller, page, objectId);
            controller.markDocumentDirty();

            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            host.invalidateOverlay();

            if (restoreSelection) {
                try { host.selectionManager().selectByObjectNumber(objectId, new RectF(annot), host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore) {}
            }

            if (before != null) {
                EmbeddedFreeTextSnapshot after = undoOps.snapshotByObjectNumber(objectId, annot);
                if (after != null && !before.equals(after)) {
                    undoController.push(undoOps.snapshotOp(before, after, objectId));
                }
            }
            return true;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", logMessage, t);
            return false;
        }
    }

    @Nullable
    private Annotation selectedEmbeddedFreeTextOrNull() {
        if (host.sidecarSessionOrNull() != null) return null;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return null;
        if (annot.objectNumber <= 0L) return null;
        return annot;
    }

    @Nullable
    private RectF computeAutoFitBoundsForEmbeddedFreeTextTextUpdate(int page,
                                                                    long objectId,
                                                                    @NonNull RectF currentBoundsDoc,
                                                                    @NonNull String nextText,
                                                                    boolean allowWidthGrow) {
        if (host.sidecarSessionOrNull() != null) return null;
        if (objectId <= 0L) return null;
        if (currentBoundsDoc == null) return null;
        if (nextText == null || nextText.trim().isEmpty()) return null;

        float scale = host.scale();
        if (scale <= 0f) return null;
        float pageDocWidth = host.viewWidthPx() / scale;
        float pageDocHeight = host.viewHeightPx() / scale;
        if (pageDocWidth <= 0f || pageDocHeight <= 0f) return null;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return null;

        float fontSizePt = repoOps.getFreeTextFontSizeOrDefaultRaw(controller, page, objectId, 12.0f);
        int baseDpi = repoOps.getBaseResolutionDpiOrDefault(controller, 160);

        return FreeTextBoundsFitter.compute(
                host.resources(),
                scale,
                pageDocWidth,
                pageDocHeight,
                currentBoundsDoc,
                nextText,
                fontSizePt,
                baseDpi,
                allowWidthGrow,
                false);
    }

    private boolean isEmbeddedFreeTextPositionLocked(long objectId) {
        if (host.sidecarSessionOrNull() != null) return false;
        if (objectId <= 0L) return false;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        return repoOps.isFreeTextPositionLockedOrDefault(controller, host.pageNumber(), objectId, false);
    }

    private boolean isEmbeddedFreeTextContentsLocked(long objectId) {
        if (host.sidecarSessionOrNull() != null) return false;
        if (objectId <= 0L) return false;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        return repoOps.isFreeTextContentsLockedOrDefault(controller, host.pageNumber(), objectId, false);
    }

    private boolean pasteEmbeddedFromClipboard(@NonNull TextAnnotationClipboard.Payload payload) {
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        final float scale = host.scale();
        if (scale <= 0f) return false;
        final float docW = host.viewWidthPx() / scale;
        final float docH = host.viewHeightPx() / scale;

        int offsetSteps = TextAnnotationClipboard.nextPasteIndex();
        final RectF desiredBounds = TextAnnotationPageDelegate.offsetAndClampDocBoundsWithSteps(host.resources(), scale, docW, docH, payload.boundsDoc, offsetSteps);
        if (desiredBounds == null) return false;

        final String text = payload.text != null ? payload.text : "";
        final int page = host.pageNumber();

        final PointF[] rectTwoPoints = TextAnnotationQuadPoints.fromBounds(false,
                desiredBounds.left,
                desiredBounds.top,
                desiredBounds.right,
                desiredBounds.bottom,
                docH);

        AppCoroutines.launchIo(AppCoroutines.ioScope(), () -> {
            EmbeddedFreeTextRepositoryOps.CreatedFreeText created =
                    repoOps.tryAddFreeTextAnnotation(controller, page, rectTwoPoints, desiredBounds, text);
            if (created.objectId > 0L) {
                repoOps.tryApplyPayloadStyle(controller, page, created.objectId, payload);
            }

            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                // Ensure the next draw uses a full redraw (FreeText appearances can be missed by incremental updates).
                host.requestFullRedrawAfterNextAnnotationLoad();
                host.discardRenderedPage();
                host.loadAnnotations();

                if (created.objectId > 0L) {
                    RectF bounds = created.annotation != null ? new RectF(created.annotation) : new RectF(desiredBounds);
                    try { host.selectionManager().selectByObjectNumber(created.objectId, bounds, host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore2) {}

                    EmbeddedFreeTextSnapshot snapshot = undoOps.snapshotByObjectNumber(created.objectId, created.annotation);
                    if (snapshot != null) {
                        undoController.push(undoOps.presenceOp(snapshot, created.objectId, true));
                    }
                } else {
                    try { host.setAnnotationSelectionBox(new RectF(desiredBounds)); } catch (Throwable ignore2) {}
                }
                host.invalidateOverlay();
            });
        });

        return true;
    }

    private boolean duplicateSelectedEmbeddedFreeText() {
        Annotation annot = selectedEmbeddedFreeTextOrNull();
        if (annot == null) return false;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        final float scale = host.scale();
        if (scale <= 0f) return false;
        final float docW = host.viewWidthPx() / scale;
        final float docH = host.viewHeightPx() / scale;

        final RectF desiredBounds = TextAnnotationPageDelegate.offsetAndClampDocBounds(host.resources(), scale, docW, docH, new RectF(annot));
        if (desiredBounds == null) return false;

        final String text = annot.text != null ? annot.text : "";
        final long sourceObjectId = annot.objectNumber;
        final int page = host.pageNumber();

        final PointF[] rectTwoPoints = TextAnnotationQuadPoints.fromBounds(false,
                desiredBounds.left,
                desiredBounds.top,
                desiredBounds.right,
                desiredBounds.bottom,
                docH);

        AppCoroutines.launchIo(AppCoroutines.ioScope(), () -> {
            EmbeddedFreeTextRepositoryOps.CreatedFreeText created =
                    repoOps.tryAddFreeTextAnnotation(controller, page, rectTwoPoints, desiredBounds, text);
            if (sourceObjectId > 0L && created.objectId > 0L) {
                repoOps.tryCopyStyleFromSourceToDest(controller, page, sourceObjectId, created.objectId);
            }

            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                // Ensure the next draw uses a full redraw (FreeText appearances can be missed by incremental updates).
                host.requestFullRedrawAfterNextAnnotationLoad();
                host.discardRenderedPage();
                host.loadAnnotations();

                if (created.objectId > 0L) {
                    RectF bounds = created.annotation != null ? new RectF(created.annotation) : new RectF(desiredBounds);
                    try { host.selectionManager().selectByObjectNumber(created.objectId, bounds, host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore2) {}

                    EmbeddedFreeTextSnapshot snapshot = undoOps.snapshotByObjectNumber(created.objectId, created.annotation);
                    if (snapshot != null) {
                        undoController.push(undoOps.presenceOp(snapshot, created.objectId, true));
                    }
                } else {
                    try { host.setAnnotationSelectionBox(new RectF(desiredBounds)); } catch (Throwable ignore2) {}
                }
                host.invalidateOverlay();
            });
        });

        return true;
    }
}
