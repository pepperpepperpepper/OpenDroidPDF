package org.opendroidpdf.app.document;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.core.SearchController;
import org.opendroidpdf.SearchResult;
import org.opendroidpdf.SearchTaskManager;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.PageView;

/**
 * Handles core initialization, docView setup, and search task setup to slim the activity.
 */
public class DocumentSetupController {

    private static final String TAG = "DocumentSetupController";

    private SearchTaskManager searchTaskManager;

    public interface Host {
        OpenDroidPDFCore getCore();
        void setCoreInstance(OpenDroidPDFCore core);
        AlertDialog.Builder alertBuilder();
        SharedPreferences getSharedPreferences(String name, int mode);
        void requestPassword();
        SearchController getSearchController();
        MuPDFReaderView getDocView();
        default void onSearchTaskReady(SearchTaskManager mgr) {}
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
    }

    private final Host host;

    public DocumentSetupController(@NonNull Host host) {
        this.host = host;
    }

    public void setupCore(Context context, Uri intentUri) {
        if (host.getCore() != null) return;

        OpenDroidPDFCore newCore = null;
        try {
            newCore = new OpenDroidPDFCore(context, intentUri);
            if (newCore == null) throw new Exception(context.getResources().getString(R.string.unable_to_interpret_uri) + " " + intentUri);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open document uri=" + intentUri, e);
            AlertDialog alert = host.alertBuilder().create();
            alert.setTitle(R.string.cannot_open_document);
            alert.setMessage(context.getResources().getString(R.string.reason) + ": " + e.toString());
            alert.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(R.string.dismiss), (d, w) -> {});
            alert.show();
            newCore = null;
        }

        host.setCoreInstance(newCore);
        OpenDroidPDFCore core = host.getCore();
        if (core == null) return;

        if (core.needsPassword()) host.requestPassword();
        if (core.countPages() == 0) {
            host.setCoreInstance(null);
            return;
        }

        SharedPreferences prefs = host.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        core.onSharedPreferenceChanged(prefs, "");
    }

    public void setupSearchTaskManager(final MuPDFReaderView docView) {
        SearchController searchController = host.getSearchController();
        if (searchController == null) {
            searchTaskManager = null;
            return;
        }

        SearchTaskManager mgr = new SearchTaskManager((Context) docView.getContext(), searchController) {
            @Override
            protected void onTextFound(SearchResult result) {
                // Debug aid: surface search callbacks to logcat for emulator smoke checks
                try {
                    Log.d(TAG, "search:onTextFound page=" + result.getPageNumber() +
                            " hits=" + (result.getSearchBoxes() == null ? 0 : result.getSearchBoxes().length));
                } catch (Throwable ignore) {}
                docView.addSearchResult(result);
            }

            @Override
            protected void goToResult(SearchResult result) {
                try {
                    Log.d(TAG, "search:goToResult page=" + result.getPageNumber() +
                            " focus=" + result.getFocusedSearchBox());
                } catch (Throwable ignore) {}
                docView.resetupChildren();
                if (docView.getSelectedItemPosition() != result.getPageNumber())
                    docView.setDisplayedViewIndex(result.getPageNumber());
                if (result.getFocusedSearchBox() != null) {
                    docView.doNextScrollWithCenter();
                    docView.setDocRelXScroll(result.getFocusedSearchBox().left);
                    docView.setDocRelYScroll(result.getFocusedSearchBox().top);
                }
            }
        };
        searchTaskManager = mgr;
        host.onSearchTaskReady(mgr);
    }

    public SearchTaskManager getSearchTaskManager() {
        return searchTaskManager;
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
    }
}
