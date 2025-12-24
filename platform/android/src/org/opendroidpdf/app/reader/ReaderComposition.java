package org.opendroidpdf.app.reader;

import android.content.Context;

import androidx.annotation.Nullable;

import org.opendroidpdf.core.AnnotationController;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.SignatureController;
import org.opendroidpdf.core.WidgetController;
import org.opendroidpdf.app.annotation.AnnotationUiController;
import org.opendroidpdf.app.widget.WidgetUiBridge;
import org.opendroidpdf.app.widget.WidgetAreasLoader;
import org.opendroidpdf.widget.WidgetUiController;
import org.opendroidpdf.app.signature.SignatureFlowController;
import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.reflow.ReflowLayoutProfileId;
import org.opendroidpdf.app.reflow.ReflowPrefsSnapshot;
import org.opendroidpdf.app.reflow.ReflowPrefsStore;
import org.opendroidpdf.app.reflow.SharedPreferencesReflowPrefsStore;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.SQLiteSidecarAnnotationStore;

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
    @Nullable private final SidecarAnnotationSession sidecarSession;
    private final org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager =
            new org.opendroidpdf.app.annotation.AnnotationSelectionManager();
    private volatile ReaderModeRequester modeRequester = ReaderModeRequester.NOOP;
    private volatile TextAnnotationRequester textAnnotationRequester = TextAnnotationRequester.NOOP;

    public ReaderComposition(Context context,
                             MuPdfController muPdfController,
                             String docId,
                             String legacyDocId,
                             DocumentType docType,
                             boolean canSaveToCurrentUri) {
        this.context = context;
        this.sidecarSession = maybeCreateSidecarSession(context, muPdfController, docId, legacyDocId, docType, canSaveToCurrentUri);
        this.annotationController = new AnnotationController(muPdfController);
        this.annotationUiController = new AnnotationUiController(annotationController, sidecarSession);
        this.widgetController = new WidgetController(muPdfController);
        this.widgetUiBridge = new WidgetUiBridge(context, widgetController);
        this.signatureController = new SignatureController(muPdfController);
    }

    public Context context() { return context; }

    public AnnotationController annotationController() { return annotationController; }
    public AnnotationUiController annotationUiController() { return annotationUiController; }
    @Nullable public SidecarAnnotationSession sidecarSession() { return sidecarSession; }
    public org.opendroidpdf.app.annotation.AnnotationSelectionManager selectionManager() { return selectionManager; }

    public ReaderModeRequester modeRequester() { return modeRequester; }
    public void setModeRequester(ReaderModeRequester requester) {
        modeRequester = requester != null ? requester : ReaderModeRequester.NOOP;
    }

    public TextAnnotationRequester textAnnotationRequester() { return textAnnotationRequester; }
    public void setTextAnnotationRequester(TextAnnotationRequester requester) {
        textAnnotationRequester = requester != null ? requester : TextAnnotationRequester.NOOP;
    }

    public WidgetController widgetController() { return widgetController; }
    public WidgetUiController newWidgetUiController() { return new WidgetUiController(widgetUiBridge); }
    public WidgetAreasLoader newWidgetAreasLoader() { return new WidgetAreasLoader(widgetController); }

    public SignatureController signatureController() { return signatureController; }

    public SignatureFlowController newSignatureFlow(SignatureFlowController.FilePickerLauncher pickerLauncher,
                                                    SignatureFlowController.ChangeReporter reporter) {
        return new SignatureFlowController(context, signatureController, pickerLauncher, reporter);
    }

    @Nullable
    private static SidecarAnnotationSession maybeCreateSidecarSession(Context context,
                                                                      MuPdfController muPdfController,
                                                                      String docId,
                                                                      String legacyDocId,
                                                                      DocumentType docType,
                                                                      boolean canSaveToCurrentUri) {
        if (context == null || muPdfController == null || docId == null || docId.isEmpty() || docType == null) {
            return null;
        }
        boolean useSidecar = (docType == DocumentType.EPUB) || (docType == DocumentType.PDF && !canSaveToCurrentUri);
        if (!useSidecar) return null;

        String layoutProfileId = null;
        ReflowPrefsStore reflowPrefsStore = null;
        ReflowPrefsSnapshot reflowPrefsSnapshot = null;
        if (docType == DocumentType.EPUB) {
            reflowPrefsStore = new SharedPreferencesReflowPrefsStore(context.getApplicationContext());
            reflowPrefsSnapshot = reflowPrefsStore.load(docId);
            float em = reflowPrefsSnapshot.fontDp * 72f / 160f;
            android.graphics.PointF pageSize0 = null;
            try {
                pageSize0 = muPdfController.pageSize(0);
            } catch (Throwable ignore) {
            }
            layoutProfileId = ReflowLayoutProfileId.from(reflowPrefsSnapshot, pageSize0, em);
        }
        return new SidecarAnnotationSession(
                docId,
                legacyDocId,
                layoutProfileId,
                new SQLiteSidecarAnnotationStore(context),
                reflowPrefsStore,
                reflowPrefsSnapshot);
    }
}
