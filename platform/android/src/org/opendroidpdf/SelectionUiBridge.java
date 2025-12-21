package org.opendroidpdf;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

/**
 * Hosts selection routing callbacks so MuPDFPageView stays lean.
 * Depends only on {@link SelectionPageModel} to avoid leaking PageView internals.
 */
class SelectionUiBridge {

    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager;
    private final SelectionActionRouter.Host selectionRouterHost;
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager.Host selectionBoxHost;

    SelectionUiBridge(SelectionPageModel pageModel,
                      org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager) {
        this.selectionManager = selectionManager;
        this.selectionBoxHost = rect -> pageModel.setSelectionBox(rect);
        this.selectionRouterHost = new SelectionActionRouter.Host() {
            @Override public Annotation[] annotations() { return pageModel.annotations(); }
            @Override public int pageNumber() { return pageModel.pageNumber(); }
            @Override public int pageCount() { return pageModel.pageCount(); }
            @Override public org.opendroidpdf.app.annotation.AnnotationSelectionManager.Host selectionHost() { return selectionBoxHost; }
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

    org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager() { return selectionManager; }
    SelectionActionRouter.Host selectionRouterHost() { return selectionRouterHost; }
    org.opendroidpdf.app.annotation.AnnotationSelectionManager.Host selectionBoxHost() { return selectionBoxHost; }
}
