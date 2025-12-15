package org.opendroidpdf.app.services;

import org.opendroidpdf.AnnotationModeController;
import org.opendroidpdf.MuPDFReaderView;

import java.util.function.Supplier;

/**
 * Simple DrawingService that defers to the active MuPDFReaderView.
 */
public class DrawingServiceImpl implements DrawingService {
    private final Supplier<MuPDFReaderView> docViewSupplier;

    public DrawingServiceImpl(Supplier<MuPDFReaderView> docViewSupplier) {
        this.docViewSupplier = docViewSupplier;
    }

    @Override
    public void finalizePendingInkBeforePenSettingChange() {
        AnnotationModeController.finalizePendingInkBeforePenSettingChange(docViewSupplier.get());
    }
}
