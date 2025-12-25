package org.opendroidpdf.app.selection;

import android.content.Context;
import android.graphics.PointF;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.TextProcessor;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.app.annotation.AnnotationSelectionManager;

/**
 * Hosts selection routing callbacks so MuPDFPageView stays lean.
 * Depends only on {@link SelectionPageModel} to avoid leaking PageView internals.
 */
public final class SelectionUiBridge {

    private final AnnotationSelectionManager selectionManager;
    private final SelectionActionRouter.Host selectionRouterHost;
    private final AnnotationSelectionManager.Host selectionBoxHost;

    public SelectionUiBridge(SelectionPageModel pageModel, AnnotationSelectionManager selectionManager) {
        this.selectionManager = selectionManager;
        this.selectionBoxHost = rect -> pageModel.setSelectionBox(rect);
        this.selectionRouterHost = new SelectionActionRouter.Host() {
            @Override public Annotation[] annotations() { return pageModel.annotations(); }
            @Override public int pageNumber() { return pageModel.pageNumber(); }
            @Override public int pageCount() { return pageModel.pageCount(); }
            @Override public long reflowLocation() { return pageModel.reflowLocation(); }
            @Override public AnnotationSelectionManager.Host selectionHost() { return selectionBoxHost; }
            @Override public void requestFullRedrawAfterNextAnnotationLoad() { pageModel.requestFullRedrawAfterNextAnnotationLoad(); }
            @Override public void loadAnnotations() { pageModel.loadAnnotations(); }
            @Override public void discardRenderedPage() { pageModel.discardRenderedPage(); }
            @Override public void redraw(boolean updateHq) { pageModel.redraw(updateHq); }
            @Override public void setModeDrawing() { pageModel.setModeDrawing(); }
            @Override public void refreshUndoState() { pageModel.refreshUndoState(); }
            @Override public TextWord[][] textLines() { return pageModel.textLines(); }
            @Override public void processSelectedText(TextProcessor processor) { pageModel.processSelectedText(processor); }
            @Override public void deselectText() { pageModel.deselectText(); }
            @Override public void setDraw(PointF[][] arcs) { pageModel.setDraw(arcs); }
            @Override public Context getContext() { return pageModel.getContext(); }
        };
    }

    public AnnotationSelectionManager selectionManager() { return selectionManager; }
    public SelectionActionRouter.Host selectionRouterHost() { return selectionRouterHost; }
    public AnnotationSelectionManager.Host selectionBoxHost() { return selectionBoxHost; }
}
