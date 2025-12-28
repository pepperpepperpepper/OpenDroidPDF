package org.opendroidpdf.app.document;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.graphics.PointF;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.PageView;
import org.opendroidpdf.app.epub.EpubEncryptionDetector;
import org.opendroidpdf.app.services.SearchService;
import org.opendroidpdf.app.preferences.PreferencesCoordinator;
import org.opendroidpdf.app.reflow.ReflowAnnotatedLayout;
import org.opendroidpdf.app.reflow.ReflowPrefsSnapshot;
import org.opendroidpdf.app.reflow.ReflowPrefsStore;
import org.opendroidpdf.app.services.search.SearchDocumentView;
import org.opendroidpdf.app.diagnostics.AppLog;

/**
 * Handles core initialization, docView setup, and search task setup to slim the activity.
 */
public class DocumentSetupController {

    private static final String TAG = "DocumentSetupController";

    private final SearchService searchService;
    private final PreferencesCoordinator preferencesCoordinator;
    private final ReflowPrefsStore reflowPrefsStore;

    public interface Host {
        OpenDroidPDFCore getCore();
        void setCoreInstance(OpenDroidPDFCore core);
        void setCurrentDocumentIdentity(@NonNull DocumentIdentity identity);
        @Nullable DocumentIdentity currentDocumentIdentityOrNull();
        AlertDialog.Builder alertBuilder();
        void requestPassword();
        org.opendroidpdf.core.SearchController getSearchController();
        MuPDFReaderView getDocView();
        default void onDocViewReady() {}
        void showInfo(String message);
        Context getContext();
        void setTitle();
        org.opendroidpdf.app.hosts.FilePickerHostAdapter filePickerHost();
        int getActionBarHeightPx();
        // New: doc view orchestration hooks
        void hideDashboard();
        android.view.ViewGroup ensureDocumentContainer();
        void createDocViewIfNeeded();
        void attachDocViewToContainer(android.view.ViewGroup container);
        void onDocViewAttached();
        void ensureDocAdapter();
        void restoreViewportIfAny();
        void restoreDocViewStateIfAny();
        void syncDocViewPreferences();
        /** Offer the user a way to re-open the document and grant access when permissions are missing. */
        void promptReopenWithPermission(Uri failedUri);
    }

    private final Host host;

    public DocumentSetupController(@NonNull Host host,
                                   @NonNull SearchService searchService,
                                   @NonNull PreferencesCoordinator preferencesCoordinator,
                                   @NonNull ReflowPrefsStore reflowPrefsStore) {
        this.host = host;
        this.searchService = searchService;
        this.preferencesCoordinator = preferencesCoordinator;
        this.reflowPrefsStore = reflowPrefsStore;
    }

