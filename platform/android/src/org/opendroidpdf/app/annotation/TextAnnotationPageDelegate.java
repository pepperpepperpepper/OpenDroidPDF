package org.opendroidpdf.app.annotation;

import android.content.ClipData;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.app.drawing.InkController;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.selection.SelectionUiBridge;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarNote;
import org.opendroidpdf.core.MuPdfController;

import java.util.List;

 /**
 * Extracted text-annotation logic from {@code MuPDFPageView} to keep the view smaller and to
 * centralize the embedded-vs-sidecar behavior differences behind a single adapter.
 */
public final class TextAnnotationPageDelegate {

    public interface Host {
        @NonNull Context context();
        @NonNull Resources resources();
        float scale();
        int viewWidthPx();
        int viewHeightPx();
        int pageNumber();

        @Nullable MuPdfController muPdfControllerOrNull();
        @Nullable SidecarAnnotationSession sidecarSessionOrNull();

        @NonNull SidecarSelectionController sidecarSelectionController();
        @NonNull AnnotationSelectionManager selectionManager();
        @NonNull SelectionUiBridge selectionUiBridge();
        @NonNull InkController inkController();
        @NonNull AnnotationUiController annotationUiController();

        @Nullable Annotation[] embeddedAnnotationsOrNull();

        void requestFullRedrawAfterNextAnnotationLoad();
        void discardRenderedPage();
        void loadAnnotations();
        void invalidateOverlay();
        void setAnnotationSelectionBox(@Nullable RectF rectDoc);
    }

    private final Host host;
    private final TextAnnotationUndoController undoController = new TextAnnotationUndoController();

    private final TextAnnotationEmbeddedFreeTextOps embeddedOps;
    private final TextAnnotationSidecarNoteOps sidecarOps;

    public TextAnnotationPageDelegate(@NonNull Host host) {
        this.host = host;
        this.embeddedOps = new TextAnnotationEmbeddedFreeTextOps(this, host, undoController);
        this.sidecarOps = new TextAnnotationSidecarNoteOps(this, host);
    }

    public void clearEmbeddedTextUndoHistory() {
        undoController.clear();
    }

    public boolean hasEmbeddedTextUndo() { return undoController.hasUndo(); }

    public boolean hasEmbeddedTextRedo() { return undoController.hasRedo(); }

    public long embeddedTextHistoryLastMutationUptimeMs() { return undoController.lastMutationUptimeMs(); }

    public boolean undoLastEmbeddedTextEdit() {
        if (host.sidecarSessionOrNull() != null) return false;
        return undoController.undoLast();
    }

    public boolean redoLastEmbeddedTextEdit() {
        if (host.sidecarSessionOrNull() != null) return false;
        return undoController.redoLast();
    }

    @Nullable
    public Annotation selectedEmbeddedAnnotationOrNull() {
        if (host.sidecarSessionOrNull() != null) return null;
        Annotation[] annots = host.embeddedAnnotationsOrNull();
        if (annots == null || annots.length == 0) return null;

        // Prefer stable identity if available.
        long objectId = -1L;
        try { objectId = host.selectionManager().selectedObjectNumber(); } catch (Throwable ignore) { objectId = -1L; }
        if (objectId > 0L) {
            Annotation byId = findAnnotationByObjectNumber(annots, objectId);
            if (byId != null) return byId;
        }

        int idx = host.selectionManager().selectedIndex();
        if (idx < 0 || idx >= annots.length) return null;
        return annots[idx];
    }

