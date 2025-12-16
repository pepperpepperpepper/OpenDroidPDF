package org.opendroidpdf.app.services;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.app.services.Provider;

/**
 * Simple DrawingService that defers to the active MuPDFReaderView.
 */
public class DrawingServiceImpl implements DrawingService {
    private final Provider<MuPDFReaderView> docViewSupplier;

    public DrawingServiceImpl(Provider<MuPDFReaderView> docViewSupplier) {
        this.docViewSupplier = docViewSupplier;
    }

    @Override
    public void finalizePendingInk() {
        MuPDFReaderView docView = docViewSupplier.get();
        if (docView == null) return;
        try {
            MuPDFView view = (MuPDFView) docView.getSelectedView();
            if (view instanceof MuPDFPageView) {
                MuPDFPageView pageView = (MuPDFPageView) view;
                android.graphics.PointF[][] pending = pageView.getDrawingController().getDraw();
                if (pending != null && pending.length > 0) {
                    pageView.saveDraw();
                    notifyStrokeCountChanged(pageView.getDrawingSize());
                }
            }
        } catch (Throwable ignore) {
            // Keep best-effort behavior consistent with legacy path.
        }
    }

    @Override
    public void onPenSettingsChanged(org.opendroidpdf.app.preferences.PenPrefsSnapshot snapshot) {
        finalizePendingInk();
    }

    @Override
    public void switchToDrawingMode() { withDocView(MuPDFReaderView::switchToDrawingMode); }

    @Override
    public void switchToErasingMode() { withDocView(MuPDFReaderView::switchToErasingMode); }

    @Override
    public void switchToViewingMode() { withDocView(MuPDFReaderView::switchToViewingMode); }

    @Override
    public void switchToAddingTextMode() { withDocView(MuPDFReaderView::switchToAddingTextMode); }

    @Override
    public boolean isDrawingModeActive() {
        MuPDFReaderView v = docViewSupplier.get();
        return v != null && v.isDrawingModeActive();
    }

    @Override
    public boolean isErasingModeActive() {
        MuPDFReaderView v = docViewSupplier.get();
        return v != null && v.isErasingModeActive();
    }

    @Override
    public boolean isAddingTextModeActive() {
        MuPDFReaderView v = docViewSupplier.get();
        return v != null && v.isAddingTextModeActive();
    }

    @Override
    public void cancelAnnotationMode(org.opendroidpdf.app.ui.ActionBarMode currentMode) {
        MuPDFReaderView docView = docViewSupplier.get();
        if (docView == null || currentMode == null) return;
        switch (currentMode) {
            case Annot:
            case Edit:
            case AddingTextAnnot:
                docView.switchToViewingMode();
                break;
            default:
                // no-op
                break;
        }
    }

    @Override
    public void confirmAnnotationChanges(org.opendroidpdf.app.ui.ActionBarMode currentMode) {
        MuPDFReaderView docView = docViewSupplier.get();
        if (docView == null || currentMode == null) return;
        org.opendroidpdf.PageView page = null;
        android.view.View selected = docView.getSelectedView();
        if (selected instanceof org.opendroidpdf.PageView) {
            page = (org.opendroidpdf.PageView) selected;
        }
        switch (currentMode) {
            case Annot:
                if (page != null) {
                    page.saveDraw();
                    notifyStrokeCountChanged(page.getDrawingSize());
                }
                break;
            case Edit:
                if (page != null) {
                    page.deselectAnnotation();
                }
                break;
            default:
                break;
        }
        if (currentMode == org.opendroidpdf.app.ui.ActionBarMode.Annot ||
                currentMode == org.opendroidpdf.app.ui.ActionBarMode.Edit) {
            docView.switchToViewingMode();
        }
    }

    @Override
    public void notifyStrokeCountChanged(int strokeCount) {
        MuPDFReaderView docView = docViewSupplier.get();
        if (docView != null) {
            docView.notifyStrokeCountChanged(strokeCount);
        }
    }

    private interface DocViewConsumer { void accept(MuPDFReaderView v); }

    private void withDocView(DocViewConsumer consumer) {
        MuPDFReaderView v = docViewSupplier.get();
        if (v != null) consumer.accept(v);
    }
}
