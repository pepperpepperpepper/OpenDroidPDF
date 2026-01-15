package org.opendroidpdf.app.hosts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

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
import org.opendroidpdf.app.document.XfaPackConversionPipeline;
import org.opendroidpdf.app.document.XfaPackInstallIntents;
import org.opendroidpdf.app.document.XfaFormDetector;
import org.opendroidpdf.app.lifecycle.ActivityComposition;
import org.opendroidpdf.xfapack.IXfaPackConverter;

import java.util.ArrayList;
import java.util.List;

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
        Uri uri = activity.currentDocumentUriOrNull();
        androidx.appcompat.app.AlertDialog.Builder b = activity.getAlertBuilder();
        b.setTitle(org.opendroidpdf.R.string.app_name);

        if (uri == null) {
            b.setMessage(activity.getString(org.opendroidpdf.R.string.pdf_xfa_explainer));
            b.setPositiveButton(activity.getString(org.opendroidpdf.R.string.dismiss), (d, w) -> {});
            b.show();
            return;
        }

        final boolean xfaPackInstalled = isXfaPackInstalled(activity);
        final boolean xfaPackValid = xfaPackInstalled && xfaPackSignaturesMatch(activity);

        List<ActionItem> items = new ArrayList<>();
        if (xfaPackValid) {
            items.add(new ActionItem(
                    activity.getString(org.opendroidpdf.R.string.xfa_pack_convert_to_acroform),
                    () -> convertCurrentPdfWithXfaPack(IXfaPackConverter.MODE_CONVERT_TO_ACROFORM)));
            items.add(new ActionItem(
                    activity.getString(org.opendroidpdf.R.string.xfa_pack_flatten_to_pdf),
                    () -> convertCurrentPdfWithXfaPack(IXfaPackConverter.MODE_FLATTEN_TO_PDF)));
        } else {
            items.add(new ActionItem(
                    activity.getString(org.opendroidpdf.R.string.xfa_pack_install),
                    () -> openXfaPackInstall(activity)));
        }
        items.add(new ActionItem(
                activity.getString(org.opendroidpdf.R.string.word_import_open_in_other_app),
                () -> openPdfInAnotherApp(activity, uri)));
        items.add(new ActionItem(
                activity.getString(org.opendroidpdf.R.string.menu_share),
                this::shareCurrentDocumentForConversion));

        CharSequence[] labels = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = items.get(i).label;

        // AlertDialog can't show both a message and setItems(); use a ListView with a non-clickable
        // header so the explainer text stays visible above the actions.
        ListView listView = new ListView(activity);

        TextView header = new TextView(activity);
        header.setText(activity.getString(org.opendroidpdf.R.string.pdf_xfa_explainer));
        int padPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16,
                activity.getResources().getDisplayMetrics());
        header.setPadding(padPx, padPx, padPx, padPx);
        header.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        listView.addHeaderView(header, null, false);

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_list_item_1,
                labels);
        listView.setAdapter(adapter);

        b.setView(listView);
        b.setNegativeButton(activity.getString(org.opendroidpdf.R.string.dismiss), (d, w) -> {});
        androidx.appcompat.app.AlertDialog dialog = b.create();
        listView.setOnItemClickListener((parent, view, position, id) -> {
            int idx = position - listView.getHeaderViewsCount();
            if (idx >= 0 && idx < items.size()) {
                dialog.dismiss();
                items.get(idx).run.run();
            }
        });
        dialog.show();
    }

    private void convertCurrentPdfWithXfaPack(int mode) {
        Uri uri = activity.currentDocumentUriOrNull();
        if (uri == null) return;

        String password = null;
        try {
            org.opendroidpdf.OpenDroidPDFCore core = activity.getCore();
            if (core != null) {
                password = core.getDocumentPasswordOrNull();
            }
        } catch (Throwable ignore) {
        }

        activity.showInfo(activity.getString(org.opendroidpdf.R.string.xfa_pack_converting));

        final Context appContext = activity.getApplicationContext();
        final Uri inUri = uri;
        final String passwordFinal = password;
        AppCoroutines.launchIo(AppCoroutines.ioScope(), () -> {
            XfaPackConversionPipeline.Result result = XfaPackConversionPipeline.convert(appContext, inUri, mode, passwordFinal);
            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                if (result.outputUri != null) {
                    openConvertedPdf(result.outputUri);
                    return;
                }
                if (result.action == XfaPackConversionPipeline.Action.INSTALL_XFA_PACK) {
                    openXfaPackInstall(activity);
                    return;
                }
                if (result.message != null) {
                    activity.showInfo(result.message);
                } else {
                    activity.showInfo(activity.getString(org.opendroidpdf.R.string.xfa_pack_failed));
                }
            });
        });
    }

    private void openConvertedPdf(@NonNull Uri pdfUri) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setDataAndType(pdfUri, "application/pdf");

        // Mirror the IntentRouter onNewIntent flow: reset state before opening.
        activity.setIntent(i);
        activity.resetDocumentStateForIntent();
        activity.openDocumentFromIntent(i);
    }

    private void openPdfInAnotherApp(@NonNull Context context, @NonNull Uri pdfUri) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        String mime = null;
        try {
            mime = context.getContentResolver().getType(pdfUri);
        } catch (Throwable ignore) {
        }
        if (mime == null || mime.isEmpty()) {
            mime = "application/pdf";
        }
        i.setDataAndType(pdfUri, mime);

        try {
            context.startActivity(Intent.createChooser(
                    i,
                    context.getString(org.opendroidpdf.R.string.word_import_open_in_other_app)));
        } catch (Throwable t) {
            Log.w("OpenDroidPDF", "Failed to open external app for uri=" + pdfUri, t);
            activity.showInfo(context.getString(org.opendroidpdf.R.string.word_import_open_failed));
        }
    }

    private void openXfaPackInstall(@NonNull Context context) {
        Intent i = XfaPackInstallIntents.newOpenXfaPackInFdroidIntent();
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (i.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(i);
                return;
            }
        } catch (Throwable ignore) {
        }

        Intent fallback = XfaPackInstallIntents.newOpenRepoUrlIntent();
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (fallback.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(fallback);
                return;
            }
        } catch (Throwable ignore) {
        }

        activity.showInfo(context.getString(org.opendroidpdf.R.string.word_import_open_failed));
    }

    private void shareCurrentDocumentForConversion() {
        ActivityComposition.Composition comp = activity.getComposition();
        if (comp != null && comp.exportController != null) {
            comp.exportController.shareDoc();
            return;
        }

        Uri uri = activity.currentDocumentUriOrNull();
        if (uri == null) return;

        Intent share = new Intent(Intent.ACTION_SEND);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setType("application/pdf");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.setClipData(android.content.ClipData.newUri(activity.getContentResolver(), "PDF", uri));
        try {
            activity.startActivity(Intent.createChooser(share, activity.getString(org.opendroidpdf.R.string.share_with)));
        } catch (Throwable t) {
            Log.w("OpenDroidPDF", "Failed to share uri=" + uri, t);
            activity.showInfo(activity.getString(org.opendroidpdf.R.string.error_exporting));
        }
    }

    private static boolean isXfaPackInstalled(@NonNull Context context) {
        try {
            context.getPackageManager().getPackageInfo(XfaPackConversionPipeline.XFA_PACK_PACKAGE, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean xfaPackSignaturesMatch(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            return pm.checkSignatures(context.getPackageName(), XfaPackConversionPipeline.XFA_PACK_PACKAGE)
                    == PackageManager.SIGNATURE_MATCH;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final class ActionItem {
        final String label;
        final Runnable run;

        ActionItem(@NonNull String label, @NonNull Runnable run) {
            this.label = label;
            this.run = run;
        }
    }
}