    @Nullable
    public SidecarNote sidecarNoteById(@NonNull String noteId) {
        SidecarAnnotationSession sidecar = host.sidecarSessionOrNull();
        if (sidecar == null) return null;
        if (noteId == null || noteId.trim().isEmpty()) return null;
        try {
            List<SidecarNote> notes = sidecar.notesForPage(host.pageNumber());
            if (notes == null || notes.isEmpty()) return null;
            for (SidecarNote n : notes) {
                if (n != null && noteId.equals(n.id)) return n;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    @Nullable
    public String sidecarNoteTextById(@NonNull String noteId) {
        SidecarNote note = sidecarNoteById(noteId);
        return note != null ? note.text : null;
    }

    /** Selects an embedded PDF annotation by stable object number, if present on this page. */
    public boolean selectEmbeddedAnnotationByObjectNumber(long objectNumber) {
        if (objectNumber <= 0L) return false;
        Annotation[] annots = host.embeddedAnnotationsOrNull();
        if (annots == null || annots.length == 0) return false;
        for (int i = 0; i < annots.length; i++) {
            Annotation a = annots[i];
            if (a == null || a.objectNumber != objectNumber) continue;
            try {
                RectF bounds = new RectF(a);
                host.selectionManager().select(i, objectNumber, bounds, host.selectionUiBridge().selectionBoxHost());
            } catch (Throwable ignore) {
                try { host.setAnnotationSelectionBox(new RectF(a)); } catch (Throwable ignore2) {}
            }
            return true;
        }
        return false;
    }

    /** Selects a sidecar note by id, if present on this page. */
    public boolean selectSidecarNoteById(@NonNull String noteId) {
        if (noteId == null || noteId.trim().isEmpty()) return false;
        try {
            return host.sidecarSelectionController().selectNoteById(noteId);
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Selects a sidecar highlight by id, if present on this page. */
    public boolean selectSidecarHighlightById(@NonNull String highlightId) {
        if (highlightId == null || highlightId.trim().isEmpty()) return false;
        try {
            return host.sidecarSelectionController().selectHighlightById(highlightId);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public boolean commitTextAnnotationRectByObjectNumber(long objectId, @NonNull RectF boundsDoc, boolean markUserResized) {
        return embeddedOps.commitTextAnnotationRectByObjectNumber(objectId, boundsDoc, markUserResized);
    }

    public boolean deleteEmbeddedFreeTextByObjectNumberWithUndo(long objectId) {
        return embeddedOps.deleteEmbeddedFreeTextByObjectNumberWithUndo(objectId);
    }

    public boolean addTextAnnotationFromUiWithUndo(@NonNull Annotation annot) {
        if (annot == null) return false;
        if (host.sidecarSessionOrNull() != null) return sidecarOps.addTextAnnotationFromUi(annot);
        return embeddedOps.addTextAnnotationFromUiWithUndo(annot);
    }

    public boolean commitSidecarNoteBounds(@NonNull String noteId, @NonNull RectF boundsDoc) {
        return commitSidecarNoteBounds(noteId, boundsDoc, false);
    }

    public boolean commitSidecarNoteBounds(@NonNull String noteId, @NonNull RectF boundsDoc, boolean markUserResized) {
        return sidecarOps.commitSidecarNoteBounds(noteId, boundsDoc, markUserResized);
    }

    public boolean updateSelectedSidecarNoteText(@Nullable String text) {
        return sidecarOps.updateSelectedSidecarNoteText(text);
    }

    public boolean updateSidecarNoteTextById(@NonNull String noteId, @Nullable String text) {
        return sidecarOps.updateSidecarNoteTextById(noteId, text);
    }

    /** Applies the requested style (font size + palette color) to the selected text annotation. */
    public boolean applyTextStyleToSelectedTextAnnotation(float fontSize, int colorIndex) {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.applyTextStyleToSelectedTextAnnotation(fontSize, colorIndex);
        return embeddedOps.applyTextStyleToSelectedTextAnnotation(fontSize, colorIndex);
    }

    /** Applies background fill + opacity to the selected text annotation. */
    public boolean applyTextBackgroundToSelectedTextAnnotation(int colorIndex, float opacity) {
        opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        if (host.sidecarSessionOrNull() != null) return sidecarOps.applyTextBackgroundToSelectedTextAnnotation(colorIndex, opacity);
        return embeddedOps.applyTextBackgroundToSelectedTextAnnotation(colorIndex, opacity);
    }

    /** Applies border settings (color/width/dash/radius) to the selected text annotation. */
    public boolean applyTextBorderToSelectedTextAnnotation(int colorIndex,
                                                           float widthPt,
                                                           boolean dashed,
                                                           float radiusPt) {
        widthPt = Math.max(0.0f, Math.min(24.0f, widthPt));
        radiusPt = Math.max(0.0f, Math.min(48.0f, radiusPt));
        if (host.sidecarSessionOrNull() != null) return sidecarOps.applyTextBorderToSelectedTextAnnotation(colorIndex, widthPt, dashed, radiusPt);
        return embeddedOps.applyTextBorderToSelectedTextAnnotation(colorIndex, widthPt, dashed, radiusPt);
    }

    /** Applies lock flags (position/size + contents) to the selected text annotation. */
    public boolean applyTextLocksToSelectedTextAnnotation(boolean lockPositionSize, boolean lockContents) {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.applyTextLocksToSelectedTextAnnotation(lockPositionSize, lockContents);
        return embeddedOps.applyTextLocksToSelectedTextAnnotation(lockPositionSize, lockContents);
    }

    /** Returns whether the selected text annotation has "lock position/size" enabled. */
    public boolean selectedTextAnnotationLockPositionSizeOrDefault() {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.selectedTextAnnotationLockPositionSizeOrDefault();
        return embeddedOps.selectedTextAnnotationLockPositionSizeOrDefault();
    }

    /** Returns whether the selected text annotation has "lock contents" enabled. */
    public boolean selectedTextAnnotationLockContentsOrDefault() {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.selectedTextAnnotationLockContentsOrDefault();
        return embeddedOps.selectedTextAnnotationLockContentsOrDefault();
    }

    /** Returns the current justification (0=left, 1=center, 2=right) for the selected FreeText box. */
    public int selectedTextAnnotationAlignmentOrDefault() {
        if (host.sidecarSessionOrNull() != null) return 0;
        return embeddedOps.selectedTextAnnotationAlignmentOrDefault();
    }

    /** Returns the current font size (pt) for the selected FreeText box, or {@code fallbackPt}. */
    public float selectedTextAnnotationFontSizeOrDefault(float fallbackPt) {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.selectedTextAnnotationFontSizeOrDefault(fallbackPt);
        return embeddedOps.selectedTextAnnotationFontSizeOrDefault(fallbackPt);
    }

    /** Returns the current font family (0=sans, 1=serif, 2=mono) for the selected text annotation. */
    public int selectedTextAnnotationFontFamilyOrDefault(int fallbackFamily) {
        fallbackFamily = TextFontFamily.normalize(fallbackFamily);
        if (host.sidecarSessionOrNull() != null) return sidecarOps.selectedTextAnnotationFontFamilyOrDefault(fallbackFamily);
        return embeddedOps.selectedTextAnnotationFontFamilyOrDefault(fallbackFamily);
    }

    /** Returns style flags (bold/italic/underline/strike) for the selected text annotation. */
    public int selectedTextAnnotationStyleFlagsOrDefault(int fallbackFlags) {
        fallbackFlags = TextStyleFlags.normalize(fallbackFlags);
        if (host.sidecarSessionOrNull() != null) return sidecarOps.selectedTextAnnotationStyleFlagsOrDefault(fallbackFlags);
        return embeddedOps.selectedTextAnnotationStyleFlagsOrDefault(fallbackFlags);
    }

    /** Returns line height multiplier (CSS-like {@code line-height}) for the selected text annotation. */
    public float selectedTextAnnotationLineHeightOrDefault(float fallback) {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.selectedTextAnnotationLineHeightOrDefault(fallback);
        return embeddedOps.selectedTextAnnotationLineHeightOrDefault(fallback);
    }

    /** Returns first-line indent (pt) (CSS-like {@code text-indent}) for the selected text annotation. */
    public float selectedTextAnnotationTextIndentPtOrDefault(float fallback) {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.selectedTextAnnotationTextIndentPtOrDefault(fallback);
        return embeddedOps.selectedTextAnnotationTextIndentPtOrDefault(fallback);
    }

    /** Returns the current rotation (degrees, 0/90/180/270) for the selected text annotation. */
    public int selectedTextAnnotationRotationDegOrDefault() {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.selectedTextAnnotationRotationDegOrDefault();
        return embeddedOps.selectedTextAnnotationRotationDegOrDefault();
    }

    /** Applies font family (0=sans, 1=serif, 2=mono) to the selected text annotation. */
    public boolean applyTextFontFamilyToSelectedTextAnnotation(int fontFamily) {
        fontFamily = TextFontFamily.normalize(fontFamily);
        if (host.sidecarSessionOrNull() != null) return sidecarOps.applyTextFontFamilyToSelectedTextAnnotation(fontFamily);
        return embeddedOps.applyTextFontFamilyToSelectedTextAnnotation(fontFamily);
    }

    /** Applies style flags (bold/italic/underline/strike) to the selected text annotation. */
    public boolean applyTextStyleFlagsToSelectedTextAnnotation(int styleFlags) {
        styleFlags = TextStyleFlags.normalize(styleFlags);
        if (host.sidecarSessionOrNull() != null) return sidecarOps.applyTextStyleFlagsToSelectedTextAnnotation(styleFlags);
        return embeddedOps.applyTextStyleFlagsToSelectedTextAnnotation(styleFlags);
    }

    /** Applies paragraph settings (line-height multiplier + text-indent in pt) to the selected text annotation. */
    public boolean applyTextParagraphToSelectedTextAnnotation(float lineHeight, float textIndentPt) {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.applyTextParagraphToSelectedTextAnnotation(lineHeight, textIndentPt);
        return embeddedOps.applyTextParagraphToSelectedTextAnnotation(lineHeight, textIndentPt);
    }

    /** Applies justification (0=left, 1=center, 2=right) to the selected FreeText box. */
    public boolean applyTextAlignmentToSelectedTextAnnotation(int alignment) {
        if (host.sidecarSessionOrNull() != null) return false;
        alignment = Math.max(0, Math.min(2, alignment));
        return embeddedOps.applyTextAlignmentToSelectedTextAnnotation(alignment);
    }

    /** Applies rotation (degrees, 0/90/180/270) to the selected text annotation. */
    public boolean applyTextRotationToSelectedTextAnnotation(int rotationDegrees) {
        // Normalize to [0..359], then snap to 90deg increments to keep UI/geometry expectations stable.
        if (rotationDegrees < 0 || rotationDegrees >= 360) {
            rotationDegrees %= 360;
            if (rotationDegrees < 0) rotationDegrees += 360;
        }
        int snapped = ((rotationDegrees + 45) / 90) * 90;
        if (snapped >= 360) snapped = 0;
        rotationDegrees = snapped;

        if (host.sidecarSessionOrNull() != null) return sidecarOps.applyTextRotationToSelectedTextAnnotation(rotationDegrees);
        return embeddedOps.applyTextRotationToSelectedTextAnnotation(rotationDegrees);
    }

    /** Tightens the selected FreeText bounds to its current content (Acrobat-ish). */
    public boolean fitSelectedTextAnnotationToText() {
        if (host.sidecarSessionOrNull() != null) return false;
        return embeddedOps.fitSelectedTextAnnotationToText();
    }

    /**
     * Creates a duplicate of the currently selected text annotation (embedded FreeText or sidecar note).
     */
    public boolean duplicateSelectedTextAnnotation() {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.duplicateSelectedTextAnnotation();
        return embeddedOps.duplicateSelectedTextAnnotation();
    }

    /** Copies the currently selected text annotation (FreeText or sidecar note) to the app clipboard. */
    public boolean copySelectedTextAnnotationToClipboard() {
        if (host.sidecarSessionOrNull() != null) return sidecarOps.copySelectedTextAnnotationToClipboard();
        return embeddedOps.copySelectedTextAnnotationToClipboard();
    }

    /** Pastes the current clipboard payload as a new text annotation on the current page. */
    public boolean pasteTextAnnotationFromClipboard() {
        final TextAnnotationClipboard.Payload payload = TextAnnotationClipboard.get();
        if (payload == null) return false;
        if (host.sidecarSessionOrNull() != null) return sidecarOps.pasteFromClipboard(payload);
        return embeddedOps.pasteFromClipboard(payload);
    }

    public void updateTextAnnotationContentsByObjectNumber(long objectNumber, @Nullable String text) {
        embeddedOps.updateTextAnnotationContentsByObjectNumber(objectNumber, text);
    }

    /** True if the embedded FreeText object has the "locked contents" flag set. */
    public boolean embeddedFreeTextContentsLocked(long objectId) {
        return embeddedOps.embeddedFreeTextContentsLocked(objectId);
    }

    /** True if the embedded FreeText object has the "locked (position/size)" flag set. */
    public boolean embeddedFreeTextPositionLocked(long objectId) {
        return embeddedOps.embeddedFreeTextPositionLocked(objectId);
    }

    /** Exposed for controllers to check lock state before batch geometry operations. */
    public boolean isFreeTextPositionLocked(long objectId) {
        return embeddedOps.isFreeTextPositionLocked(objectId);
    }

    @Nullable
    static RectF offsetAndClampDocBounds(@NonNull Resources res,
                                         float scale,
                                         float docW,
                                         float docH,
                                         @NonNull RectF boundsDoc) {
        if (scale <= 0f) return null;

        float density = res.getDisplayMetrics().density;
        float offsetDoc = (16f * density) / scale;

        RectF r = new RectF(boundsDoc);
        r.offset(offsetDoc, offsetDoc);

        // Clamp to document bounds.
        float w = r.width();
        float h = r.height();
        if (w > docW) w = docW;
        if (h > docH) h = docH;

        if (r.left < 0f) r.offset(-r.left, 0f);
        if (r.top < 0f) r.offset(0f, -r.top);
        if (r.right > docW) r.offset(docW - r.right, 0f);
        if (r.bottom > docH) r.offset(0f, docH - r.bottom);

        r.left = Math.max(0f, Math.min(docW - w, r.left));
        r.top = Math.max(0f, Math.min(docH - h, r.top));
        r.right = Math.min(docW, r.left + w);
        r.bottom = Math.min(docH, r.top + h);

        // Enforce a minimum edge so selection handles remain usable.
        float minEdgeDoc = ItemSelectionHandles.minEdgePx(res) / scale;
        if (r.width() < minEdgeDoc) r.right = Math.min(docW, r.left + minEdgeDoc);
        if (r.height() < minEdgeDoc) r.bottom = Math.min(docH, r.top + minEdgeDoc);

        return r;
    }

    @Nullable
    static RectF offsetAndClampDocBoundsWithSteps(@NonNull Resources res,
                                                  float scale,
                                                  float docW,
                                                  float docH,
                                                  @NonNull RectF boundsDoc,
                                                  int offsetSteps) {
        if (scale <= 0f) return null;

        float density = res.getDisplayMetrics().density;
        float step = Math.max(0, offsetSteps);
        float offsetDoc = (16f * density * step) / scale;

        RectF r = new RectF(boundsDoc);
        r.offset(offsetDoc, offsetDoc);

        // Clamp to document bounds.
        float w = r.width();
        float h = r.height();
        if (w > docW) w = docW;
        if (h > docH) h = docH;

        if (r.left < 0f) r.offset(-r.left, 0f);
        if (r.top < 0f) r.offset(0f, -r.top);
        if (r.right > docW) r.offset(docW - r.right, 0f);
        if (r.bottom > docH) r.offset(0f, docH - r.bottom);

        r.left = Math.max(0f, Math.min(docW - w, r.left));
        r.top = Math.max(0f, Math.min(docH - h, r.top));
        r.right = Math.min(docW, r.left + w);
        r.bottom = Math.min(docH, r.top + h);

        // Enforce a minimum edge so selection handles remain usable.
        float minEdgeDoc = ItemSelectionHandles.minEdgePx(res) / scale;
        if (r.width() < minEdgeDoc) r.right = Math.min(docW, r.left + minEdgeDoc);
        if (r.height() < minEdgeDoc) r.bottom = Math.min(docH, r.top + minEdgeDoc);

        return r;
    }

    static void copyPlainTextToSystemClipboard(@NonNull Context context, @NonNull String text) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;
                cm.setPrimaryClip(ClipData.newPlainText(context.getPackageName(), text));
            } else {
                @SuppressWarnings("deprecation")
                android.text.ClipboardManager cm =
                        (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm == null) return;
                cm.setText(text);
            }
        } catch (Throwable ignore) {
        }
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
