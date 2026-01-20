package org.opendroidpdf.app.annotation;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;
import org.opendroidpdf.core.MuPdfController;

final class EmbeddedFreeTextUndo {
    private final TextAnnotationPageDelegate.Host host;
    private final TextAnnotationUndoController undoController;
    private final EmbeddedFreeTextRepositoryOps repoOps;

    EmbeddedFreeTextUndo(@NonNull TextAnnotationPageDelegate.Host host,
                         @NonNull TextAnnotationUndoController undoController,
                         @NonNull EmbeddedFreeTextRepositoryOps repoOps) {
        this.host = host;
        this.undoController = undoController;
        this.repoOps = repoOps;
    }

    TextAnnotationUndoController.Op snapshotOp(@NonNull EmbeddedFreeTextSnapshot before,
                                              @NonNull EmbeddedFreeTextSnapshot after,
                                              long objectId) {
        return new TextAnnotationUndoController.Op() {
            @Override public void undo() { applySnapshot(before, objectId); }
            @Override public void redo() { applySnapshot(after, objectId); }
        };
    }

    TextAnnotationUndoController.Op presenceOp(@NonNull EmbeddedFreeTextSnapshot snapshot, long liveObjectId, boolean present) {
        return new EmbeddedFreeTextPresenceOp(snapshot, liveObjectId, present);
    }

    void pushPresence(@NonNull EmbeddedFreeTextSnapshot snapshot, long liveObjectId, boolean present) {
        undoController.push(presenceOp(snapshot, liveObjectId, present));
    }

