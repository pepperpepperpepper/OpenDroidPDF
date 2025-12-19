package org.opendroidpdf.app.document;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.alert.AlertDialogHelper;
import org.opendroidpdf.app.services.recent.RecentFilesStore;
import org.opendroidpdf.core.AlertController;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.core.SearchController;

/**
 * Owns the MuPDF core instance and derived controllers to keep the activity lean.
 */
public final class CoreInstanceCoordinator {
    private final OpenDroidPDFActivity activity;
    private final RecentFilesStore recentFilesStore;

    @Nullable private OpenDroidPDFCore core;
    @Nullable private MuPdfRepository muPdfRepository;
    @Nullable private MuPdfController muPdfController;
    @Nullable private SearchController searchController;
    @Nullable private AlertController alertController;
    @Nullable private AlertDialogHelper alertDialogHelper;
    @Nullable private RecentFilesController recentFilesController;

    public CoreInstanceCoordinator(OpenDroidPDFActivity activity) {
        this.activity = activity;
        // Keep SharedPreferences plumbing out of coordinators; AppServices owns store construction.
        this.recentFilesStore = AppServices.init(activity.getApplication()).recentFilesStore();
        // Always keep recents available (dashboard needs them even before a document is opened).
        this.recentFilesController = new RecentFilesController(
                activity,
                null,
                null,
                recentFilesStore);
    }

    public void setCoreInstance(@Nullable OpenDroidPDFCore newCore,
                                 @Nullable AppServices appServices,
                                 @Nullable AlertDialog.Builder alertBuilder) {
        stopAlertWaiter();
        if (alertController != null) {
            alertController.shutdown();
            alertController = null;
        }
        core = newCore;
        if (newCore != null) {
            muPdfRepository = appServices != null ? appServices.newRepository(newCore) : new MuPdfRepository(newCore);
            muPdfController = new MuPdfController(muPdfRepository);
            searchController = new SearchController(muPdfRepository);
            alertController = new AlertController(muPdfRepository);
            alertDialogHelper = new AlertDialogHelper(
                    new org.opendroidpdf.app.hosts.AlertHostAdapter(activity, alertBuilder),
                    alertController);
            if (recentFilesController != null) {
                recentFilesController.shutdown();
            }
            recentFilesController = new RecentFilesController(activity, muPdfRepository, muPdfController, recentFilesStore);
        } else {
            muPdfRepository = null;
            muPdfController = null;
            searchController = null;
            alertController = null;
            alertDialogHelper = null;
            if (recentFilesController != null) {
                recentFilesController.shutdown();
            }
            recentFilesController = new RecentFilesController(activity, null, null, recentFilesStore);
        }
    }

    public void destroyCoreNow(@Nullable AppServices appServices, @Nullable AlertDialog.Builder alertBuilder) {
        if (core != null) {
            core.onDestroy();
            setCoreInstance(null, appServices, alertBuilder);
        }
    }

    public void startAlertWaiter() { if (alertDialogHelper != null) alertDialogHelper.start(); }
    public void stopAlertWaiter() { if (alertDialogHelper != null) alertDialogHelper.stop(); }

    public boolean hasCore() { return core != null; }
    @Nullable public OpenDroidPDFCore getCore() { return core; }
    @Nullable public MuPdfRepository getRepository() { return muPdfRepository; }
    @Nullable public MuPdfController getMuPdfController() { return muPdfController; }
    @Nullable public SearchController getSearchController() { return searchController; }
    @Nullable public AlertController getAlertController() { return alertController; }
    @Nullable public AlertDialogHelper getAlertDialogHelper() { return alertDialogHelper; }
    @Nullable public RecentFilesController getRecentFilesController() { return recentFilesController; }

    public boolean hasRepository() { return muPdfRepository != null; }

    @Nullable
    public Uri currentDocumentUri() {
        if (muPdfRepository != null) return muPdfRepository.getDocumentUri();
        return core != null ? core.getUri() : null;
    }

    public String currentDocumentName(Context context) {
        if (muPdfRepository != null) return muPdfRepository.getDocumentName();
        return core != null ? core.getFileName() : context.getString(R.string.app_name);
    }

    public boolean canSaveToCurrentUri(OpenDroidPDFActivity activity) {
        return core != null && core.canSaveToCurrentUri(activity);
    }

    public boolean hasUnsavedChanges() {
        if (muPdfRepository != null) return muPdfRepository.hasUnsavedChanges();
        return core != null && core.hasChanges();
    }
}
