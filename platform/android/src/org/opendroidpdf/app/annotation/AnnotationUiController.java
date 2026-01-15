package org.opendroidpdf.app.annotation;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.core.AnnotationController;
import org.opendroidpdf.app.selection.TextSelectionActions;
import org.opendroidpdf.app.preferences.EditorPreferences;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.ColorPalette;

import androidx.annotation.Nullable;

/**
 * UI-facing annotation orchestration: selection actions, dialogs, and markup/text creation.
 * Keeps MuPDFPageView slimmer by housing annotation-side effects here.
 */
public class AnnotationUiController {
    public interface Host {
        Context getContext();
        void processSelectedText(TextProcessor processor);
        void deselectText();
        @Nullable TextWord[][] textLines();
        void setDraw(PointF[][] arcs);
        void setModeDrawing();
        void deleteSelectedAnnotation();
    }

    private final AnnotationActions annotationActions;
    private final AnnotationEditController annotationEditController;
    private final TextSelectionActions textSelectionActions;
    @Nullable private final SidecarAnnotationSession sidecarSession;
    @Nullable private final EditorPreferences editorPreferences;

    public AnnotationUiController(AnnotationController annotationController) {
        this(annotationController, null, null);
    }

    public AnnotationUiController(AnnotationController annotationController,
                                  @Nullable SidecarAnnotationSession sidecarSession,
                                  @Nullable EditorPreferences editorPreferences) {
        this.annotationActions = new AnnotationActions(annotationController);
        this.annotationEditController = new AnnotationEditController();
        this.textSelectionActions = new TextSelectionActions();
        this.sidecarSession = sidecarSession;
        this.editorPreferences = editorPreferences;
    }

    public boolean copySelection(Host host) {
        return textSelectionActions.copySelection(new TextSelectionActions.Host() {
            @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
            @Override public void deselectText() { host.deselectText(); }
            @Override public Context getContext() { return host.getContext(); }
        });
    }

    public boolean markupSelection(Host host,
                                   Annotation.Type type,
                                   int pageNumber,
                                   int pageCount,
                                   long reflowLocation,
                                   Runnable reloadAnnotations) {
        final TextWord[][] textLines = host.textLines();
        return textSelectionActions.markupSelection(
                new TextSelectionActions.Host() {
                    @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
                    @Override public void deselectText() { host.deselectText(); }
                    @Override public Context getContext() { return host.getContext(); }
                },
                type,
                (quadArray, t, selectedText, onComplete) ->
                        addMarkup(pageNumber, pageCount, reflowLocation, reloadAnnotations, textLines, quadArray, t, selectedText, onComplete)
        );
    }

    /**
     * Proofreading replace workflow: strikeout the selected text and place a caret
     * after the selection to mark the insertion point.
     */
    public boolean replaceSelection(Host host,
                                    int pageNumber,
                                    int pageCount,
                                    long reflowLocation,
                                    Runnable reloadAnnotations) {
        final TextWord[][] textLines = host.textLines();
        return textSelectionActions.markupSelection(
                new TextSelectionActions.Host() {
                    @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
                    @Override public void deselectText() { host.deselectText(); }
                    @Override public Context getContext() { return host.getContext(); }
                },
                Annotation.Type.STRIKEOUT,
                (quadArray, t, selectedText, onComplete) -> {
                    PointF[] caretQuads = caretFromQuads(quadArray);
                    Runnable finish = () -> { if (onComplete != null) onComplete.run(); };
                    Runnable addCaret = caretQuads != null
                            ? () -> addMarkup(pageNumber, pageCount, reflowLocation, reloadAnnotations, textLines, caretQuads, Annotation.Type.CARET, selectedText, finish)
                            : finish;
                    addMarkup(pageNumber, pageCount, reflowLocation, reloadAnnotations, textLines, quadArray, Annotation.Type.STRIKEOUT, selectedText, addCaret);
                }
        );
    }