    void deleteEmbeddedFreeTextByObjectNumber(long objectId) {
        if (host.sidecarSessionOrNull() != null) return;
        if (objectId <= 0L) return;
        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return;

        repoOps.tryDeleteAnnotationByObjectNumber(controller, host.pageNumber(), objectId);

        host.requestFullRedrawAfterNextAnnotationLoad();
        host.discardRenderedPage();
        host.loadAnnotations();
        try { host.setAnnotationSelectionBox(null); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
    }

    long createEmbeddedFreeTextFromSnapshot(@NonNull EmbeddedFreeTextSnapshot snapshot) {
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

        EmbeddedFreeTextRepositoryOps.CreatedFreeText created =
                repoOps.tryAddFreeTextAnnotation(controller, page, rectTwoPoints, desiredBounds, text);
        long newObjectId = created.objectId;

        if (newObjectId > 0L) {
            applySnapshot(snapshot, newObjectId);
        } else {
            host.requestFullRedrawAfterNextAnnotationLoad();
            host.discardRenderedPage();
            host.loadAnnotations();
            try { host.invalidateOverlay(); } catch (Throwable ignore) {}
        }

        return newObjectId;
    }

    @Nullable
    EmbeddedFreeTextSnapshot snapshotByObjectNumber(long objectId, @Nullable Annotation annotHint) {
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

        boolean userResized = repoOps.getFreeTextUserResizedOrDefault(controller, page, objectId, true);
        float fontSizePt = repoOps.getFreeTextFontSizeOrDefaultRaw(controller, page, objectId, 12.0f);
        float lineHeight = 1.2f;
        float textIndentPt = 0.0f;
        float textR = 0.0f, textG = 0.0f, textB = 0.0f;
        int fontFamily = repoOps.getFreeTextFontFamilyOrDefaultRaw(controller, page, objectId, TextFontFamily.SANS);
        int styleFlags = repoOps.getFreeTextStyleFlagsOrDefaultRaw(controller, page, objectId, 0);
        int alignment = repoOps.getFreeTextAlignmentOrDefaultRaw(controller, page, objectId, 0);
        int rotationDeg = repoOps.getFreeTextRotationOrDefaultRaw(controller, page, objectId, 0);

        int flags = repoOps.getFreeTextFlagsOrDefault(controller, page, objectId, 0);
        boolean lockPos = (flags & EmbeddedFreeTextRepositoryOps.PDF_ANNOT_FLAG_LOCKED) != 0;
        boolean lockContents = (flags & EmbeddedFreeTextRepositoryOps.PDF_ANNOT_FLAG_LOCKED_CONTENTS) != 0;

        float bgR = 1.0f, bgG = 1.0f, bgB = 1.0f, bgOpacity = 0.0f;
        float borderR = 0.0f, borderG = 0.0f, borderB = 0.0f, borderWidthPt = 0.0f;
        boolean borderDashed = false;
        float borderRadiusPt = 0.0f;

        float[] p = repoOps.getFreeTextParagraphOrNull(controller, page, objectId);
        if (p != null && p.length >= 1) lineHeight = p[0];
        if (p != null && p.length >= 2) textIndentPt = p[1];

        float[] c = repoOps.getFreeTextTextColorOrNull(controller, page, objectId);
        if (c != null && c.length >= 3) {
            textR = c[0];
            textG = c[1];
            textB = c[2];
        }

        float[] bg = repoOps.getFreeTextBackgroundOrNull(controller, page, objectId);
        if (bg != null && bg.length >= 4) {
            bgR = bg[0];
            bgG = bg[1];
            bgB = bg[2];
            bgOpacity = bg[3];
        }

        float[] b = repoOps.getFreeTextBorderOrNull(controller, page, objectId);
        if (b != null && b.length >= 6) {
            borderR = b[0];
            borderG = b[1];
            borderB = b[2];
            borderWidthPt = b[3];
            borderDashed = b[4] > 0.5f;
            borderRadiusPt = b[5];
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

    void applySnapshot(@NonNull EmbeddedFreeTextSnapshot snapshot, long objectId) {
        if (snapshot == null) return;
        if (host.sidecarSessionOrNull() != null) return;
        if (objectId <= 0L) return;

        MuPdfController controller = host.muPdfControllerOrNull();
        if (controller == null) return;

        int page = snapshot.pageNumber;

        repoOps.tryUpdateAnnotationContentsByObjectNumber(controller, page, objectId, snapshot.text);
        repoOps.tryUpdateAnnotationRectByObjectNumber(controller, page, objectId, snapshot.boundsDoc);
        repoOps.trySetFreeTextUserResizedByObjectNumber(controller, page, objectId, snapshot.userResized);
        repoOps.tryUpdateFreeTextStyleByObjectNumber(controller, page, objectId, snapshot.fontSizePt, snapshot.textR, snapshot.textG, snapshot.textB);
        repoOps.tryUpdateFreeTextParagraphByObjectNumber(controller, page, objectId, snapshot.lineHeight, snapshot.textIndentPt);
        repoOps.tryUpdateFreeTextFontFamilyByObjectNumber(controller, page, objectId, snapshot.fontFamily);
        repoOps.tryUpdateFreeTextStyleFlagsByObjectNumber(controller, page, objectId, snapshot.styleFlags);
        repoOps.tryUpdateFreeTextAlignmentByObjectNumber(controller, page, objectId, snapshot.alignment);
        repoOps.tryUpdateFreeTextRotationByObjectNumber(controller, page, objectId, snapshot.rotationDeg);
        repoOps.tryUpdateFreeTextBackgroundByObjectNumber(controller, page, objectId, snapshot.bgR, snapshot.bgG, snapshot.bgB, snapshot.bgOpacity);
        repoOps.tryUpdateFreeTextBorderByObjectNumber(
                controller,
                page,
                objectId,
                snapshot.borderR,
                snapshot.borderG,
                snapshot.borderB,
                snapshot.borderWidthPt,
                snapshot.borderDashed,
                snapshot.borderRadiusPt);
        repoOps.tryUpdateFreeTextLocksByObjectNumber(controller, page, objectId, snapshot.lockPositionSize, snapshot.lockContents);
        repoOps.tryMarkDocumentDirty(controller);

        host.requestFullRedrawAfterNextAnnotationLoad();
        host.discardRenderedPage();
        host.loadAnnotations();
        try { host.selectionManager().selectByObjectNumber(objectId, new RectF(snapshot.boundsDoc), host.selectionUiBridge().selectionBoxHost()); } catch (Throwable ignore) {}
        try { host.invalidateOverlay(); } catch (Throwable ignore) {}
    }

    @Nullable
    RectF normalizeTextAnnotationBoundsForCommit(@NonNull RectF boundsDoc) {
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
    static Annotation findAnnotationByObjectNumber(@Nullable Annotation[] annots, long objectId) {
        if (annots == null || objectId <= 0L) return null;
        for (Annotation a : annots) {
            if (a != null && a.objectNumber == objectId) return a;
        }
        return null;
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
}

final class EmbeddedFreeTextSnapshot {
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

