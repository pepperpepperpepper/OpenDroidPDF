package org.opendroidpdf.app.services;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.app.services.Provider;
import org.opendroidpdf.app.reader.gesture.ReaderMode;

/**
 * Simple DrawingService that defers to the active MuPDFReaderView.
 */
public class DrawingServiceImpl implements DrawingService {
    private final Provider<MuPDFReaderView> docViewSupplier;
    private static final boolean LOG_MODE = org.opendroidpdf.BuildConfig.DEBUG;

    public DrawingServiceImpl(Provider<MuPDFReaderView> docViewSupplier) {
        this.docViewSupplier = docViewSupplier;
    }

    @Override
    public boolean finalizePendingInk() {
        MuPDFReaderView docView = docViewSupplier.get();
        if (docView == null) return true;
        try {
            MuPDFView view = (MuPDFView) docView.getSelectedView();
            if (view instanceof MuPDFPageView) {
                MuPDFPageView pageView = (MuPDFPageView) view;
                android.graphics.PointF[][] pending = pageView.getDrawingController().getDraw();
                if (pending != null && pending.length > 0) {
                    boolean committed = pageView.saveDraw();
                    notifyStrokeCountChanged(pageView.getDrawingSize());
                    return committed;
                }
            }
        } catch (Throwable ignore) {
            // Keep best-effort behavior consistent with legacy path.
        }
        return true;
    }

    @Override
    public void onPenSettingsChanged(org.opendroidpdf.app.preferences.PenPrefsSnapshot snapshot) {
        finalizePendingInk();
    }

    @Override
    public void switchToDrawingMode() { withDocView(v -> v.setMode(ReaderMode.DRAWING)); }

    @Override
    public void switchToErasingMode() { withDocView(v -> v.setMode(ReaderMode.ERASING)); }

    @Override
    public void switchToViewingMode() { withDocView(v -> v.setMode(ReaderMode.VIEWING)); }

    @Override
    public void switchToAddingTextMode() { withDocView(v -> v.setMode(ReaderMode.ADDING_TEXT_ANNOT)); }

    @Override
    public boolean isDrawingModeActive() {
        MuPDFReaderView v = docViewSupplier.get();
        return v != null && v.getMode() == ReaderMode.DRAWING;
    }

    @Override
    public boolean isErasingModeActive() {
        MuPDFReaderView v = docViewSupplier.get();
        return v != null && v.getMode() == ReaderMode.ERASING;
    }

    @Override
    public boolean isAddingTextModeActive() {
        MuPDFReaderView v = docViewSupplier.get();
        return v != null && v.getMode() == ReaderMode.ADDING_TEXT_ANNOT;
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
        if (v == null) {
            if (LOG_MODE) android.util.Log.w("DrawingService", "withDocView: docView is null");
            return;
        }
        if (LOG_MODE) android.util.Log.d("DrawingService", "withDocView: before mode=" + v.getMode());
        consumer.accept(v);
        if (LOG_MODE) android.util.Log.d("DrawingService", "withDocView: after mode=" + v.getMode());
    }
}