    public void setupCore(Context context, Uri intentUri) {
        if (host.getCore() != null) return;
        try {
            AppLog.i(TAG, "setupCore start uri=" + intentUri + " scheme=" + (intentUri != null ? intentUri.getScheme() : "null"));
        } catch (Throwable ignore) {}

        if (isLikelyEpub(context, intentUri) && EpubEncryptionDetector.isProbablyDrmOrEncryptedEpub(context, intentUri)) {
            Log.w(TAG, "DRM/encrypted EPUB detected; refusing to open uri=" + intentUri);
            try { AppLog.w(TAG, "DRM/encrypted EPUB detected; refusing to open"); } catch (Throwable ignore) {}
            showEpubDrmDialog(context);
            host.setCoreInstance(null);
            return;
        }

        OpenDroidPDFCore newCore = null;
        try {
            newCore = new OpenDroidPDFCore(context, intentUri);
            if (newCore == null) throw new Exception(context.getResources().getString(R.string.unable_to_interpret_uri) + " " + intentUri);
        } catch (SecurityException se) {
            Log.e(TAG, "Permission denied opening uri=" + intentUri, se);
            try { AppLog.e(TAG, "Permission denied opening uri=" + intentUri, se); } catch (Throwable ignore) {}
            showPermissionDialog(context, intentUri, se);
            newCore = null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to open document uri=" + intentUri, e);
            try { AppLog.e(TAG, "Failed to open document uri=" + intentUri, e); } catch (Throwable ignore) {}
            showGenericOpenError(context, e);
            newCore = null;
        }

        host.setCoreInstance(newCore);
        OpenDroidPDFCore core = host.getCore();
        if (core == null) {
            host.showInfo(context.getString(R.string.cannot_open_document_permission_hint));
            return;
        }

        // Compute and store a stable, content-derived doc id as early as possible so reflow prefs
        // and sidecar persistence survive rename/move.
        if (core.getUri() != null) {
            try {
                DocumentIdentity ident = DocumentIdentityResolver.resolve(context, core.getUri());
                host.setCurrentDocumentIdentity(ident);
                maybeMigrateReflowPrefsIfNeeded(ident);
            } catch (Throwable ignore) {
            }
        }

        // For reflowable formats (EPUB/HTML), apply a baseline layout before callers ask for page
        // count/sizes so adapters see the correct pagination.
        maybeApplyBaselineReflowLayout(context, core);

        if (core.needsPassword()) host.requestPassword();
        if (core.countPages() == 0) {
            host.showInfo(context.getString(R.string.cannot_open_document_permission_hint));
            host.setCoreInstance(null);
            return;
        }
        try {
            int pages = core.countPages();
            PointF sz = core.getPageSize(0);
            Log.i(TAG, "Opened document uri=" + core.getUri()
                    + " format=" + core.fileFormat()
                    + " pages=" + pages
                    + " page0=" + (sz != null ? sz.x + "x" + sz.y : "null"));
            try {
                AppLog.i(TAG, "Opened document format=" + core.fileFormat() + " pages=" + pages
                        + " page0=" + (sz != null ? sz.x + "x" + sz.y : "null"));
            } catch (Throwable ignore) {}
        } catch (Throwable t) {
            Log.w(TAG, "Failed to log core metadata", t);
            try { AppLog.w(TAG, "Failed to log core metadata: " + t); } catch (Throwable ignore) {}
        }

        // Apply current preferences (pen + annotation colors) to the newly created core.
        preferencesCoordinator.applyToCore(core);
    }

    private void maybeApplyBaselineReflowLayout(Context context, OpenDroidPDFCore core) {
        if (context == null || core == null) return;
        try {
            DocumentType type = DocumentType.fromFileFormat(core.fileFormat());
            if (type != DocumentType.EPUB) return;

            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int actionBarPx = host.getActionBarHeightPx();
            int widthPx = dm != null ? dm.widthPixels : 0;
            int heightPx = dm != null ? dm.heightPixels : 0;
            if (widthPx <= 0 || heightPx <= 0) return;

            int usableHeightPx = Math.max(1, heightPx - Math.max(0, actionBarPx));
            float densityDpi = dm.densityDpi > 0 ? dm.densityDpi : 160f;
            float pageW = widthPx * 72f / densityDpi;
            float pageH = usableHeightPx * 72f / densityDpi;

            String docId = null;
            DocumentIdentity ident = host.currentDocumentIdentityOrNull();
            if (ident != null) docId = ident.docId();
            if (docId == null && core.getUri() != null) docId = DocumentIds.fromUri(core.getUri());

            ReflowPrefsSnapshot prefs =
                    (docId != null ? reflowPrefsStore.load(docId) : ReflowPrefsSnapshot.defaults());
            float em = prefs.fontDp * 72f / 160f;

            // Apply user CSS before layout so margins/line-spacing take effect during pagination.
            String css = org.opendroidpdf.app.reflow.ReflowCss.compose(prefs, em);
            core.setUserCss(css);

            boolean ok = core.layoutDocument(pageW, pageH, em);
            Log.i(TAG, "Baseline reflow layout applied type=" + type
                    + " w=" + pageW + " h=" + pageH + " em=" + em + " ok=" + ok
                    + " theme=" + prefs.theme);
        } catch (Throwable t) {
            Log.w(TAG, "Baseline reflow layout failed", t);
        }
    }

    public void setupSearchSession(final MuPDFReaderView docView) {
        org.opendroidpdf.core.SearchController searchController = host.getSearchController();
        if (searchController == null) {
            searchService.clearDocument();
            return;
        }
        org.opendroidpdf.OpenDroidPDFCore core = host.getCore();
        if (core == null || core.getUri() == null) {
            searchService.clearDocument();
            return;
        }
        String docId = null;
        DocumentIdentity ident = host.currentDocumentIdentityOrNull();
        if (ident != null) docId = ident.docId();
        if (docId == null) docId = DocumentIds.fromUri(core.getUri());
        SearchDocumentView searchDoc = new org.opendroidpdf.app.hosts.SearchDocumentHostAdapter(docView);
        searchService.bindDocument(docId, searchController, searchDoc);
    }

