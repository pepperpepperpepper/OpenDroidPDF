package org.opendroidpdf.app.reader;

import android.content.Context;

import org.opendroidpdf.core.AnnotationController;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.SignatureController;
import org.opendroidpdf.core.WidgetController;
import org.opendroidpdf.app.annotation.AnnotationUiController;
import org.opendroidpdf.app.widget.WidgetUiBridge;
import org.opendroidpdf.app.widget.WidgetAreasLoader;
import org.opendroidpdf.widget.WidgetUiController;
import org.opendroidpdf.app.signature.SignatureFlowController;

/**
 * Shared, per-document composition for reader/page controllers.
 *
 * Built once for a MuPdfController and reused across page views so ownership
 * is explicit and construction boilerplate stays out of MuPDFPageView.
 */
public class ReaderComposition {

    private final AnnotationController annotationController;
    private final AnnotationUiController annotationUiController;
    private final WidgetController widgetController;
    private final WidgetUiBridge widgetUiBridge;
    private final SignatureController signatureController;
    private final Context context;
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager =
            new org.opendroidpdf.app.annotation.AnnotationSelectionManager();

    public ReaderComposition(Context context, MuPdfController muPdfController) {
        this.context = context;
        this.annotationController = new AnnotationController(muPdfController);
        this.annotationUiController = new AnnotationUiController(annotationController);
        this.widgetController = new WidgetController(muPdfController);
        this.widgetUiBridge = new WidgetUiBridge(context, widgetController);
        this.signatureController = new SignatureController(muPdfController);
    }

    public Context context() { return context; }

    public AnnotationController annotationController() { return annotationController; }
    public AnnotationUiController annotationUiController() { return annotationUiController; }
    public org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager() { return selectionManager; }

    public WidgetController widgetController() { return widgetController; }
    public WidgetUiController newWidgetUiController() { return new WidgetUiController(widgetUiBridge); }
    public WidgetAreasLoader newWidgetAreasLoader() { return new WidgetAreasLoader(widgetController); }

    public SignatureController signatureController() { return signatureController; }

    public SignatureFlowController newSignatureFlow(SignatureFlowController.FilePickerLauncher pickerLauncher,
                                                    SignatureFlowController.ChangeReporter reporter) {
        return new SignatureFlowController(context, signatureController, pickerLauncher, reporter);
    }
}
