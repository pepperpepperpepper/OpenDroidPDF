package org.opendroidpdf.app.annotation;

import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;
import org.opendroidpdf.core.MuPdfController;

import java.util.HashSet;
import java.util.Set;

final class TextAnnotationEmbeddedFreeTextOps {
    // PDF annotation flags (/F) bits for lock controls.
    private static final int PDF_ANNOT_FLAG_LOCKED = 1 << (8 - 1);
    private static final int PDF_ANNOT_FLAG_LOCKED_CONTENTS = 1 << (10 - 1);

    private final TextAnnotationPageDelegate router;
    private final TextAnnotationPageDelegate.Host host;
    private final TextAnnotationUndoController undoController;

    TextAnnotationEmbeddedFreeTextOps(@NonNull TextAnnotationPageDelegate router,
                                      @NonNull TextAnnotationPageDelegate.Host host,
                                      @NonNull TextAnnotationUndoController undoController) {
        this.router = router;
        this.host = host;
        this.undoController = undoController;
    }

    boolean commitTextAnnotationRectByObjectNumber(long objectId, @NonNull RectF boundsDoc, boolean markUserResized) {
        if (host.sidecarSessionOrNull() != null) return false;
        if (objectId <= 0L || boundsDoc == null) return false;
        if (isEmbeddedFreeTextPositionLocked(objectId)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_position_size, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        final Annotation hint = findAnnotationByObjectNumber(host.embeddedAnnotationsOrNull(), objectId);
        final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectId, hint);

        RectF normalized = normalizeTextAnnotationBoundsForCommit(boundsDoc);
        if (normalized == null) return false;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        controller.rawRepository().updateAnnotationRectByObjectNumber(
                host.pageNumber(),
                objectId,
                normalized.left,
                normalized.top,
                normalized.right,
                normalized.bottom);
        if (markUserResized) {
            try {
                controller.rawRepository().setFreeTextUserResizedByObjectNumber(host.pageNumber(), objectId, true);
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
                undoController.push(new TextAnnotationUndoController.Op() {
                    @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectId); }
                    @Override public void redo() { applyEmbeddedFreeTextSnapshot(after, objectId); }
                });
            }
        }
        return true;
    }
    boolean deleteEmbeddedFreeTextByObjectNumberWithUndo(long objectId) {
        if (host.sidecarSessionOrNull() != null) return false;
        if (objectId <= 0L) return false;
        Annotation hint = findAnnotationByObjectNumber(host.embeddedAnnotationsOrNull(), objectId);
        EmbeddedFreeTextSnapshot snapshot = snapshotEmbeddedFreeTextByObjectNumber(objectId, hint);
        if (snapshot == null) return false;
        deleteEmbeddedFreeTextByObjectNumber(objectId);
        undoController.push(new EmbeddedFreeTextPresenceOp(snapshot, -1L, false));
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

        final RectF desiredBounds = normalizeTextAnnotationBoundsForCommit(new RectF(annot));
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
            Annotation newAnnot = null;
            long newObjectId = -1L;
            try {
                Annotation[] before = controller.annotations(page);
                Set<Long> beforeIds = new HashSet<>();
                if (before != null) {
                    for (Annotation a : before) {
                        if (a == null) continue;
                        if (a.objectNumber > 0L) beforeIds.add(a.objectNumber);
                    }
                }

                controller.addTextAnnotation(page, rectTwoPoints, text);

                Annotation[] after = controller.annotations(page);
                newAnnot = findNewFreeText(after, beforeIds, desiredBounds, text);
                if (newAnnot != null) newObjectId = newAnnot.objectNumber;
            } catch (Throwable ignore) {
                newAnnot = null;
                newObjectId = -1L;
            }

            final Annotation finalAnnot = newAnnot;
            final long finalId = newObjectId;
            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                host.requestFullRedrawAfterNextAnnotationLoad();
                host.discardRenderedPage();
                host.loadAnnotations();

                if (finalId > 0L) {
                    RectF b = finalAnnot != null ? new RectF(finalAnnot) : new RectF(desiredBounds);
                    try { host.selectionManager().selectByObjectNumber(finalId, b, host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore2) {}

                    EmbeddedFreeTextSnapshot snapshot = snapshotEmbeddedFreeTextByObjectNumber(finalId, finalAnnot);
                    if (snapshot != null) {
                        undoController.push(new EmbeddedFreeTextPresenceOp(snapshot, finalId, true));
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

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;
        if (isEmbeddedFreeTextContentsLocked(objectId)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);

        float r = ColorPalette.getR(colorIndex);
        float g = ColorPalette.getG(colorIndex);
        float b = ColorPalette.getB(colorIndex);

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        controller.rawRepository().updateFreeTextStyleByObjectNumber(host.pageNumber(), objectId, fontSize, r, g, b);
        controller.markDocumentDirty();

        host.requestFullRedrawAfterNextAnnotationLoad();
        host.discardRenderedPage();
        host.loadAnnotations();
        host.invalidateOverlay();

        if (before != null) {
            EmbeddedFreeTextSnapshot after = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
            if (after != null && !before.equals(after)) {
                undoController.push(new TextAnnotationUndoController.Op() {
                    @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectId); }
                    @Override public void redo() { applyEmbeddedFreeTextSnapshot(after, objectId); }
                });
            }
        }
        return true;
    }

    boolean applyTextBackgroundToSelectedTextAnnotation(int colorIndex, float opacity) {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;
        if (isEmbeddedFreeTextContentsLocked(objectId)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        float r = ColorPalette.getR(colorIndex);
        float g = ColorPalette.getG(colorIndex);
        float b = ColorPalette.getB(colorIndex);

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);

        try {
            controller.rawRepository().updateFreeTextBackgroundByObjectNumber(host.pageNumber(), objectId, r, g, b, opacity);
            controller.markDocumentDirty();

            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            host.invalidateOverlay();

            // Best-effort: keep selection stable.
            try { host.selectionManager().selectByObjectNumber(objectId, new RectF(annot), host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore) {}

            if (before != null) {
                EmbeddedFreeTextSnapshot after = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
                if (after != null && !before.equals(after)) {
                    undoController.push(new TextAnnotationUndoController.Op() {
                        @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectId); }
                        @Override public void redo() { applyEmbeddedFreeTextSnapshot(after, objectId); }
                    });
                }
            }
            return true;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update FreeText background", t);
            return false;
        }
    }

    boolean applyTextBorderToSelectedTextAnnotation(int colorIndex, float widthPt, boolean dashed, float radiusPt) {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;
        if (isEmbeddedFreeTextContentsLocked(objectId)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        float r = ColorPalette.getR(colorIndex);
        float g = ColorPalette.getG(colorIndex);
        float b = ColorPalette.getB(colorIndex);

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);

        try {
            controller.rawRepository().updateFreeTextBorderByObjectNumber(host.pageNumber(), objectId, r, g, b, widthPt, dashed, radiusPt);
            controller.markDocumentDirty();

            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            host.invalidateOverlay();

            // Best-effort: keep selection stable.
            try { host.selectionManager().selectByObjectNumber(objectId, new RectF(annot), host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore) {}

            if (before != null) {
                EmbeddedFreeTextSnapshot after = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
                if (after != null && !before.equals(after)) {
                    undoController.push(new TextAnnotationUndoController.Op() {
                        @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectId); }
                        @Override public void redo() { applyEmbeddedFreeTextSnapshot(after, objectId); }
                    });
                }
            }
            return true;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update FreeText border", t);
            return false;
        }
    }

    boolean applyTextLocksToSelectedTextAnnotation(boolean lockPositionSize, boolean lockContents) {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);

        try {
            controller.rawRepository().updateFreeTextLocksByObjectNumber(host.pageNumber(), objectId, lockPositionSize, lockContents);
            controller.markDocumentDirty();

            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            host.invalidateOverlay();

            // Best-effort: keep selection stable.
            try { host.selectionManager().selectByObjectNumber(objectId, new RectF(annot), host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore) {}

            if (before != null) {
                EmbeddedFreeTextSnapshot after = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
                if (after != null && !before.equals(after)) {
                    undoController.push(new TextAnnotationUndoController.Op() {
                        @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectId); }
                        @Override public void redo() { applyEmbeddedFreeTextSnapshot(after, objectId); }
                    });
                }
            }
            return true;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update FreeText locks", t);
            return false;
        }
    }

    boolean selectedTextAnnotationLockPositionSizeOrDefault() {
        if (host.sidecarSessionOrNull() != null) return false;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return false;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        try {
            int flags = controller.rawRepository().getFreeTextFlagsByObjectNumber(host.pageNumber(), annot.objectNumber);
            return (flags & PDF_ANNOT_FLAG_LOCKED) != 0;
        } catch (Throwable ignore) {
            return false;
        }
    }

    boolean selectedTextAnnotationLockContentsOrDefault() {
        if (host.sidecarSessionOrNull() != null) return false;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return false;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        try {
            int flags = controller.rawRepository().getFreeTextFlagsByObjectNumber(host.pageNumber(), annot.objectNumber);
            return (flags & PDF_ANNOT_FLAG_LOCKED_CONTENTS) != 0;
        } catch (Throwable ignore) {
            return false;
        }
    }

    int selectedTextAnnotationAlignmentOrDefault() {
        if (host.sidecarSessionOrNull() != null) return 0;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return 0;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return 0;
        try {
            int q = controller.rawRepository().getFreeTextAlignmentByObjectNumber(host.pageNumber(), annot.objectNumber);
            return Math.max(0, Math.min(2, q));
        } catch (Throwable ignore) {
            return 0;
        }
    }

    float selectedTextAnnotationFontSizeOrDefault(float fallbackPt) {
        if (host.sidecarSessionOrNull() != null) return fallbackPt;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallbackPt;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return fallbackPt;
        try {
            float pt = controller.rawRepository().getFreeTextFontSizeByObjectNumber(host.pageNumber(), annot.objectNumber);
            if (!Float.isNaN(pt) && !Float.isInfinite(pt) && pt > 0.0f) return pt;
        } catch (Throwable ignore) {
        }
        return fallbackPt;
    }

    int selectedTextAnnotationFontFamilyOrDefault(int fallbackFamily) {
        if (host.sidecarSessionOrNull() != null) return fallbackFamily;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallbackFamily;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return fallbackFamily;
        try {
            int family = controller.rawRepository().getFreeTextFontFamilyByObjectNumber(host.pageNumber(), annot.objectNumber);
            return TextFontFamily.normalize(family);
        } catch (Throwable ignore) {
            return fallbackFamily;
        }
    }

    int selectedTextAnnotationStyleFlagsOrDefault(int fallbackFlags) {
        if (host.sidecarSessionOrNull() != null) return fallbackFlags;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallbackFlags;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return fallbackFlags;
        try {
            int flags = controller.rawRepository().getFreeTextStyleFlagsByObjectNumber(host.pageNumber(), annot.objectNumber);
            return TextStyleFlags.normalize(flags);
        } catch (Throwable ignore) {
            return fallbackFlags;
        }
    }

    float selectedTextAnnotationLineHeightOrDefault(float fallback) {
        if (host.sidecarSessionOrNull() != null) return fallback;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallback;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return fallback;
        try {
            float[] p = controller.rawRepository().getFreeTextParagraphByObjectNumber(host.pageNumber(), annot.objectNumber);
            if (p != null && p.length >= 1) return p[0];
        } catch (Throwable ignore) {
        }
        return fallback;
    }

    float selectedTextAnnotationTextIndentPtOrDefault(float fallback) {
        if (host.sidecarSessionOrNull() != null) return fallback;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return fallback;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return fallback;
        try {
            float[] p = controller.rawRepository().getFreeTextParagraphByObjectNumber(host.pageNumber(), annot.objectNumber);
            if (p != null && p.length >= 2) return p[1];
        } catch (Throwable ignore) {
        }
        return fallback;
    }

    int selectedTextAnnotationRotationDegOrDefault() {
        if (host.sidecarSessionOrNull() != null) return 0;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return 0;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return 0;
        try {
            int rot = controller.rawRepository().getFreeTextRotationByObjectNumber(host.pageNumber(), annot.objectNumber);
            if (rot < 0 || rot >= 360) {
                rot %= 360;
                if (rot < 0) rot += 360;
            }
            return rot;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    boolean applyTextFontFamilyToSelectedTextAnnotation(int fontFamily) {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;
        if (isEmbeddedFreeTextContentsLocked(objectId)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
        try {
            controller.rawRepository().updateFreeTextFontFamilyByObjectNumber(host.pageNumber(), objectId, fontFamily);
            controller.markDocumentDirty();
            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            host.invalidateOverlay();
            if (before != null) {
                EmbeddedFreeTextSnapshot after = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
                if (after != null && !before.equals(after)) {
                    undoController.push(new TextAnnotationUndoController.Op() {
                        @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectId); }
                        @Override public void redo() { applyEmbeddedFreeTextSnapshot(after, objectId); }
                    });
                }
            }
            return true;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update FreeText font family", t);
            return false;
        }
    }

    boolean applyTextStyleFlagsToSelectedTextAnnotation(int styleFlags) {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;
        if (isEmbeddedFreeTextContentsLocked(objectId)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
        try {
            controller.rawRepository().updateFreeTextStyleFlagsByObjectNumber(host.pageNumber(), objectId, styleFlags);
            controller.markDocumentDirty();
            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            host.invalidateOverlay();
            // Best-effort: keep selection stable.
            try { host.selectionManager().selectByObjectNumber(objectId, new RectF(annot), host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore) {}
            if (before != null) {
                EmbeddedFreeTextSnapshot after = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
                if (after != null && !before.equals(after)) {
                    undoController.push(new TextAnnotationUndoController.Op() {
                        @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectId); }
                        @Override public void redo() { applyEmbeddedFreeTextSnapshot(after, objectId); }
                    });
                }
            }
            return true;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update FreeText style flags", t);
            return false;
        }
    }

    boolean applyTextParagraphToSelectedTextAnnotation(float lineHeight, float textIndentPt) {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;
        if (isEmbeddedFreeTextContentsLocked(objectId)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        try {
            final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
            controller.rawRepository().updateFreeTextParagraphByObjectNumber(host.pageNumber(), objectId, lineHeight, textIndentPt);
            controller.markDocumentDirty();
            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            host.invalidateOverlay();
            // Best-effort: keep selection stable.
            try { host.selectionManager().selectByObjectNumber(objectId, new RectF(annot), host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore) {}
            if (before != null) {
                EmbeddedFreeTextSnapshot after = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
                if (after != null && !before.equals(after)) {
                    undoController.push(new TextAnnotationUndoController.Op() {
                        @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectId); }
                        @Override public void redo() { applyEmbeddedFreeTextSnapshot(after, objectId); }
                    });
                }
            }
            return true;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update FreeText paragraph", t);
            return false;
        }
    }

    boolean applyTextAlignmentToSelectedTextAnnotation(int alignment) {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return false;
        long objectId = annot.objectNumber;
        if (isEmbeddedFreeTextContentsLocked(objectId)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
        try {
            controller.rawRepository().updateFreeTextAlignmentByObjectNumber(host.pageNumber(), objectId, alignment);
            controller.markDocumentDirty();

            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            host.invalidateOverlay();

            // Best-effort: keep selection stable.
            try { host.selectionManager().selectByObjectNumber(objectId, new RectF(annot), host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore) {}
            if (before != null) {
                EmbeddedFreeTextSnapshot after = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
                if (after != null && !before.equals(after)) {
                    undoController.push(new TextAnnotationUndoController.Op() {
                        @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectId); }
                        @Override public void redo() { applyEmbeddedFreeTextSnapshot(after, objectId); }
                    });
                }
            }
            return true;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update FreeText alignment", t);
            return false;
        }
    }

    boolean applyTextRotationToSelectedTextAnnotation(int rotationDegrees) {
        if (host.sidecarSessionOrNull() != null) return false;

        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;
        long objectId = annot.objectNumber;
        if (objectId <= 0L) return false;
        if (isEmbeddedFreeTextContentsLocked(objectId)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return false;
        }

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;

        final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
        try {
            controller.rawRepository().updateFreeTextRotationByObjectNumber(host.pageNumber(), objectId, rotationDegrees);
            controller.markDocumentDirty();

            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            host.invalidateOverlay();

            // Best-effort: keep selection stable.
            try { host.selectionManager().selectByObjectNumber(objectId, new RectF(annot), host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore) {}
            if (before != null) {
                EmbeddedFreeTextSnapshot after = snapshotEmbeddedFreeTextByObjectNumber(objectId, annot);
                if (after != null && !before.equals(after)) {
                    undoController.push(new TextAnnotationUndoController.Op() {
                        @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectId); }
                        @Override public void redo() { applyEmbeddedFreeTextSnapshot(after, objectId); }
                    });
                }
            }
            return true;
        } catch (Throwable t) {
            android.util.Log.e("MuPDFPageView", "Failed to update FreeText rotation", t);
            return false;
        }
    }

    boolean fitSelectedTextAnnotationToText() {
        if (host.sidecarSessionOrNull() != null) return false;
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT || annot.objectNumber <= 0L) return false;
        String text = annot.text;
        if (text == null || text.trim().isEmpty()) return false;

        float scale = host.scale();
        if (scale <= 0f) return false;
        float docW = host.viewWidthPx() / scale;
        float docH = host.viewHeightPx() / scale;

        float fontSizePt = 12.0f;
        int baseDpi = 160;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        try { fontSizePt = controller.rawRepository().getFreeTextFontSizeByObjectNumber(host.pageNumber(), annot.objectNumber); } catch (Throwable ignore) {}
        try { baseDpi = controller.rawRepository().getBaseResolutionDpi(); } catch (Throwable ignore) {}

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
            try { fontSizePt = controller.rawRepository().getFreeTextFontSizeByObjectNumber(page, objectId); } catch (Throwable ignore) {}
            try { fontFamily = controller.rawRepository().getFreeTextFontFamilyByObjectNumber(page, objectId); } catch (Throwable ignore) {}
            try { fontStyleFlags = controller.rawRepository().getFreeTextStyleFlagsByObjectNumber(page, objectId); } catch (Throwable ignore) {}
            try {
                float[] p = controller.rawRepository().getFreeTextParagraphByObjectNumber(page, objectId);
                if (p != null && p.length >= 1) lineHeight = p[0];
                if (p != null && p.length >= 2) textIndentPt = p[1];
            } catch (Throwable ignore) {}
            try { alignment = controller.rawRepository().getFreeTextAlignmentByObjectNumber(page, objectId); } catch (Throwable ignore) {}
            try { rotationDeg = controller.rawRepository().getFreeTextRotationByObjectNumber(page, objectId); } catch (Throwable ignore) {}
            try {
                int flags = controller.rawRepository().getFreeTextFlagsByObjectNumber(page, objectId);
                lockPos = (flags & PDF_ANNOT_FLAG_LOCKED) != 0;
                lockContents = (flags & PDF_ANNOT_FLAG_LOCKED_CONTENTS) != 0;
            } catch (Throwable ignore) {}
            try { userResized = controller.rawRepository().getFreeTextUserResizedByObjectNumber(page, objectId); } catch (Throwable ignore) {}
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

    boolean pasteFromClipboard(@NonNull TextAnnotationClipboard.Payload payload) {
        if (payload == null) return false;
        return pasteEmbeddedFromClipboard(payload);
    }
    void updateTextAnnotationContentsByObjectNumber(long objectNumber, @Nullable String text) {
        if (host.sidecarSessionOrNull() != null) return;
        if (objectNumber <= 0L) return;

        final MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return;

        if (isEmbeddedFreeTextContentsLocked(objectNumber)) {
            try { android.widget.Toast.makeText(host.context(), R.string.text_locked_contents, android.widget.Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
            return;
        }

        final int page = host.pageNumber();
        final String nextText = text != null ? text : "";

        final Annotation hint = findAnnotationByObjectNumber(host.embeddedAnnotationsOrNull(), objectNumber);
        final EmbeddedFreeTextSnapshot before = snapshotEmbeddedFreeTextByObjectNumber(objectNumber, hint);
        final RectF priorBounds = before != null ? new RectF(before.boundsDoc) : (hint != null ? new RectF(hint) : null);

        final boolean lockPos = before != null ? before.lockPositionSize : isEmbeddedFreeTextPositionLocked(objectNumber);

        boolean allowWidthGrow = false;
        if (before != null) {
            allowWidthGrow = !before.userResized;
        } else {
            try {
                boolean userResized = controller.rawRepository().getFreeTextUserResizedByObjectNumber(page, objectNumber);
                allowWidthGrow = !userResized;
            } catch (Throwable ignore) {
                allowWidthGrow = false;
            }
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
                        controller.rawRepository().updateAnnotationRectByObjectNumber(
                                page,
                                objectNumber,
                                desiredBoundsDoc.left,
                                desiredBoundsDoc.top,
                                desiredBoundsDoc.right,
                                desiredBoundsDoc.bottom);
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
                        undoController.push(new TextAnnotationUndoController.Op() {
                            @Override public void undo() { applyEmbeddedFreeTextSnapshot(before, objectNumber); }
                            @Override public void redo() { applyEmbeddedFreeTextSnapshot(afterSnapshot, objectNumber); }
                        });
                    }
                });
            });
        });
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

        float fontSizePt = 12.0f;
        int baseDpi = 160;
        try { fontSizePt = controller.rawRepository().getFreeTextFontSizeByObjectNumber(page, objectId); } catch (Throwable ignore) {}
        try { baseDpi = controller.rawRepository().getBaseResolutionDpi(); } catch (Throwable ignore) {}

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

    boolean embeddedFreeTextContentsLocked(long objectId) {
        return isEmbeddedFreeTextContentsLocked(objectId);
    }

    boolean embeddedFreeTextPositionLocked(long objectId) {
        return isEmbeddedFreeTextPositionLocked(objectId);
    }

    boolean isFreeTextPositionLocked(long objectId) {
        return isEmbeddedFreeTextPositionLocked(objectId);
    }

    private static final class EmbeddedFreeTextSnapshot {
        final int pageNumber;
        final long objectNumber;
        @NonNull final RectF boundsDoc;
        @NonNull final String text;
        final boolean userResized;

        final float fontSizePt;
        final float lineHeight;
        final float textIndentPt;
        final float textR;
        final float textG;
        final float textB;
        final int fontFamily;
        final int styleFlags;
        final int alignment;
        final int rotationDeg;

        final boolean lockPositionSize;
        final boolean lockContents;

        final float bgR;
        final float bgG;
        final float bgB;
        final float bgOpacity;

        final float borderR;
        final float borderG;
        final float borderB;
        final float borderWidthPt;
        final boolean borderDashed;
        final float borderRadiusPt;

        EmbeddedFreeTextSnapshot(int pageNumber,
                                 long objectNumber,
                                 @NonNull RectF boundsDoc,
                                 @NonNull String text,
                                 boolean userResized,
                                 float fontSizePt,
                                 float lineHeight,
                                 float textIndentPt,
                                 float textR,
                                 float textG,
                                 float textB,
                                 int fontFamily,
                                 int styleFlags,
                                 int alignment,
                                 int rotationDeg,
                                 boolean lockPositionSize,
                                 boolean lockContents,
                                 float bgR,
                                 float bgG,
                                 float bgB,
                                 float bgOpacity,
                                 float borderR,
                                 float borderG,
                                 float borderB,
                                 float borderWidthPt,
                                 boolean borderDashed,
                                 float borderRadiusPt) {
            this.pageNumber = pageNumber;
            this.objectNumber = objectNumber;
            this.boundsDoc = new RectF(boundsDoc);
            this.text = text != null ? text : "";
            this.userResized = userResized;
            this.fontSizePt = fontSizePt;
            this.lineHeight = lineHeight;
            this.textIndentPt = textIndentPt;
            this.textR = textR;
            this.textG = textG;
            this.textB = textB;
            this.fontFamily = fontFamily;
            this.styleFlags = styleFlags;
            this.alignment = alignment;
            this.rotationDeg = rotationDeg;
            this.lockPositionSize = lockPositionSize;
            this.lockContents = lockContents;
            this.bgR = bgR;
            this.bgG = bgG;
            this.bgB = bgB;
            this.bgOpacity = bgOpacity;
            this.borderR = borderR;
            this.borderG = borderG;
            this.borderB = borderB;
            this.borderWidthPt = borderWidthPt;
            this.borderDashed = borderDashed;
            this.borderRadiusPt = borderRadiusPt;
        }

        EmbeddedFreeTextSnapshot withBounds(@NonNull RectF nextBoundsDoc, boolean nextUserResized) {
            return new EmbeddedFreeTextSnapshot(
                    pageNumber,
                    objectNumber,
                    nextBoundsDoc,
                    text,
                    nextUserResized,
                    fontSizePt,
                    lineHeight,
                    textIndentPt,
                    textR, textG, textB,
                    fontFamily,
                    styleFlags,
                    alignment,
                    rotationDeg,
                    lockPositionSize,
                    lockContents,
                    bgR, bgG, bgB, bgOpacity,
                    borderR, borderG, borderB,
                    borderWidthPt,
                    borderDashed,
                    borderRadiusPt);
        }

        EmbeddedFreeTextSnapshot withTextAndMaybeBounds(@NonNull String nextText, @Nullable RectF nextBoundsDocOrNull) {
            RectF nextBounds = nextBoundsDocOrNull != null ? nextBoundsDocOrNull : boundsDoc;
            return new EmbeddedFreeTextSnapshot(
                    pageNumber,
                    objectNumber,
                    nextBounds,
                    nextText,
                    userResized,
                    fontSizePt,
                    lineHeight,
                    textIndentPt,
                    textR, textG, textB,
                    fontFamily,
                    styleFlags,
                    alignment,
                    rotationDeg,
                    lockPositionSize,
                    lockContents,
                    bgR, bgG, bgB, bgOpacity,
                    borderR, borderG, borderB,
                    borderWidthPt,
                    borderDashed,
                    borderRadiusPt);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (!(o instanceof EmbeddedFreeTextSnapshot)) return false;
            EmbeddedFreeTextSnapshot other = (EmbeddedFreeTextSnapshot) o;
            if (pageNumber != other.pageNumber) return false;
            if (objectNumber != other.objectNumber) return false;
            if (userResized != other.userResized) return false;
            if (Float.compare(fontSizePt, other.fontSizePt) != 0) return false;
            if (Float.compare(lineHeight, other.lineHeight) != 0) return false;
            if (Float.compare(textIndentPt, other.textIndentPt) != 0) return false;
            if (Float.compare(textR, other.textR) != 0) return false;
            if (Float.compare(textG, other.textG) != 0) return false;
            if (Float.compare(textB, other.textB) != 0) return false;
            if (fontFamily != other.fontFamily) return false;
            if (styleFlags != other.styleFlags) return false;
            if (alignment != other.alignment) return false;
            if (rotationDeg != other.rotationDeg) return false;
            if (lockPositionSize != other.lockPositionSize) return false;
            if (lockContents != other.lockContents) return false;
            if (Float.compare(bgR, other.bgR) != 0) return false;
            if (Float.compare(bgG, other.bgG) != 0) return false;
            if (Float.compare(bgB, other.bgB) != 0) return false;
            if (Float.compare(bgOpacity, other.bgOpacity) != 0) return false;
            if (Float.compare(borderR, other.borderR) != 0) return false;
            if (Float.compare(borderG, other.borderG) != 0) return false;
            if (Float.compare(borderB, other.borderB) != 0) return false;
            if (Float.compare(borderWidthPt, other.borderWidthPt) != 0) return false;
            if (borderDashed != other.borderDashed) return false;
            if (Float.compare(borderRadiusPt, other.borderRadiusPt) != 0) return false;
            if (!boundsDoc.equals(other.boundsDoc)) return false;
            return text.equals(other.text);
        }

        @Override
        public int hashCode() {
            int result = pageNumber;
            result = 31 * result + (int) (objectNumber ^ (objectNumber >>> 32));
            result = 31 * result + boundsDoc.hashCode();
            result = 31 * result + text.hashCode();
            result = 31 * result + (userResized ? 1 : 0);
            result = 31 * result + Float.floatToIntBits(fontSizePt);
            result = 31 * result + Float.floatToIntBits(lineHeight);
            result = 31 * result + Float.floatToIntBits(textIndentPt);
            result = 31 * result + Float.floatToIntBits(textR);
            result = 31 * result + Float.floatToIntBits(textG);
            result = 31 * result + Float.floatToIntBits(textB);
            result = 31 * result + fontFamily;
            result = 31 * result + styleFlags;
            result = 31 * result + alignment;
            result = 31 * result + rotationDeg;
            result = 31 * result + (lockPositionSize ? 1 : 0);
            result = 31 * result + (lockContents ? 1 : 0);
            result = 31 * result + Float.floatToIntBits(bgR);
            result = 31 * result + Float.floatToIntBits(bgG);
            result = 31 * result + Float.floatToIntBits(bgB);
            result = 31 * result + Float.floatToIntBits(bgOpacity);
            result = 31 * result + Float.floatToIntBits(borderR);
            result = 31 * result + Float.floatToIntBits(borderG);
            result = 31 * result + Float.floatToIntBits(borderB);
            result = 31 * result + Float.floatToIntBits(borderWidthPt);
            result = 31 * result + (borderDashed ? 1 : 0);
            result = 31 * result + Float.floatToIntBits(borderRadiusPt);
            return result;
        }
    }

    @Nullable
    private EmbeddedFreeTextSnapshot snapshotEmbeddedFreeTextByObjectNumber(long objectId, @Nullable Annotation annotHint) {
        if (host.sidecarSessionOrNull() != null) return null;
        if (objectId <= 0L) return null;

        Annotation a = annotHint;
        if (a == null || a.objectNumber != objectId) {
            a = findAnnotationByObjectNumber(host.embeddedAnnotationsOrNull(), objectId);
        }
        if (a == null || a.type != Annotation.Type.FREETEXT) return null;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return null;
        int page = host.pageNumber();

        RectF bounds = new RectF(a);
        String text = a.text != null ? a.text : "";

        boolean userResized = true;
        float fontSizePt = 12.0f;
        float lineHeight = 1.2f;
        float textIndentPt = 0.0f;
        float textR = 0.0f, textG = 0.0f, textB = 0.0f;
        int fontFamily = TextFontFamily.SANS;
        int styleFlags = 0;
        int alignment = 0;
        int rotationDeg = 0;
        boolean lockPos = false;
        boolean lockContents = false;
        float bgR = 1.0f, bgG = 1.0f, bgB = 1.0f, bgOpacity = 0.0f;
        float borderR = 0.0f, borderG = 0.0f, borderB = 0.0f, borderWidthPt = 0.0f;
        boolean borderDashed = false;
        float borderRadiusPt = 0.0f;

        try { userResized = controller.rawRepository().getFreeTextUserResizedByObjectNumber(page, objectId); } catch (Throwable ignore) { userResized = true; }
        try { fontSizePt = controller.rawRepository().getFreeTextFontSizeByObjectNumber(page, objectId); } catch (Throwable ignore) { fontSizePt = 12.0f; }
        try { fontFamily = controller.rawRepository().getFreeTextFontFamilyByObjectNumber(page, objectId); } catch (Throwable ignore) { fontFamily = TextFontFamily.SANS; }
        try { styleFlags = controller.rawRepository().getFreeTextStyleFlagsByObjectNumber(page, objectId); } catch (Throwable ignore) { styleFlags = 0; }
        try {
            float[] p = controller.rawRepository().getFreeTextParagraphByObjectNumber(page, objectId);
            if (p != null && p.length >= 1) lineHeight = p[0];
            if (p != null && p.length >= 2) textIndentPt = p[1];
        } catch (Throwable ignore) {}
        try { alignment = controller.rawRepository().getFreeTextAlignmentByObjectNumber(page, objectId); } catch (Throwable ignore) { alignment = 0; }
        try { rotationDeg = controller.rawRepository().getFreeTextRotationByObjectNumber(page, objectId); } catch (Throwable ignore) { rotationDeg = 0; }
        try {
            int flags = controller.rawRepository().getFreeTextFlagsByObjectNumber(page, objectId);
            lockPos = (flags & PDF_ANNOT_FLAG_LOCKED) != 0;
            lockContents = (flags & PDF_ANNOT_FLAG_LOCKED_CONTENTS) != 0;
        } catch (Throwable ignore) {
            lockPos = false;
            lockContents = false;
        }
        try {
            float[] c = controller.rawRepository().getFreeTextTextColorByObjectNumber(page, objectId);
            if (c != null && c.length >= 3) {
                textR = c[0];
                textG = c[1];
                textB = c[2];
            }
        } catch (Throwable ignore) {
        }
        try {
            float[] bg = controller.rawRepository().getFreeTextBackgroundByObjectNumber(page, objectId);
            if (bg != null && bg.length >= 4) {
                bgR = bg[0];
                bgG = bg[1];
                bgB = bg[2];
                bgOpacity = bg[3];
            }
        } catch (Throwable ignore) {
        }
        try {
            float[] b = controller.rawRepository().getFreeTextBorderByObjectNumber(page, objectId);
            if (b != null && b.length >= 6) {
                borderR = b[0];
                borderG = b[1];
                borderB = b[2];
                borderWidthPt = b[3];
                borderDashed = b[4] > 0.5f;
                borderRadiusPt = b[5];
            }
        } catch (Throwable ignore) {
        }

        return new EmbeddedFreeTextSnapshot(
                page,
                objectId,
                bounds,
                text,
                userResized,
                fontSizePt,
                lineHeight,
                textIndentPt,
                textR,
                textG,
                textB,
                fontFamily,
                styleFlags,
                alignment,
                rotationDeg,
                lockPos,
                lockContents,
                bgR,
                bgG,
                bgB,
                bgOpacity,
                borderR,
                borderG,
                borderB,
                borderWidthPt,
                borderDashed,
                borderRadiusPt);
    }

    private void applyEmbeddedFreeTextSnapshot(@NonNull EmbeddedFreeTextSnapshot snapshot, long objectId) {
        if (snapshot == null) return;
        if (host.sidecarSessionOrNull() != null) return;
        if (objectId <= 0L) return;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return;

        int page = snapshot.pageNumber;
        try {
            controller.rawRepository().updateAnnotationContentsByObjectNumber(page, objectId, snapshot.text);
        } catch (Throwable ignore) {
        }
        try {
            controller.rawRepository().updateAnnotationRectByObjectNumber(
                    page,
                    objectId,
                    snapshot.boundsDoc.left,
                    snapshot.boundsDoc.top,
                    snapshot.boundsDoc.right,
                    snapshot.boundsDoc.bottom);
        } catch (Throwable ignore) {
        }
        try {
            controller.rawRepository().setFreeTextUserResizedByObjectNumber(page, objectId, snapshot.userResized);
        } catch (Throwable ignore) {
        }
        try {
            controller.rawRepository().updateFreeTextStyleByObjectNumber(page, objectId, snapshot.fontSizePt, snapshot.textR, snapshot.textG, snapshot.textB);
        } catch (Throwable ignore) {
        }
        try { controller.rawRepository().updateFreeTextParagraphByObjectNumber(page, objectId, snapshot.lineHeight, snapshot.textIndentPt); } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextFontFamilyByObjectNumber(page, objectId, snapshot.fontFamily); } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextStyleFlagsByObjectNumber(page, objectId, snapshot.styleFlags); } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextAlignmentByObjectNumber(page, objectId, snapshot.alignment); } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextRotationByObjectNumber(page, objectId, snapshot.rotationDeg); } catch (Throwable ignore) {}
        try { controller.rawRepository().updateFreeTextBackgroundByObjectNumber(page, objectId, snapshot.bgR, snapshot.bgG, snapshot.bgB, snapshot.bgOpacity); } catch (Throwable ignore) {}
        try {
            controller.rawRepository().updateFreeTextBorderByObjectNumber(
                    page,
                    objectId,
                    snapshot.borderR,
                    snapshot.borderG,
                    snapshot.borderB,
                    snapshot.borderWidthPt,
                    snapshot.borderDashed,
                    snapshot.borderRadiusPt);
        } catch (Throwable ignore) {
        }
        try { controller.rawRepository().updateFreeTextLocksByObjectNumber(page, objectId, snapshot.lockPositionSize, snapshot.lockContents); } catch (Throwable ignore) {}

        try { controller.markDocumentDirty(); } catch (Throwable ignore) {}

        host.requestFullRedrawAfterNextAnnotationLoad();
        host.discardRenderedPage();
        host.loadAnnotations();
        try { host.selectionManager().selectByObjectNumber(objectId, new RectF(snapshot.boundsDoc), host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
    }

    @Nullable
    private static Annotation findNewFreeText(@Nullable Annotation[] after,
                                              @NonNull Set<Long> beforeIds,
                                              @NonNull RectF desiredBounds,
                                              @NonNull String desiredText) {
        if (after == null || after.length == 0) return null;

        Annotation best = null;
        float bestDist = Float.MAX_VALUE;
        float cx = desiredBounds.centerX();
        float cy = desiredBounds.centerY();

        for (Annotation a : after) {
            if (a == null) continue;
            if (a.type != Annotation.Type.FREETEXT) continue;
            if (a.objectNumber <= 0L) continue;
            if (beforeIds.contains(a.objectNumber)) continue;

            float dx = a.centerX() - cx;
            float dy = a.centerY() - cy;
            float dist = (dx * dx) + (dy * dy);

            // Prefer exact text match when available; fall back to proximity.
            if (desiredText != null && !desiredText.isEmpty()) {
                String t = a.text != null ? a.text : "";
                if (!t.equals(desiredText)) {
                    dist += 5_000_000f;
                }
            }

            if (best == null || dist < bestDist) {
                best = a;
                bestDist = dist;
            }
        }
        return best;
    }

    private void deleteEmbeddedFreeTextByObjectNumber(long objectId) {
        if (host.sidecarSessionOrNull() != null) return;
        if (objectId <= 0L) return;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return;
        try {
            controller.deleteAnnotationByObjectNumber(host.pageNumber(), objectId);
        } catch (Throwable ignore) {
        }
        host.requestFullRedrawAfterNextAnnotationLoad();
        host.discardRenderedPage();
        host.loadAnnotations();
        try { host.setAnnotationSelectionBox(null); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
    }

    private long createEmbeddedFreeTextFromSnapshot(@NonNull EmbeddedFreeTextSnapshot snapshot) {
        if (snapshot == null) return -1L;
        if (host.sidecarSessionOrNull() != null) return -1L;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return -1L;

        float scale = host.scale();
        if (scale <= 0f) return -1L;
        float docH = host.viewHeightPx() / scale;

        final RectF desiredBounds = new RectF(snapshot.boundsDoc);
        final String text = snapshot.text != null ? snapshot.text : "";
        final int page = snapshot.pageNumber;

        final PointF[] rectTwoPoints = TextAnnotationQuadPoints.fromBounds(false,
                desiredBounds.left,
                desiredBounds.top,
                desiredBounds.right,
                desiredBounds.bottom,
                docH);

        long newObjectId = -1L;
        try {
            Annotation[] before = controller.annotations(page);
            Set<Long> beforeIds = new HashSet<>();
            if (before != null) {
                for (Annotation a : before) {
                    if (a == null) continue;
                    if (a.objectNumber > 0L) beforeIds.add(a.objectNumber);
                }
            }

            controller.addTextAnnotation(page, rectTwoPoints, text);

            Annotation[] after = controller.annotations(page);
            Annotation created = findNewFreeText(after, beforeIds, desiredBounds, text);
            if (created != null) newObjectId = created.objectNumber;
        } catch (Throwable ignore) {
            newObjectId = -1L;
        }

        if (newObjectId > 0L) {
            applyEmbeddedFreeTextSnapshot(snapshot, newObjectId);
        } else {
            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        }

        return newObjectId;
    }

    /**
     * Undo/redo op that toggles an embedded FreeText annotation's presence
     * (create ↔ delete) using a captured snapshot.
     */
    private final class EmbeddedFreeTextPresenceOp implements TextAnnotationUndoController.Op {
        @NonNull private final EmbeddedFreeTextSnapshot snapshot;
        private long liveObjectId;
        private boolean present;

        EmbeddedFreeTextPresenceOp(@NonNull EmbeddedFreeTextSnapshot snapshot, long liveObjectId, boolean present) {
            this.snapshot = snapshot;
            this.liveObjectId = liveObjectId;
            this.present = present;
        }

        private void toggle() {
            if (present) {
                if (liveObjectId > 0L) {
                    deleteEmbeddedFreeTextByObjectNumber(liveObjectId);
                }
                liveObjectId = -1L;
                present = false;
                return;
            }

            liveObjectId = createEmbeddedFreeTextFromSnapshot(snapshot);
            present = (liveObjectId > 0L);
        }

        @Override public void undo() { toggle(); }
        @Override public void redo() { toggle(); }
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
            Annotation newAnnot = null;
            long newObjectId = -1L;
            try {
                Annotation[] before = controller.annotations(page);
                Set<Long> beforeIds = new HashSet<>();
                if (before != null) {
                    for (Annotation a : before) {
                        if (a == null) continue;
                        if (a.objectNumber > 0L) beforeIds.add(a.objectNumber);
                    }
                }

                controller.addTextAnnotation(page, rectTwoPoints, text);

                Annotation[] after = controller.annotations(page);
                newAnnot = findNewFreeText(after, beforeIds, desiredBounds, text);
                if (newAnnot != null) newObjectId = newAnnot.objectNumber;

                if (newObjectId > 0L) {
                    try {
                        float r = Color.red(payload.textColorArgb) / 255f;
                        float g = Color.green(payload.textColorArgb) / 255f;
                        float b = Color.blue(payload.textColorArgb) / 255f;
                        controller.rawRepository().updateFreeTextStyleByObjectNumber(page, newObjectId, payload.fontSizePt, r, g, b);
                    } catch (Throwable ignore) {}
                    try {
                        float r = Color.red(payload.backgroundColorArgb) / 255f;
                        float g = Color.green(payload.backgroundColorArgb) / 255f;
                        float b = Color.blue(payload.backgroundColorArgb) / 255f;
                        controller.rawRepository().updateFreeTextBackgroundByObjectNumber(page, newObjectId, r, g, b, payload.backgroundOpacity);
                    } catch (Throwable ignore) {}
                    try {
                        float r = Color.red(payload.borderColorArgb) / 255f;
                        float g = Color.green(payload.borderColorArgb) / 255f;
                        float b = Color.blue(payload.borderColorArgb) / 255f;
                        controller.rawRepository().updateFreeTextBorderByObjectNumber(page, newObjectId, r, g, b, payload.borderWidthPt, payload.borderDashed, payload.borderRadiusPt);
                    } catch (Throwable ignore) {}
                    try { controller.rawRepository().updateFreeTextFontFamilyByObjectNumber(page, newObjectId, payload.fontFamily); } catch (Throwable ignore) {}
                    try { controller.rawRepository().updateFreeTextStyleFlagsByObjectNumber(page, newObjectId, payload.fontStyleFlags); } catch (Throwable ignore) {}
                    try { controller.rawRepository().updateFreeTextParagraphByObjectNumber(page, newObjectId, payload.lineHeight, payload.textIndentPt); } catch (Throwable ignore) {}
                    try { controller.rawRepository().updateFreeTextAlignmentByObjectNumber(page, newObjectId, payload.alignment); } catch (Throwable ignore) {}
                    try { controller.rawRepository().updateFreeTextRotationByObjectNumber(page, newObjectId, payload.rotationDeg); } catch (Throwable ignore) {}
                    try { controller.rawRepository().updateFreeTextLocksByObjectNumber(page, newObjectId, payload.lockPositionSize, payload.lockContents); } catch (Throwable ignore) {}
                    try { controller.rawRepository().setFreeTextUserResizedByObjectNumber(page, newObjectId, payload.userResized); } catch (Throwable ignore) {}
                    try { controller.markDocumentDirty(); } catch (Throwable ignore) {}
                }
            } catch (Throwable ignore) {
                newAnnot = null;
                newObjectId = -1L;
            }

            final Annotation finalAnnot = newAnnot;
            final long finalId = newObjectId;
            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                // Ensure the next draw uses a full redraw (FreeText appearances can be missed by incremental updates).
                host.requestFullRedrawAfterNextAnnotationLoad();
                host.discardRenderedPage();
                host.loadAnnotations();

                if (finalId > 0L) {
                    RectF bounds = finalAnnot != null ? new RectF(finalAnnot) : new RectF(desiredBounds);
                    try { host.selectionManager().selectByObjectNumber(finalId, bounds, host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore2) {}
                    EmbeddedFreeTextSnapshot snapshot = snapshotEmbeddedFreeTextByObjectNumber(finalId, finalAnnot);
                    if (snapshot != null) {
                        undoController.push(new EmbeddedFreeTextPresenceOp(snapshot, finalId, true));
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
        Annotation annot = router.selectedEmbeddedAnnotationOrNull();
        if (annot == null || annot.type != Annotation.Type.FREETEXT) return false;

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
            Annotation newAnnot = null;
            long newObjectId = -1L;
            try {
                Annotation[] before = controller.annotations(page);
                Set<Long> beforeIds = new HashSet<>();
                if (before != null) {
                    for (Annotation a : before) {
                        if (a == null) continue;
                        if (a.objectNumber > 0L) beforeIds.add(a.objectNumber);
                    }
                }

                controller.addTextAnnotation(page, rectTwoPoints, text);

                Annotation[] after = controller.annotations(page);
                newAnnot = findNewFreeText(after, beforeIds, desiredBounds, text);
                if (newAnnot != null) newObjectId = newAnnot.objectNumber;

                // Best-effort: copy the subset of styling attributes we can query by object id.
                if (sourceObjectId > 0L && newObjectId > 0L) {
                    try {
                        int family = controller.rawRepository().getFreeTextFontFamilyByObjectNumber(page, sourceObjectId);
                        controller.rawRepository().updateFreeTextFontFamilyByObjectNumber(page, newObjectId, family);
                    } catch (Throwable ignore) {}
                    try {
                        int flags = controller.rawRepository().getFreeTextStyleFlagsByObjectNumber(page, sourceObjectId);
                        controller.rawRepository().updateFreeTextStyleFlagsByObjectNumber(page, newObjectId, flags);
                    } catch (Throwable ignore) {}
                    try {
                        float[] p = controller.rawRepository().getFreeTextParagraphByObjectNumber(page, sourceObjectId);
                        if (p != null && p.length >= 2) {
                            controller.rawRepository().updateFreeTextParagraphByObjectNumber(page, newObjectId, p[0], p[1]);
                        }
                    } catch (Throwable ignore) {}
                    try {
                        int q = controller.rawRepository().getFreeTextAlignmentByObjectNumber(page, sourceObjectId);
                        controller.rawRepository().updateFreeTextAlignmentByObjectNumber(page, newObjectId, q);
                    } catch (Throwable ignore) {}
                    try {
                        int rot = controller.rawRepository().getFreeTextRotationByObjectNumber(page, sourceObjectId);
                        controller.rawRepository().updateFreeTextRotationByObjectNumber(page, newObjectId, rot);
                    } catch (Throwable ignore) {}
                    try {
                        int flags = controller.rawRepository().getFreeTextFlagsByObjectNumber(page, sourceObjectId);
                        boolean lockPos = (flags & PDF_ANNOT_FLAG_LOCKED) != 0;
                        boolean lockContents = (flags & PDF_ANNOT_FLAG_LOCKED_CONTENTS) != 0;
                        controller.rawRepository().updateFreeTextLocksByObjectNumber(page, newObjectId, lockPos, lockContents);
                    } catch (Throwable ignore) {}
                    try {
                        boolean userResized = controller.rawRepository().getFreeTextUserResizedByObjectNumber(page, sourceObjectId);
                        controller.rawRepository().setFreeTextUserResizedByObjectNumber(page, newObjectId, userResized);
                    } catch (Throwable ignore) {}
                    try { controller.markDocumentDirty(); } catch (Throwable ignore) {}
                }
            } catch (Throwable ignore) {
                newAnnot = null;
                newObjectId = -1L;
            }

            final Annotation finalAnnot = newAnnot;
            final long finalId = newObjectId;
            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                // Ensure the next draw uses a full redraw (FreeText appearances can be missed by incremental updates).
                host.requestFullRedrawAfterNextAnnotationLoad();
                host.discardRenderedPage();
                host.loadAnnotations();

                if (finalId > 0L) {
                    RectF bounds = finalAnnot != null ? new RectF(finalAnnot) : new RectF(desiredBounds);
                    try { host.selectionManager().selectByObjectNumber(finalId, bounds, host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore2) {}
                    EmbeddedFreeTextSnapshot snapshot = snapshotEmbeddedFreeTextByObjectNumber(finalId, finalAnnot);
                    if (snapshot != null) {
                        undoController.push(new EmbeddedFreeTextPresenceOp(snapshot, finalId, true));
                    }
                } else {
                    try { host.setAnnotationSelectionBox(new RectF(desiredBounds)); } catch (Throwable ignore2) {}
                }
                host.invalidateOverlay();
            });
        });

        return true;
    }

    private boolean isEmbeddedFreeTextPositionLocked(long objectId) {
        if (host.sidecarSessionOrNull() != null) return false;
        if (objectId <= 0L) return false;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        try {
            int flags = controller.rawRepository().getFreeTextFlagsByObjectNumber(host.pageNumber(), objectId);
            return (flags & PDF_ANNOT_FLAG_LOCKED) != 0;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private boolean isEmbeddedFreeTextContentsLocked(long objectId) {
        if (host.sidecarSessionOrNull() != null) return false;
        if (objectId <= 0L) return false;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return false;
        try {
            int flags = controller.rawRepository().getFreeTextFlagsByObjectNumber(host.pageNumber(), objectId);
            return (flags & PDF_ANNOT_FLAG_LOCKED_CONTENTS) != 0;
        } catch (Throwable ignore) {
            return false;
        }
    }

    @Nullable
    private RectF normalizeTextAnnotationBoundsForCommit(@NonNull RectF boundsDoc) {
        if (boundsDoc == null) return null;
        float scale = host.scale();
        if (scale <= 0f) return null;
        final float docWidth = host.viewWidthPx() / scale;
        final float docHeight = host.viewHeightPx() / scale;

        float left = Math.min(boundsDoc.left, boundsDoc.right);
        float right = Math.max(boundsDoc.left, boundsDoc.right);
        float top = Math.min(boundsDoc.top, boundsDoc.bottom);
        float bottom = Math.max(boundsDoc.top, boundsDoc.bottom);

        // Enforce a minimum on-screen size so the box remains selectable.
        float minEdgeDoc = ItemSelectionHandles.minEdgePx(host.resources()) / scale;
        if ((right - left) < minEdgeDoc) right = Math.min(docWidth, left + minEdgeDoc);
        if ((bottom - top) < minEdgeDoc) bottom = Math.min(docHeight, top + minEdgeDoc);

        // Clamp to doc bounds.
        left = Math.max(0f, Math.min(left, docWidth));
        right = Math.max(0f, Math.min(right, docWidth));
        top = Math.max(0f, Math.min(top, docHeight));
        bottom = Math.max(0f, Math.min(bottom, docHeight));

        if (right <= left || bottom <= top) return null;
        return new RectF(left, top, right, bottom);
    }

    @Nullable
    private static Annotation findAnnotationByObjectNumber(@Nullable Annotation[] annots, long objectId) {
        if (annots == null || objectId <= 0L) return null;
        for (Annotation a : annots) {
            if (a != null && a.objectNumber == objectId) return a;
        }
        return null;
    }
}
