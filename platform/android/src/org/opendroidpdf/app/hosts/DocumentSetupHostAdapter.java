package org.opendroidpdf.app.hosts;

import android.content.Context;
import android.net.Uri;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.document.DocumentIdentity;
import org.opendroidpdf.app.document.DocumentIdentityResolver;
import org.opendroidpdf.app.document.DocumentOrigin;
import org.opendroidpdf.app.document.DocumentSetupController;
import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.document.XfaFormDetector;

/**
 * Bridges DocumentSetupController.Host calls onto OpenDroidPDFActivity while
 * keeping that class lean.
 */
public final class DocumentSetupHostAdapter implements DocumentSetupController.Host {
    private final OpenDroidPDFActivity activity;
    private final DocumentViewHostAdapter documentViewHostAdapter;
    private final FilePickerHostAdapter filePickerHost;
    private final DocumentAccessHostAdapter documentAccessHostAdapter;

    @Nullable private Uri lastXfaCheckedUri;
    private boolean lastXfaCheckComplete;
    private boolean lastXfaHasXfa;

    public DocumentSetupHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                    @NonNull DocumentViewHostAdapter documentViewHostAdapter,
                                    @NonNull FilePickerHostAdapter filePickerHost,
                                    @NonNull DocumentAccessHostAdapter documentAccessHostAdapter) {
        this.activity = activity;
        this.documentViewHostAdapter = documentViewHostAdapter;
        this.filePickerHost = filePickerHost;
        this.documentAccessHostAdapter = documentAccessHostAdapter;
    }

    @Nullable @Override public OpenDroidPDFCore getCore() { return activity.getCore(); }
    @Override public void setCoreInstance(OpenDroidPDFCore core) { activity.setCoreInstance(core); }
    @Override public void setCurrentDocumentIdentity(@NonNull DocumentIdentity identity) { activity.setCurrentDocumentIdentity(identity); }
    @Override public void setCurrentDocumentOrigin(@NonNull DocumentOrigin origin) { activity.setCurrentDocumentOrigin(origin); }
    @Override public void setSaveToCurrentUriDisabledByPolicy(boolean disabled) { activity.setSaveToCurrentUriDisabledByPolicy(disabled); }
    @Override public void setCurrentUserFacingDocument(@Nullable Uri uri, @Nullable String displayName) { activity.setCurrentUserFacingDocument(uri, displayName); }
    @Nullable @Override public DocumentIdentity currentDocumentIdentityOrNull() { return activity.currentDocumentIdentityOrNull(); }
    @Override public androidx.appcompat.app.AlertDialog.Builder alertBuilder() { return activity.getAlertBuilder(); }
    @Override public void requestPassword() { activity.requestPassword(); }
    @Override public org.opendroidpdf.core.SearchController getSearchController() { return activity.getSearchController(); }
    @Override public MuPDFReaderView getDocView() { return activity.getDocView(); }
    @Override public void showInfo(String message) { activity.showInfo(message); }
    @Override public Context getContext() { return activity; }
    @Override public void setTitle() { activity.setTitle(); }
    @Override public org.opendroidpdf.app.hosts.FilePickerHostAdapter filePickerHost() { return filePickerHost; }
    @Override public int getActionBarHeightPx() {
        try {
            androidx.appcompat.app.ActionBar ab = activity.getSupportActionBar();
            if (ab != null && ab.isShowing()) {
                android.util.TypedValue tv = new android.util.TypedValue();
                if (activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
                    return android.util.TypedValue.complexToDimensionPixelSize(tv.data, activity.getResources().getDisplayMetrics());
                }
            }
        } catch (Throwable ignore) { }
        return 0;
    }
    @Override public void hideDashboard() {
        org.opendroidpdf.app.navigation.DashboardDelegate dd = activity.getDashboardDelegate();
        if (dd != null) dd.hideDashboard();
    }
    @Override public ViewGroup ensureDocumentContainer() {
        org.opendroidpdf.app.navigation.DashboardDelegate dd = activity.getDashboardDelegate();
        return dd != null ? dd.ensureDocumentContainer() : null;
    }
    @Override public void createDocViewIfNeeded() {
        if (activity.getCore() == null || activity.getDocView() != null) return;
        activity.setDocView(org.opendroidpdf.DocViewFactory.create(new DocViewFactoryHostAdapter(activity)));
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd != null) dvd.markDocViewNeedsNewAdapter();
    }
    @Override public void attachDocViewToContainer(ViewGroup container) {
        org.opendroidpdf.app.navigation.DashboardDelegate dd = activity.getDashboardDelegate();
        if (dd != null) dd.attachDocViewToContainer(container);
    }
    @Override public void onDocViewAttached() {
        MuPDFReaderView doc = activity.getDocView();
        if (doc == null) return;
        doc.clearSearchResults();
        if (activity.currentDocumentOrigin() == DocumentOrigin.WORD) {
            Uri uri = activity.currentDocumentState().uri();
            if (uri != null) activity.recordRecent(uri);
        }
        maybePromptReflowLayoutMismatch();
        maybePromptImportedWordBanner();
        maybePromptPdfXfaUnsupportedBanner();
        maybePromptPdfReadOnlyBanner();
    }
    @Override public void ensureDocAdapter() {
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd == null) return;
        org.opendroidpdf.core.MuPdfRepository repo = activity.getRepository();
        org.opendroidpdf.core.MuPdfController controller = activity.getMuPdfController();
        dvd.ensureDocAdapter(activity.getCore(), repo, controller, dvd.docViewNeedsNewAdapter());
    }
    @Override public void restoreViewportIfAny() {
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd == null) return;
        dvd.restoreViewportIfAny(activity.currentDocumentUriOrNull());
    }
    @Override public void restoreDocViewStateIfAny() {
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd != null) dvd.restoreDocViewStateIfAny();
    }
    @Override public void syncDocViewPreferences() {
        org.opendroidpdf.app.document.DocumentViewDelegate dvd = activity.getDocumentViewDelegate();
        if (dvd != null) dvd.syncPreferences();
    }

    @Override
    public void promptReopenWithPermission(Uri failedUri) {
        documentAccessHostAdapter.showOpenDocumentForEditActivity();
    }

    private void maybePromptReflowLayoutMismatch() {
        org.opendroidpdf.app.lifecycle.ActivityComposition.Composition comp = activity.getComposition();
        if (comp == null || comp.reflowPrefsStore == null) return;
        org.opendroidpdf.app.ui.UiStateDelegate ui = activity.getUiStateDelegate();
        if (documentViewHostAdapter.currentDocumentType() != org.opendroidpdf.app.document.DocumentType.EPUB) {
            if (ui != null) ui.dismissReflowLayoutMismatchBanner();
            return;
        }

        SidecarAnnotationProvider provider = documentViewHostAdapter.sidecarAnnotationProviderOrNull();
        if (!(provider instanceof SidecarAnnotationSession)) {
            if (ui != null) ui.dismissReflowLayoutMismatchBanner();
            return;
        }
        SidecarAnnotationSession session = (SidecarAnnotationSession) provider;
        if (!session.hasAnnotationsInOtherLayouts()) {
            if (ui != null) ui.dismissReflowLayoutMismatchBanner();
            return;
        }

        // If we can't map back to a stored annotated layout snapshot, fall back to a toast.
        if (comp.reflowPrefsStore.loadAnnotatedLayoutOrNull(session.docId()) == null) {
            activity.showInfo(activity.getString(R.string.reflow_annotations_hidden));
            return;
        }

        if (ui != null) {
            int message = session.hasAnyAnnotationsInCurrentLayout()
                    ? R.string.reflow_layout_mismatch_message
                    : R.string.reflow_annotations_hidden;
            ui.showReflowLayoutMismatchBanner(message, () ->
                    new org.opendroidpdf.app.reflow.ReflowSettingsController(
                            new ReflowSettingsHostAdapter(activity),
                            documentViewHostAdapter,
                            comp.reflowPrefsStore,
                            comp.documentViewDelegate)
                            .applyAnnotatedLayoutForCurrentDocument());
        } else {
            activity.showInfo(activity.getString(R.string.reflow_annotations_hidden));
        }
    }

    private void maybePromptPdfReadOnlyBanner() {
        org.opendroidpdf.app.ui.UiStateDelegate ui = activity.getUiStateDelegate();
        if (activity.currentDocumentOrigin() == DocumentOrigin.WORD) {
            if (ui != null) ui.dismissPdfReadOnlyBanner();
            return;
        }
        if (documentViewHostAdapter.currentDocumentType() != DocumentType.PDF || activity.canSaveToCurrentUri()) {
            if (ui != null) ui.dismissPdfReadOnlyBanner();
            return;
        }

        org.opendroidpdf.OpenDroidPDFCore core = activity.getCore();
        android.net.Uri uri = core != null ? core.getUri() : null;
        if (uri == null) {
            if (ui != null) ui.dismissPdfReadOnlyBanner();
            return;
        }

        if (ui != null) {
            ui.showPdfReadOnlyBanner(
                    org.opendroidpdf.R.string.pdf_readonly_banner,
                    org.opendroidpdf.R.string.pdf_enable_saving,
                    () -> promptReopenWithPermission(uri));
        } else {
            activity.showInfo(activity.getString(org.opendroidpdf.R.string.pdf_readonly_banner));
        }
    }

    private void maybePromptImportedWordBanner() {
        org.opendroidpdf.app.ui.UiStateDelegate ui = activity.getUiStateDelegate();
        if (activity.currentDocumentOrigin() != DocumentOrigin.WORD) {
            if (ui != null) ui.dismissImportedWordBanner();
            return;
        }

        if (ui != null) {
            ui.showImportedWordBanner(
                    org.opendroidpdf.R.string.word_imported_banner,
                    org.opendroidpdf.R.string.word_imported_learn_more,
                    () -> {
                        androidx.appcompat.app.AlertDialog a = activity.getAlertBuilder().create();
                        a.setTitle(org.opendroidpdf.R.string.app_name);
                        a.setMessage(activity.getString(org.opendroidpdf.R.string.word_imported_explainer));
                        a.setButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE,
                                activity.getString(org.opendroidpdf.R.string.dismiss),
                                (d, w) -> {});
                        a.show();
                    });
        } else {
            activity.showInfo(activity.getString(org.opendroidpdf.R.string.word_imported_banner));
        }
    }

    private void maybePromptPdfXfaUnsupportedBanner() {
        org.opendroidpdf.app.ui.UiStateDelegate ui = activity.getUiStateDelegate();
        if (documentViewHostAdapter.currentDocumentType() != DocumentType.PDF) {
            if (ui != null) ui.dismissPdfXfaUnsupportedBanner();
            lastXfaCheckedUri = null;
            lastXfaCheckComplete = false;
            lastXfaHasXfa = false;
            return;
        }

        Uri uri = activity.currentDocumentUriOrNull();
        if (uri == null) {
            if (ui != null) ui.dismissPdfXfaUnsupportedBanner();
            lastXfaCheckedUri = null;
            lastXfaCheckComplete = false;
            lastXfaHasXfa = false;
            return;
        }

        if (lastXfaCheckComplete && uri.equals(lastXfaCheckedUri)) {
            if (ui != null) {
                if (lastXfaHasXfa) {
                    ui.showPdfXfaUnsupportedBanner(
                            R.string.pdf_xfa_banner,
                            R.string.pdf_xfa_learn_more,
                            () -> showXfaExplainerDialog());
                } else {
                    ui.dismissPdfXfaUnsupportedBanner();
                }
            } else if (lastXfaHasXfa) {
                activity.showInfo(activity.getString(R.string.pdf_xfa_banner));
            }
            return;
        }

        lastXfaCheckedUri = uri;
        lastXfaCheckComplete = false;
        lastXfaHasXfa = false;
        if (ui != null) ui.dismissPdfXfaUnsupportedBanner();

        final Context appContext = activity.getApplicationContext();
        final Uri checkUri = uri;
        AppCoroutines.launchIo(AppCoroutines.ioScope(), () -> {
            final boolean hasXfa = XfaFormDetector.hasXfaForms(appContext, checkUri);
            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                if (lastXfaCheckedUri == null || !lastXfaCheckedUri.equals(checkUri)) return;
                lastXfaCheckComplete = true;
                lastXfaHasXfa = hasXfa;

                org.opendroidpdf.app.ui.UiStateDelegate uiNow = activity.getUiStateDelegate();
                if (uiNow != null) {
                    if (hasXfa) {
                        uiNow.showPdfXfaUnsupportedBanner(
                                R.string.pdf_xfa_banner,
                                R.string.pdf_xfa_learn_more,
                                () -> showXfaExplainerDialog());
                    } else {
                        uiNow.dismissPdfXfaUnsupportedBanner();
                    }
                } else if (hasXfa) {
                    activity.showInfo(activity.getString(R.string.pdf_xfa_banner));
                }
            });
        });
    }

    private void showXfaExplainerDialog() {
        androidx.appcompat.app.AlertDialog a = activity.getAlertBuilder().create();
        a.setTitle(org.opendroidpdf.R.string.app_name);
        a.setMessage(activity.getString(org.opendroidpdf.R.string.pdf_xfa_explainer));
        a.setButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE,
                activity.getString(org.opendroidpdf.R.string.dismiss),
                (d, w) -> {});
        a.show();
    }
}