    public void setupDocView() {
        OpenDroidPDFCore core = host.getCore();
        if (core == null) {
            Log.i(TAG, "setupDocView(): core is null, aborting setup");
            return;
        }
        host.hideDashboard();
        android.view.ViewGroup container = host.ensureDocumentContainer();
        host.createDocViewIfNeeded();
        if (host.getDocView() == null) {
            host.showInfo(host.getContext().getString(R.string.cannot_open_document));
            return;
        }
        Log.i(TAG, "setupDocView(): docView=" + host.getDocView() + " container=" + container);
        // Ensure content appears below the toolbar when fully zoomed out
        MuPDFReaderView docView = host.getDocView();
        int topPadding = host.getActionBarHeightPx();
        if (docView != null && topPadding > 0) {
            docView.setPadding(0, topPadding, 0, 0);
            docView.setClipToPadding(false);
        }
        host.attachDocViewToContainer(container);
        host.onDocViewReady();
        host.ensureDocAdapter();
        host.restoreViewportIfAny();
        host.restoreDocViewStateIfAny();
        host.syncDocViewPreferences();
        host.setTitle();
        host.onDocViewAttached();
        // Bind search once the docView is ready
        setupSearchSession(host.getDocView());
    }

    private void maybeMigrateReflowPrefsIfNeeded(@NonNull DocumentIdentity ident) {
        try {
            if (ident.docId().equals(ident.legacyDocId())) return;
            if (reflowPrefsStore == null) return;

            // Only relevant for reflowable documents.
            OpenDroidPDFCore core = host.getCore();
            if (core == null) return;
            if (DocumentType.fromFileFormat(core.fileFormat()) != DocumentType.EPUB) return;

            boolean hasNew = reflowPrefsStore.hasPrefs(ident.docId());
            boolean hasOld = reflowPrefsStore.hasPrefs(ident.legacyDocId());
            if (!hasNew && hasOld) {
                ReflowPrefsSnapshot legacy = reflowPrefsStore.load(ident.legacyDocId());
                reflowPrefsStore.save(ident.docId(), legacy);
            }

            ReflowAnnotatedLayout annotatedNew = reflowPrefsStore.loadAnnotatedLayoutOrNull(ident.docId());
            if (annotatedNew == null) {
                ReflowAnnotatedLayout annotatedOld = reflowPrefsStore.loadAnnotatedLayoutOrNull(ident.legacyDocId());
                if (annotatedOld != null) {
                    reflowPrefsStore.saveAnnotatedLayout(ident.docId(), annotatedOld);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private boolean isLikelyEpub(Context context, Uri uri) {
        if (context == null || uri == null) return false;
        try {
            String mime = context.getContentResolver().getType(uri);
            if (mime != null && mime.toLowerCase(java.util.Locale.US).contains("epub")) return true;
        } catch (Throwable ignore) {
        }

        try {
            String path = uri.getPath();
            if (path != null && path.toLowerCase(java.util.Locale.US).endsWith(".epub")) return true;
        } catch (Throwable ignore) {
        }

        try {
            android.database.Cursor c = context.getContentResolver().query(
                    uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        String name = c.getString(0);
                        if (name != null && name.toLowerCase(java.util.Locale.US).endsWith(".epub")) return true;
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Throwable ignore) {
        }

        return false;
    }

    private void showEpubDrmDialog(Context context) {
        AlertDialog alert = host.alertBuilder().create();
        alert.setTitle(R.string.cannot_open_document);
        alert.setMessage(context.getString(R.string.epub_drm_not_supported));
        alert.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.dismiss), (d, w) -> {});
        alert.show();
    }

    private void showGenericOpenError(Context context, Exception e) {
        AlertDialog alert = host.alertBuilder().create();
        alert.setTitle(R.string.cannot_open_document);
        alert.setMessage(context.getResources().getString(R.string.reason) + ": " + e.toString());
        alert.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.dismiss), (d, w) -> {});
        alert.show();
    }

    private void showPermissionDialog(Context context, Uri uri, Exception e) {
        AlertDialog alert = host.alertBuilder().create();
        alert.setTitle(R.string.cannot_open_document);
        alert.setMessage(context.getString(R.string.cannot_open_document_permission_hint));
        alert.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.grant_access), (d, w) -> host.promptReopenWithPermission(uri));
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, context.getString(R.string.dismiss), (d, w) -> {});
        alert.show();
    }
}