    private void addMarkup(int pageNumber,
                           int pageCount,
                           long reflowLocation,
                           Runnable reloadAnnotations,
                           @Nullable TextWord[][] textLines,
                           PointF[] quadArray,
                           Annotation.Type type,
                           String selectedText,
                           Runnable onComplete) {
        SidecarAnnotationSession sidecar = sidecarSession;
        if (sidecar != null) {
            EditorPreferences prefs = editorPreferences;
            int color;
            float opacity;
            switch (type) {
                case HIGHLIGHT:
                    color = prefs != null ? prefs.getHighlightColorHex() : ColorPalette.getHex(0);
                    opacity = 0.35f;
                    break;
                case UNDERLINE:
                case SQUIGGLY:
                    color = prefs != null ? prefs.getUnderlineColorHex() : ColorPalette.getHex(0);
                    opacity = 1.0f;
                    break;
                case CARET:
                    color = prefs != null ? prefs.getUnderlineColorHex() : ColorPalette.getHex(0);
                    opacity = 1.0f;
                    break;
                case STRIKEOUT:
                    color = prefs != null ? prefs.getStrikeoutColorHex() : ColorPalette.getHex(0);
                    opacity = 1.0f;
                    break;
                default:
                    color = prefs != null ? prefs.getHighlightColorHex() : ColorPalette.getHex(0);
                    opacity = 0.35f;
                    break;
            }
            float docProgress01 = pageCount > 0 ? ((pageNumber + 0.5f) / (float) pageCount) : -1f;
            String quote = normalizeSelectedTextAnchor(selectedText);
            sidecar.addHighlight(pageNumber, type, quadArray, color, opacity, System.currentTimeMillis(), reflowLocation, textLines, quote, docProgress01);
            if (reloadAnnotations != null) reloadAnnotations.run();
            if (onComplete != null) onComplete.run();
        } else {
            annotationActions.addMarkupAnnotation(pageNumber, quadArray, type, () -> {
                if (reloadAnnotations != null) reloadAnnotations.run();
                if (onComplete != null) onComplete.run();
            });
        }
    }

    public void addTextAnnotation(int pageNumber, PointF[] quadPoints, String text, Runnable afterAdd) {
        SidecarAnnotationSession sidecar = sidecarSession;
        if (sidecar != null) {
            RectF bounds = boundsFromTwoPoints(quadPoints);
            if (bounds != null) {
                sidecar.addNote(pageNumber, bounds, text, System.currentTimeMillis());
            }
            if (afterAdd != null) afterAdd.run();
            return;
        }
        annotationActions.addTextAnnotation(pageNumber, quadPoints, text, afterAdd);
    }

    public void updateTextAnnotationContentsByObjectNumber(int pageNumber, long objectNumber, String text, Runnable afterUpdate) {
        SidecarAnnotationSession sidecar = sidecarSession;
        if (sidecar != null) {
            // Sidecar notes use stable noteIds, not embedded PDF object numbers. Editing is handled
            // by the sidecar selection controller.
            if (afterUpdate != null) afterUpdate.run();
            return;
        }
        annotationActions.updateTextAnnotationContentsByObjectNumber(pageNumber, objectNumber, text, afterUpdate);
    }

    public void deleteAnnotation(int pageNumber, int annotationIndex, Runnable afterDelete) {
        annotationActions.deleteAnnotation(pageNumber, annotationIndex, afterDelete);
    }

    public void editAnnotation(Annotation annot, Host host) {
        annotationEditController.editIfSupported(annot, new AnnotationEditController.Host() {
            @Override public void setDraw(PointF[][] arcs) { host.setDraw(arcs); }
            @Override public void setModeDrawing() { host.setModeDrawing(); }
            @Override public void deleteSelectedAnnotation() { host.deleteSelectedAnnotation(); }
        });
    }

    public void release() {
        annotationActions.release();
    }

    @Nullable
    private static PointF[] caretFromQuads(@Nullable PointF[] quadArray) {
        if (quadArray == null || quadArray.length < 4) return null;
        // Use the last quad (end of the selection) to position the caret.
        int start = quadArray.length - 4;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = start; i < quadArray.length; i++) {
            PointF p = quadArray[i];
            if (p == null) continue;
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        if (minX == Float.MAX_VALUE || minY == Float.MAX_VALUE) return null;
        float lineHeight = Math.max(maxY - minY, 10f);
        float caretWidth = Math.max(lineHeight * 0.35f, 6f);
        float gap = Math.min(caretWidth, 6f);
        float x0 = maxX + gap * 0.5f;
        float x1 = x0 + caretWidth;
        float y0 = minY;
        float y1 = maxY;
        return new PointF[] {
                new PointF(x0, y1),
                new PointF(x1, y1),
                new PointF(x1, y0),
                new PointF(x0, y0)
        };
    }

    @Nullable
    private static RectF boundsFromTwoPoints(@Nullable PointF[] twoPoints) {
        if (twoPoints == null || twoPoints.length < 2) return null;
        PointF a = twoPoints[0];
        PointF b = twoPoints[1];
        if (a == null || b == null) return null;
        float left = Math.min(a.x, b.x);
        float right = Math.max(a.x, b.x);
        float top = Math.max(a.y, b.y);
        float bottom = Math.min(a.y, b.y);
        return new RectF(left, bottom, right, top);
    }

    @Nullable
    private static String normalizeSelectedTextAnchor(@Nullable String selectedText) {
        if (selectedText == null) return null;
        String normalized = selectedText.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return null;
        // Keep anchors bounded to avoid pathological searches.
        final int max = 512;
        if (normalized.length() > max) normalized = normalized.substring(0, max);
        return normalized;
    }
}
