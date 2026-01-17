package org.opendroidpdf.app.selection;

import android.content.Context;
import android.graphics.PointF;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.app.annotation.AnnotationSelectionManager;
import org.opendroidpdf.app.annotation.AnnotationUiController;

/**
 * Delegates selection-driven actions (copy, markup, edit, delete) away from MuPDFPageView
 * to keep the view focused on rendering.
 */
public final class SelectionActionRouter {
    public interface Host {
        Annotation[] annotations();
        int pageNumber();
        int pageCount();
        long reflowLocation();
        AnnotationSelectionManager.Host selectionHost();

        // page/reader hooks
        void requestFullRedrawAfterNextAnnotationLoad();
        void loadAnnotations();
        void discardRenderedPage();
        void redraw(boolean updateHq);
        void setModeDrawing();
        void refreshUndoState();

        TextWord[][] textLines();

        // text/selection helpers
        void processSelectedText(TextProcessor processor);
        void deselectText();
        void setDraw(PointF[][] arcs);
        Context getContext();

        boolean addEmbeddedMarkupAnnotationWithUndo(int pageNumber, PointF[] quadPoints, Annotation.Type type, Runnable onComplete);
    }

    private final AnnotationSelectionManager selectionManager;
    private final AnnotationUiController annotationUiController;
    private final Host host;

    public SelectionActionRouter(AnnotationSelectionManager selectionManager,
                                 AnnotationUiController annotationUiController,
                                 Host host) {
        this.selectionManager = selectionManager;
        this.annotationUiController = annotationUiController;
        this.host = host;
    }

    public boolean copySelection() {
        return annotationUiController.copySelection(new AnnotationUiController.Host() {
            @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
            @Override public void deselectText() { host.deselectText(); }
            @Override public Context getContext() { return host.getContext(); }
            @Override public TextWord[][] textLines() { return host.textLines(); }
            @Override public void setDraw(PointF[][] arcs) { host.setDraw(arcs); }
            @Override public void setModeDrawing() { host.setModeDrawing(); }
            @Override public void deleteSelectedAnnotation() { SelectionActionRouter.this.deleteSelectedAnnotation(); }
            @Override public boolean addEmbeddedMarkupAnnotationWithUndo(int pageNumber, PointF[] quadPoints, Annotation.Type type, Runnable onComplete) {
                return host.addEmbeddedMarkupAnnotationWithUndo(pageNumber, quadPoints, type, onComplete);
            }
        });
    }

    public boolean markupSelection(final Annotation.Type type) {
        return annotationUiController.markupSelection(
                new AnnotationUiController.Host() {
                    @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
                    @Override public void deselectText() { host.deselectText(); }
                    @Override public Context getContext() { return host.getContext(); }
                    @Override public TextWord[][] textLines() { return host.textLines(); }
                    @Override public void setDraw(PointF[][] arcs) { host.setDraw(arcs); }
                    @Override public void setModeDrawing() { host.setModeDrawing(); }
                    @Override public void deleteSelectedAnnotation() { SelectionActionRouter.this.deleteSelectedAnnotation(); }
                    @Override public boolean addEmbeddedMarkupAnnotationWithUndo(int pageNumber, PointF[] quadPoints, Annotation.Type t, Runnable onComplete) {
                        return host.addEmbeddedMarkupAnnotationWithUndo(pageNumber, quadPoints, t, onComplete);
                    }
                },
                type,
                host.pageNumber(),
                host.pageCount(),
                host.reflowLocation(),
                () -> {
                    host.loadAnnotations();
                    host.refreshUndoState();
                }
        );
    }

    public boolean replaceSelection() {
        return annotationUiController.replaceSelection(
                new AnnotationUiController.Host() {
                    @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
                    @Override public void deselectText() { host.deselectText(); }
                    @Override public Context getContext() { return host.getContext(); }
                    @Override public TextWord[][] textLines() { return host.textLines(); }
                    @Override public void setDraw(PointF[][] arcs) { host.setDraw(arcs); }
                    @Override public void setModeDrawing() { host.setModeDrawing(); }
                    @Override public void deleteSelectedAnnotation() { SelectionActionRouter.this.deleteSelectedAnnotation(); }
                    @Override public boolean addEmbeddedMarkupAnnotationWithUndo(int pageNumber, PointF[] quadPoints, Annotation.Type type, Runnable onComplete) {
                        return host.addEmbeddedMarkupAnnotationWithUndo(pageNumber, quadPoints, type, onComplete);
                    }
                },
                host.pageNumber(),
                host.pageCount(),
                host.reflowLocation(),
                () -> {
                    host.loadAnnotations();
                    host.refreshUndoState();
                }
        );
    }

    public void deleteSelectedAnnotation() {
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

    public void editSelectedAnnotation() {
        if (!selectionManager.hasSelection()) return;
        Annotation[] annotations = host.annotations();
        if (annotations == null) return;
        final Annotation annot = annotations[selectionManager.selectedIndex()];
        annotationUiController.editAnnotation(annot, new AnnotationUiController.Host() {
            @Override public Context getContext() { return host.getContext(); }
            @Override public void processSelectedText(TextProcessor processor) { host.processSelectedText(processor); }
            @Override public void deselectText() { host.deselectText(); }
            @Override public TextWord[][] textLines() { return host.textLines(); }
            @Override public void setDraw(PointF[][] arcs) { host.setDraw(arcs); }
            @Override public void setModeDrawing() { host.setModeDrawing(); }
            @Override public void deleteSelectedAnnotation() { SelectionActionRouter.this.deleteSelectedAnnotation(); }
            @Override public boolean addEmbeddedMarkupAnnotationWithUndo(int pageNumber, PointF[] quadPoints, Annotation.Type type, Runnable onComplete) {
                return host.addEmbeddedMarkupAnnotationWithUndo(pageNumber, quadPoints, type, onComplete);
            }
        });
    }

    public Annotation.Type selectedAnnotationType() {
        return selectionManager.selectedType(host.annotations());
    }

    public boolean selectedAnnotationIsEditable() {
        return selectionManager.isEditable(host.annotations());
    }

    public void deselectAnnotation() {
        selectionManager.deselect(host.selectionHost());
    }
}
