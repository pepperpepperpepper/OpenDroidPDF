package org.opendroidpdf.app.annotation;

import android.content.Context;
import android.graphics.PointF;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.core.AnnotationController;
import org.opendroidpdf.app.selection.TextSelectionActions;

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

    public AnnotationUiController(AnnotationController annotationController) {
        this.annotationActions = new AnnotationActions(annotationController);
        this.annotationEditController = new AnnotationEditController();
        this.textSelectionActions = new TextSelectionActions();
    }

    public boolean copySelection(Host host) {
        return textSelectionActions.copySelection(new TextSelectionActions.Host() {
            @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
            @Override public void deselectText() { host.deselectText(); }
            @Override public Context getContext() { return host.getContext(); }
        });
    }

    public boolean markupSelection(Host host, Annotation.Type type, int pageNumber, Runnable reloadAnnotations) {
        return textSelectionActions.markupSelection(
                new TextSelectionActions.Host() {
                    @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
                    @Override public void deselectText() { host.deselectText(); }
                    @Override public Context getContext() { return host.getContext(); }
                },
                type,
                (quadArray, t, onComplete) -> annotationActions.addMarkupAnnotation(pageNumber, quadArray, t, () -> { reloadAnnotations.run(); onComplete.run(); })
        );
    }

    public void addTextAnnotation(int pageNumber, PointF[] quadPoints, String text, Runnable afterAdd) {
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
}
