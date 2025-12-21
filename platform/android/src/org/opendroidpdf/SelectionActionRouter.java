package org.opendroidpdf;

import android.content.Context;
import android.graphics.PointF;

import org.opendroidpdf.app.annotation.AnnotationSelectionManager;
import org.opendroidpdf.app.annotation.AnnotationUiController;

/**
 * Delegates selection-driven actions (copy, markup, edit, delete) away from MuPDFPageView
 * to keep the view focused on rendering.
 */
class SelectionActionRouter {
    interface Host {
        Annotation[] annotations();
        int pageNumber();
        int pageCount();
        AnnotationSelectionManager.Host selectionHost();

        // page/reader hooks
        void requestFullRedrawAfterNextAnnotationLoad();
        void loadAnnotations();
        void discardRenderedPage();
        void redraw(boolean updateHq);
        void setModeDrawing();
        void refreshUndoState();

        // text/selection helpers
        void processSelectedText(TextProcessor processor);
        void deselectText();
        void setDraw(PointF[][] arcs);
        Context getContext();
    }

    private final AnnotationSelectionManager selectionManager;
    private final AnnotationUiController annotationUiController;
    private final Host host;

    SelectionActionRouter(AnnotationSelectionManager selectionManager,
                          AnnotationUiController annotationUiController,
                          Host host) {
        this.selectionManager = selectionManager;
        this.annotationUiController = annotationUiController;
        this.host = host;
    }

    boolean copySelection() {
        return annotationUiController.copySelection(new AnnotationUiController.Host() {
            @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
            @Override public void deselectText() { host.deselectText(); }
            @Override public Context getContext() { return host.getContext(); }
            @Override public void setDraw(PointF[][] arcs) { host.setDraw(arcs); }
            @Override public void setModeDrawing() { host.setModeDrawing(); }
            @Override public void deleteSelectedAnnotation() { SelectionActionRouter.this.deleteSelectedAnnotation(); }
        });
    }

    boolean markupSelection(final Annotation.Type type) {
        return annotationUiController.markupSelection(
                new AnnotationUiController.Host() {
                    @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
                    @Override public void deselectText() { host.deselectText(); }
                    @Override public Context getContext() { return host.getContext(); }
                    @Override public void setDraw(PointF[][] arcs) { host.setDraw(arcs); }
                    @Override public void setModeDrawing() { host.setModeDrawing(); }
                    @Override public void deleteSelectedAnnotation() { SelectionActionRouter.this.deleteSelectedAnnotation(); }
                },
                type,
                host.pageNumber(),
                host.pageCount(),
                () -> {
                    host.loadAnnotations();
                    host.refreshUndoState();
                }
        );
    }

    void deleteSelectedAnnotation() {
        if (!selectionManager.hasSelection()) return;
        final int targetIndex = selectionManager.selectedIndex();
        annotationUiController.deleteAnnotation(
                host.pageNumber(),
                targetIndex,
                () -> {
                    host.requestFullRedrawAfterNextAnnotationLoad();
                    host.loadAnnotations();
                    host.discardRenderedPage();
                    host.redraw(false);
                }
        );
        deselectAnnotation();
    }

    void editSelectedAnnotation() {
        if (!selectionManager.hasSelection()) return;
        Annotation[] annotations = host.annotations();
        if (annotations == null) return;
        final Annotation annot = annotations[selectionManager.selectedIndex()];
        annotationUiController.editAnnotation(annot, new AnnotationUiController.Host() {
            @Override public Context getContext() { return host.getContext(); }
            @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
            @Override public void deselectText() { host.deselectText(); }
            @Override public void setDraw(PointF[][] arcs) { host.setDraw(arcs); }
            @Override public void setModeDrawing() { host.setModeDrawing(); }
            @Override public void deleteSelectedAnnotation() { SelectionActionRouter.this.deleteSelectedAnnotation(); }
        });
    }

    Annotation.Type selectedAnnotationType() {
        return selectionManager.selectedType(host.annotations());
    }

    boolean selectedAnnotationIsEditable() {
        return selectionManager.isEditable(host.annotations());
    }

    void deselectAnnotation() {
        selectionManager.deselect(host.selectionHost());
    }
}
