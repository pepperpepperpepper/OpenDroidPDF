package org.opendroidpdf;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;

/**
 * Hosts selection routing callbacks so MuPDFPageView stays lean.
 * Lives in the core package to access package-private/protected members.
 */
class SelectionUiBridge {

    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager;
    private final SelectionActionRouter.Host selectionRouterHost;
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager.Host selectionBoxHost;

    SelectionUiBridge(MuPDFPageView pageView, MuPDFReaderView readerView,
                      org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager) {
        this.selectionManager = selectionManager;
        this.selectionBoxHost = rect -> pageView.setItemSelectBox(rect);
        this.selectionRouterHost = new SelectionActionRouter.Host() {
            @Override public Annotation[] annotations() { return pageView.mAnnotations; }
            @Override public int pageNumber() { return pageView.mPageNumber; }
            @Override public org.opendroidpdf.app.annotation.AnnotationSelectionManager.Host selectionHost() { return selectionBoxHost; }
            @Override public void requestFullRedrawAfterNextAnnotationLoad() { pageView.requestFullRedrawAfterNextAnnotationLoad(); }
            @Override public void loadAnnotations() { pageView.loadAnnotations(); }
            @Override public void discardRenderedPage() { pageView.discardRenderedPage(); }
            @Override public void redraw(boolean updateHq) { pageView.redraw(updateHq); }
            @Override public void setModeDrawing() {
                // PageViews can be constructed before being attached to the ReaderView.
                // Resolve the parent at call time to avoid NPEs during edit flows.
                MuPDFReaderView rv = pageView.mParent instanceof MuPDFReaderView ? (MuPDFReaderView) pageView.mParent : null;
                if (rv != null) rv.requestMode(MuPDFReaderView.Mode.Drawing);
            }
            @Override public void processSelectedText(TextProcessor processor) { pageView.processSelectedText(processor); }
            @Override public void deselectText() { pageView.deselectText(); }
            @Override public void setDraw(PointF[][] arcs) { pageView.setDraw(arcs); }
            @Override public Context getContext() { return pageView.getContext(); }
        };
    }

    org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager() { return selectionManager; }
    SelectionActionRouter.Host selectionRouterHost() { return selectionRouterHost; }
    org.opendroidpdf.app.annotation.AnnotationSelectionManager.Host selectionBoxHost() { return selectionBoxHost; }
}
