package org.opendroidpdf;

import android.graphics.RectF;

import org.opendroidpdf.app.reader.gesture.AnnotationHitHelper;
import org.opendroidpdf.app.reader.gesture.PageHitRouter;
import org.opendroidpdf.app.selection.SelectionUiBridge;
import org.opendroidpdf.core.WidgetController;

final class MuPDFPageViewHitHost implements PageHitRouter.Host {
    private static final Annotation[] EMPTY_ANNOTATIONS = new Annotation[0];

    private final MuPDFPageView view;
    private final MuPDFPageViewWidgets widgets;
    private final WidgetController widgetController;
    private final AnnotationHitHelper annotationHitHelper;
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager;
    private final SelectionUiBridge selectionUiBridge;

    MuPDFPageViewHitHost(
            MuPDFPageView view,
            MuPDFPageViewWidgets widgets,
            WidgetController widgetController,
            AnnotationHitHelper annotationHitHelper,
            org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager,
            SelectionUiBridge selectionUiBridge) {
        this.view = view;
        this.widgets = widgets;
        this.widgetController = widgetController;
        this.annotationHitHelper = annotationHitHelper;
        this.selectionManager = selectionManager;
        this.selectionUiBridge = selectionUiBridge;
    }

    @Override public float scale() { return view.getScale(); }
    @Override public int viewLeft() { return view.getLeft(); }
    @Override public int viewTop() { return view.getTop(); }
    @Override public int pageNumber() { return view.mPageNumber; }

    @Override public LinkInfo[] links() { return view.mLinks; }
    @Override public Annotation[] annotations() { return view.areCommentsVisible() ? view.mAnnotations : EMPTY_ANNOTATIONS; }
    @Override public RectF[] widgetAreas() { return widgets.widgetAreas(); }

    @Override public AnnotationHitHelper annotationHitHelper() { return annotationHitHelper; }
    @Override public WidgetController widgetController() { return widgetController; }
    @Override public void setWidgetJob(WidgetController.WidgetJob job) { widgets.setWidgetJob(job); }

    @Override public void deselectAnnotation() { view.deselectAnnotation(); }

    @Override
    public void selectAnnotation(int index, RectF bounds) {
        long objectId = -1L;
        try {
            Annotation[] annots = view.mAnnotations;
            if (annots != null && index >= 0 && index < annots.length) {
                Annotation a = annots[index];
                if (a != null) objectId = a.objectNumber;
            }
        } catch (Throwable ignore) {
            objectId = -1L;
        }
        selectionManager.select(index, objectId, bounds, selectionUiBridge.selectionBoxHost());
    }

    @Override public void onTextAnnotationTapped(Annotation annotation) { view.forwardTextAnnotation(annotation); }

    @Override public void requestChangeReport() { view.requestChangeReport(); }

    @Override public void invokeTextDialog(String text, float docRelX, float docRelY) {
        widgets.invokeTextDialog(text, docRelX, docRelY);
    }

    @Override
    public void invokeChoiceDialog(String[] options, String[] selected, boolean multiSelect, boolean editable, float docRelX, float docRelY) {
        widgets.invokeChoiceDialog(options, selected, multiSelect, editable, docRelX, docRelY);
    }

    @Override public void warnNoSignatureSupport() { widgets.warnNoSignatureSupport(); }
    @Override public void invokeSigningDialog() { widgets.invokeSigningDialog(); }
    @Override public void invokeSignatureCheckingDialog() { widgets.invokeSignatureCheckingDialog(); }
}

