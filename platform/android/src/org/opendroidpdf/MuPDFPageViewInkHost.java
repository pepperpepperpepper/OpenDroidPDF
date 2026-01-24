package org.opendroidpdf;

import org.opendroidpdf.app.drawing.InkController;
import org.opendroidpdf.app.reader.ReaderComposition;
import org.opendroidpdf.app.reader.gesture.ReaderMode;

final class MuPDFPageViewInkHost implements InkController.Host {
    private final MuPDFPageView view;
    private final ReaderComposition composition;

    MuPDFPageViewInkHost(MuPDFPageView view, ReaderComposition composition) {
        this.view = view;
        this.composition = composition;
    }

    @Override public DrawingController drawingController() { return view.getDrawingController(); }

    @Override
    public void requestReaderErasingMode() {
        composition.modeRequester().requestMode(ReaderMode.ERASING);
    }

    @Override public int pageNumber() { return view.mPageNumber; }
    @Override public void requestFullRedraw() { view.requestFullRedrawAfterNextAnnotationLoad(); }
    @Override public void loadAnnotations() { view.loadAnnotations(); }
    @Override public void discardRenderedPage() { view.discardRenderedPage(); }
    @Override public void redraw(boolean updateHq) { view.redraw(updateHq); }
    @Override public void invalidateOverlay() { view.invalidateOverlay(); }
    @Override public float currentInkThickness() { return view.currentInkThickness(); }
    @Override public int currentInkColor() { return view.currentInkColor(); }
    @Override public float currentEraserThickness() { return view.currentEraserThickness(); }
}

