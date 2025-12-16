package org.opendroidpdf.app.document;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.graphics.PointF;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.PageView;
import org.opendroidpdf.app.services.SearchService;

/**
 * Handles core initialization, docView setup, and search task setup to slim the activity.
 */
public class DocumentSetupController {

    private static final String TAG = "DocumentSetupController";

    private final SearchService searchService;

    public interface Host {
        OpenDroidPDFCore getCore();
        void setCoreInstance(OpenDroidPDFCore core);
        AlertDialog.Builder alertBuilder();
        SharedPreferences getSharedPreferences(String name, int mode);
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

    public DocumentSetupController(@NonNull Host host, @NonNull SearchService searchService) {
        this.host = host;
        this.searchService = searchService;
    }

    public void setupCore(Context context, Uri intentUri) {
        if (host.getCore() != null) return;

        OpenDroidPDFCore newCore = null;
        try {
            newCore = new OpenDroidPDFCore(context, intentUri);
            if (newCore == null) throw new Exception(context.getResources().getString(R.string.unable_to_interpret_uri) + " " + intentUri);
        } catch (SecurityException se) {
            Log.e(TAG, "Permission denied opening uri=" + intentUri, se);
            showPermissionDialog(context, intentUri, se);
            newCore = null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to open document uri=" + intentUri, e);
            showGenericOpenError(context, e);
            newCore = null;
        }

        host.setCoreInstance(newCore);
        OpenDroidPDFCore core = host.getCore();
        if (core == null) {
            host.showInfo(context.getString(R.string.cannot_open_document_permission_hint));
            return;
        }

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
        } catch (Throwable t) {
            Log.w(TAG, "Failed to log core metadata", t);
        }

        SharedPreferences prefs = host.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        core.onSharedPreferenceChanged(prefs, "");
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
        String docId = core.getUri().toString();
        searchService.bindDocument(docId, searchController, docView);
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
