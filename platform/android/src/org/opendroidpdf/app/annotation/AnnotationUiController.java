package org.opendroidpdf.app.annotation;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.core.AnnotationController;
import org.opendroidpdf.app.selection.TextSelectionActions;
import org.opendroidpdf.app.preferences.EditorPreferences;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;

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
        void setDraw(PointF[][] arcs);
        void setModeDrawing();
        void deleteSelectedAnnotation();
    }

    private final AnnotationActions annotationActions;
    private final AnnotationEditController annotationEditController;
    private final TextSelectionActions textSelectionActions;
    @Nullable private final SidecarAnnotationSession sidecarSession;

    public AnnotationUiController(AnnotationController annotationController) {
        this(annotationController, null);
    }

    public AnnotationUiController(AnnotationController annotationController,
                                  @Nullable SidecarAnnotationSession sidecarSession) {
        this.annotationActions = new AnnotationActions(annotationController);
        this.annotationEditController = new AnnotationEditController();
        this.textSelectionActions = new TextSelectionActions();
        this.sidecarSession = sidecarSession;
    }

    public boolean copySelection(Host host) {
        return textSelectionActions.copySelection(new TextSelectionActions.Host() {
            @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
            @Override public void deselectText() { host.deselectText(); }
            @Override public Context getContext() { return host.getContext(); }
        });
    }

    public boolean markupSelection(Host host, Annotation.Type type, int pageNumber, Runnable reloadAnnotations) {
        SidecarAnnotationSession sidecar = sidecarSession;
        return textSelectionActions.markupSelection(
                new TextSelectionActions.Host() {
                    @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
                    @Override public void deselectText() { host.deselectText(); }
                    @Override public Context getContext() { return host.getContext(); }
                },
                type,
                (quadArray, t, onComplete) -> {
                    if (sidecar != null) {
                        EditorPreferences prefs = new EditorPreferences(host.getContext());
                        int color;
                        float opacity;
                        switch (t) {
                            case HIGHLIGHT:
                                color = prefs.getHighlightColorHex();
                                opacity = 0.35f;
                                break;
                            case UNDERLINE:
                                color = prefs.getUnderlineColorHex();
                                opacity = 1.0f;
                                break;
                            case STRIKEOUT:
                                color = prefs.getStrikeoutColorHex();
                                opacity = 1.0f;
                                break;
                            default:
                                color = prefs.getHighlightColorHex();
                                opacity = 0.35f;
                                break;
                        }
                        sidecar.addHighlight(pageNumber, t, quadArray, color, opacity, System.currentTimeMillis());
                        if (reloadAnnotations != null) reloadAnnotations.run();
                        if (onComplete != null) onComplete.run();
                    } else {
                        annotationActions.addMarkupAnnotation(pageNumber, quadArray, t, () -> { reloadAnnotations.run(); onComplete.run(); });
                    }
                }
        );
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
}
