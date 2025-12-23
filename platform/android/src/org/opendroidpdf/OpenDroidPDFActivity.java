package org.opendroidpdf;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;
import android.graphics.PointF;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.activity.OnBackPressedCallback;

import org.opendroidpdf.app.DashboardFragment;
import org.opendroidpdf.app.DocumentHostFragment;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.document.DocumentHostController;
import org.opendroidpdf.app.document.DocumentNavigationController;
	import org.opendroidpdf.app.document.DocumentSetupController;
	import org.opendroidpdf.app.document.CoreInstanceCoordinator;
	import org.opendroidpdf.app.document.DocumentLifecycleManager;
	import org.opendroidpdf.app.document.DocumentToolbarController;
	import org.opendroidpdf.app.document.DocumentType;
	import org.opendroidpdf.app.document.DocumentIdentity;
	import org.opendroidpdf.app.document.DocumentIdentityResolver;
	import org.opendroidpdf.app.document.RecentFilesController;
	import org.opendroidpdf.app.document.SaveUiDelegate;
import org.opendroidpdf.app.notes.NotesController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.helpers.IntentRouter;
import org.opendroidpdf.app.helpers.IntentResumeDelegate;
	import org.opendroidpdf.app.helpers.UriPermissionHelper;
	import org.opendroidpdf.app.helpers.StoragePermissionController;
	import org.opendroidpdf.app.search.SearchToolbarController;
	import org.opendroidpdf.app.annotation.PenSettingsController;
	import org.opendroidpdf.app.toolbar.ToolbarStateController;
import org.opendroidpdf.app.lifecycle.LifecycleHooks;
import org.opendroidpdf.app.lifecycle.ActivityComposition;
import org.opendroidpdf.app.lifecycle.ActivityLifecycleHostAdapter;
import org.opendroidpdf.app.lifecycle.ActivityFacade;
import org.opendroidpdf.app.ui.ActionBarHost;
import org.opendroidpdf.core.AlertController;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.app.ui.ActionBarMode;
import org.opendroidpdf.app.ui.ActionBarModeDelegate;
import org.opendroidpdf.app.ui.UiStateDelegate;
import org.opendroidpdf.app.ui.KeyboardHostAdapter;
import org.opendroidpdf.app.ui.TitleHostAdapter;
import org.opendroidpdf.core.SearchController;
import org.opendroidpdf.app.dashboard.DashboardController;
import org.opendroidpdf.app.navigation.NavigationController;
import org.opendroidpdf.app.navigation.NavigationDelegate;
import org.opendroidpdf.app.navigation.BackPressController;
import org.opendroidpdf.app.navigation.BackPressHostAdapter;
import org.opendroidpdf.app.navigation.DashboardDelegate;
import org.opendroidpdf.app.navigation.LinkBackDelegate;
import org.opendroidpdf.app.navigation.LinkBackHelper;
import org.opendroidpdf.app.hosts.TempUriPermissionHostAdapter;
import org.opendroidpdf.app.debug.DebugDelegate;
import java.util.concurrent.Callable;

public class OpenDroidPDFActivity extends AppCompatActivity implements TemporaryUriPermission.TemporaryUriPermissionProvider, PenSettingsController.Host, DashboardFragment.DashboardHost, org.opendroidpdf.app.lifecycle.ActivityCompositionOwner {
    private static final String TAG = "OpenDroidPDFActivity";

    private ActivityComposition.Composition comp;
    private AppServices appServices;
    private OnBackPressedCallback backPressedCallback;
    public org.opendroidpdf.app.hosts.FilePickerHostAdapter getFilePickerHost() { return comp != null ? comp.filePickerHostAdapter : null; }

    private org.opendroidpdf.app.helpers.StoragePermissionController storagePermissionController() {
        return comp != null ? comp.storagePermissionController : null;
    }

    public boolean ensureStoragePermission(Intent intent) {
        org.opendroidpdf.app.helpers.StoragePermissionController pc = storagePermissionController();
        if (pc == null) return false;
        return pc.ensureForIntent(this, intent);
    }

    public boolean hasCore() { return facade != null && facade.hasCore(); }

    /**
     * Returns the current document type as reported by MuPDF.
     */
    public DocumentType currentDocumentType() {
        OpenDroidPDFCore core = getCore();
        if (core == null) return DocumentType.OTHER;
        String format = null;
        try {
            format = core.fileFormat();
        } catch (Throwable ignore) {
        }
        return DocumentType.fromFileFormat(format);
    }

    /**
     * Whether the currently open document is a PDF document.
     * Non-PDF formats (e.g. EPUB) will use explicit export flows.
     */
    public boolean isPdfDocument() {
        return currentDocumentType() == DocumentType.PDF;
    }

    public boolean isEpubDocument() {
        return currentDocumentType() == DocumentType.EPUB;
    }

    private MuPDFPageView currentPageView() {
        if (mDocView == null) {
            return null;
        }
        try {
            MuPDFView v = (MuPDFView) mDocView.getSelectedView();
            if (v instanceof MuPDFPageView) {
                return (MuPDFPageView) v;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }
    public MuPDFPageView currentPageViewPublic() { return currentPageView(); }
    
    private CoreInstanceCoordinator coreCoordinator;
    private MuPDFReaderView mDocView;
    private DocumentLifecycleManager documentLifecycleManager;
    private org.opendroidpdf.app.ui.UiStateManager uiStateManager;
    private org.opendroidpdf.app.ui.AlertUiManager alertUiManager;
    private ActivityFacade facade;
    public ActivityComposition.Composition getComposition() { return comp; }
    private final ActionBarModeDelegate actionBarModeDelegate = new ActionBarModeDelegate();
    private org.opendroidpdf.app.annotation.AnnotationModeStore annotationModeStore;
    private AlertDialog.Builder mAlertBuilder;
	    private IntentResumeDelegate intentResumeDelegate;
	    private LifecycleHooks lifecycleHooks;
        private org.opendroidpdf.app.preferences.PreferencesSubscription preferencesSubscription;

    public void setCoreInstance(OpenDroidPDFCore newCore) {
        if (documentLifecycleManager != null) documentLifecycleManager.setCoreInstance(newCore);
    }

    public void destroyCoreNow() {
        if (documentLifecycleManager != null) documentLifecycleManager.destroyCoreNow();
    }

    // Alert host moved to org.opendroidpdf.app.hosts.AlertHostAdapter

    private boolean hasRepository() { return facade != null && facade.hasRepository(); }

    // Exposed for host adapters/controllers
    public org.opendroidpdf.core.MuPdfRepository getRepository() { return facade != null ? facade.repository() : null; }

    @Nullable
    public Uri currentDocumentUriOrNull() { return facade != null ? facade.currentDocumentUri() : null; }

    @Nullable
    public DocumentIdentity currentDocumentIdentityOrNull() {
        return documentLifecycleManager != null
                ? documentLifecycleManager.currentDocumentIdentityOrNull()
                : null;
    }

    @Nullable
    public String currentDocIdOrNull() {
        DocumentIdentity ident = currentDocumentIdentityOrNull();
        return ident != null ? ident.docId() : null;
    }

    @Nullable
    public String currentLegacyDocIdOrNull() {
        DocumentIdentity ident = currentDocumentIdentityOrNull();
        return ident != null ? ident.legacyDocId() : null;
    }

    public void setCurrentDocumentIdentity(@Nullable DocumentIdentity identity) {
        if (documentLifecycleManager != null) documentLifecycleManager.setCurrentDocumentIdentity(identity);
    }

    @NonNull
    public String currentDocumentNameOrAppName() {
        return facade != null ? facade.currentDocumentName() : getString(R.string.app_name);
    }

    @NonNull
    public org.opendroidpdf.app.document.DocumentState currentDocumentState() {
        if (documentLifecycleManager != null) return documentLifecycleManager.documentState();
        return org.opendroidpdf.app.document.DocumentState.empty(getString(R.string.app_name));
    }

    public boolean canSaveToCurrentUri() { return facade != null && facade.canSaveToCurrentUri(); }

    public boolean hasUnsavedChanges() { return facade != null && facade.hasUnsavedChanges(); }

    /** Disables "Save to current URI" after a failed save attempt (e.g., revoked permissions). */
    public void markSaveToCurrentUriFailureOverride() {
        if (documentLifecycleManager == null) return;
        if (documentLifecycleManager.markSaveToCurrentUriFailureOverride()) invalidateOptionsMenuSafely();
    }

    /** Clears the transient save failure override (e.g., after Save As or re-open). */
    public void clearSaveToCurrentUriFailureOverride() {
        if (documentLifecycleManager == null) return;
        if (documentLifecycleManager.clearSaveToCurrentUriFailureOverride()) invalidateOptionsMenuSafely();
    }

    /** Refreshes cached save-capability state after URI/permission changes (e.g., Save As). */
    public void refreshSaveCapabilityCache() {
        if (documentLifecycleManager != null) documentLifecycleManager.refreshSaveCapabilityCache();
        invalidateOptionsMenuSafely();
    }
    

    public void createAlertWaiter() {
        destroyAlertWaiter();
        if (coreCoordinator != null) coreCoordinator.startAlertWaiter();
    }

    // MuPDF alert UI handled by AlertDialogHelper

    public void destroyAlertWaiter() {
        if (coreCoordinator != null) coreCoordinator.stopAlertWaiter();
    }

    
    @Override
    public void onCreate(Bundle savedInstanceState)
        {
                //Treat the bundle with the SaveInstanceStateManager before calling through to super
            savedInstanceState = SaveInstanceStateManager.recoverBundleIfNecessary(savedInstanceState, getClass().getClassLoader());
            
            super.onCreate(savedInstanceState);

            coreCoordinator = new CoreInstanceCoordinator(this);

			//Initialize the layout
        setContentView(R.layout.main);
        Toolbar myToolbar = (Toolbar)findViewById(R.id.toolbar);
            setSupportActionBar(myToolbar);
            comp = ActivityComposition.setup(this);
            appServices = comp.appServices;
            intentResumeDelegate = comp.intentResumeDelegate;
            documentLifecycleManager = new DocumentLifecycleManager(this, coreCoordinator, comp);
            backPressedCallback = new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    boolean consumed = comp != null && comp.backPressController != null && comp.backPressController.onBackPressed();
                    if (!consumed) {
                        setEnabled(false); // avoid re-entry
                        OpenDroidPDFActivity.super.onBackPressed();
                        setEnabled(true);
                    }
                }
            };
            getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
            uiStateManager = new org.opendroidpdf.app.ui.UiStateManager(this, comp);
            alertUiManager = new org.opendroidpdf.app.ui.AlertUiManager(this, comp);
	            facade = new ActivityFacade(this, documentLifecycleManager, uiStateManager, alertUiManager);
			
	                // Preferences, alert builder, non-config core, and debug hooks
	            preferencesSubscription = org.opendroidpdf.app.lifecycle.StartupBootstrap.bootstrap(this, comp.preferencesCoordinator);
	            
	            org.opendroidpdf.app.lifecycle.SavedStateHelper.restore(this, savedInstanceState);
	        }
    
    @Override
    protected void onResume()
        {
            super.onResume();
            if (intentResumeDelegate != null) intentResumeDelegate.onResume(getIntent());
        }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intentResumeDelegate != null) intentResumeDelegate.onNewIntent(intent);
    }

    public void resetDocumentStateForIntent() {
        if (documentLifecycleManager != null) documentLifecycleManager.resetDocumentStateForIntent();
    }

    public boolean isUriInAppPrivateStorage(Uri uri) { return org.opendroidpdf.app.util.PathUtils.isUriInAppPrivateStorage(this, uri); }

    public void openDocumentFromIntent(Intent intent) {
        Log.i(TAG, "openDocumentFromIntent(): data=" + intent.getData() + " type=" + intent.getType());
        if (comp != null && comp.navigationDelegate != null) {
            comp.navigationDelegate.openDocumentFromIntent(intent);
        } else if (comp != null && comp.documentNavigationController != null) {
            comp.documentNavigationController.openDocumentFromIntent(intent);
        }
    }

    public void runAutotestIfNeeded(final Intent intent) { if (comp != null) comp.debugDelegate.runAutotestIfNeeded(this, mDocView, getRepository(), intent); }
    public boolean isAutoTestRanFlag() { return comp != null && comp.debugDelegate.isAutoTestRan(); }
    public void markAutoTestRanFlag() { if (comp != null) comp.debugDelegate.markAutoTestRan(); }

    @Override
    protected void onPause() {
        super.onPause();
        if (intentResumeDelegate != null) intentResumeDelegate.onPause();
        ensureLifecycleHooks();
        lifecycleHooks.onPause();
    }
    

    @Override
    protected void onStop() {
        super.onStop();
        ensureLifecycleHooks();
        lifecycleHooks.onStop();
    }
    
    
	    @Override
	    protected void onDestroy() {//There is no guarantee that this is ever called!!!
	        super.onDestroy();
            if (preferencesSubscription != null) {
                preferencesSubscription.stop();
                preferencesSubscription = null;
            }
	        ensureLifecycleHooks();
	        lifecycleHooks.onDestroy();
	        destroyAlertWaiter();
        if (coreCoordinator != null && coreCoordinator.getAlertController() != null) {
            coreCoordinator.getAlertController().shutdown();
        }
        if (comp != null && comp.saveUiDelegate != null) comp.saveUiDelegate.cancelActiveSaveJob();
        if (comp != null && comp.searchToolbarController != null) { comp.searchToolbarController.detach(); }
    }

    private void ensureLifecycleHooks() {
        if (lifecycleHooks != null) return;
        lifecycleHooks = new LifecycleHooks(new ActivityLifecycleHostAdapter(
                this,
                comp != null ? comp.saveFlagController : null,
                comp != null ? comp.saveUiDelegate : null));
    }

    public void setIgnoreSaveFlagsForFinish() { if (comp != null && comp.saveFlagController != null) comp.saveFlagController.setIgnoreSaveFlagsForFinish(); }
    public SearchToolbarController getSearchToolbarController() { return comp != null ? comp.searchToolbarController : null; }
    // Removed one-line public wrappers; corresponding methods are now public
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
        {
            super.onCreateOptionsMenu(menu);
            if (comp != null && comp.optionsMenuController != null) return comp.optionsMenuController.onCreateOptionsMenu(menu);
            return false;
        }

    public boolean isCurrentNoteDocument() {
        return comp != null && comp.notesDelegate != null && comp.notesDelegate.isCurrentNoteDocument(getIntent());
    }

    public boolean hasDocumentLoaded() { return hasRepository(); }

    public boolean isLinkBackAvailable() { return comp != null && comp.linkBackHelper != null && comp.linkBackHelper.isAvailable(); }

	    @Override
	    public void onPenPreferenceChanged(String key) {
	        if (comp != null && comp.preferencesCoordinator != null) {
	            comp.preferencesCoordinator.refreshAndApply();
	        }
	    }

    @Override
    public Context getContext() {
        return this;
    }

    public androidx.appcompat.app.AppCompatActivity getActivity() {
        return this;
    }

    public boolean isSelectedAnnotationEditable() {
        MuPDFPageView pageView = currentPageView();
        if (pageView == null) return false;
        try {
            return pageView.selectedAnnotationIsEditable();
        } catch (Throwable ignore) {
            return false;
        }
    }

    public org.opendroidpdf.PageView getSelectedPageView() {
        if (mDocView == null) return null;
        android.view.View sel = mDocView.getSelectedView();
        return (sel instanceof org.opendroidpdf.PageView) ? (org.opendroidpdf.PageView) sel : null;
    }

    public boolean isDrawingModeActive() { return mDocView != null && mDocView.getMode() == MuPDFReaderView.Mode.Drawing; }
    public boolean isErasingModeActive() { return mDocView != null && mDocView.getMode() == MuPDFReaderView.Mode.Erasing; }

    // DashboardFragment.DashboardHost
    @Override public void onOpenDocumentRequested() { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onOpenDocumentRequested(); }
    @Override public void onCreateNewDocumentRequested() { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onCreateNewDocumentRequested(); }
    @Override public void onOpenSettingsRequested() { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onOpenSettingsRequested(); }
    @Override public void onRecentEntryRequested(final org.opendroidpdf.app.services.recent.RecentEntry entry) { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onRecentEntryRequested(entry); }

    @Override
    public boolean isMemoryLow() {
        if (comp != null && comp.dashboardHostAdapter != null) return comp.dashboardHostAdapter.isMemoryLow();
        return comp != null && comp.uiStateDelegate != null && comp.uiStateDelegate.isMemoryLow();
    }

    @Override
    public int maxRecentFiles() {
        return comp != null && comp.saveFlagController != null
                ? comp.saveFlagController.maxRecentFiles()
                : 20;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { //Handle clicks in the options menu
        if (comp != null && comp.optionsMenuController != null && comp.optionsMenuController.onOptionsItemSelected(item)) return true;
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (comp != null && comp.optionsMenuController != null) {
            return comp.optionsMenuController.onPrepareOptionsMenu(menu, () -> super.onPrepareOptionsMenu(menu));
        }
        return super.onPrepareOptionsMenu(menu);
    }

    public void invalidateOptionsMenuSafely() { if (comp != null && comp.optionsMenuController != null) comp.optionsMenuController.invalidateOptionsMenuSafely(); }

    public boolean isPreparingOptionsMenu() { return facade != null && facade.isPreparingOptionsMenu(); }

	public void tryToTakePersistablePermissions(Intent intent) {
        Uri uri = intent.getData();
        UriPermissionHelper.tryTakePersistablePermissions(this, uri);
	}
	

    public void setupCore() { // Called during onResume()
        if (documentLifecycleManager != null) documentLifecycleManager.setupCore(getIntent());
    }
    
        
    public void setupSearchSession() { // Called once docView is ready
        if (documentLifecycleManager != null) documentLifecycleManager.setupSearchSession(mDocView);
    }

    public OpenDroidPDFCore getCore() { return coreCoordinator != null ? coreCoordinator.getCore() : null; }
    public AlertDialog.Builder alertBuilder() { return mAlertBuilder; }
    public SearchController getSearchController() { return coreCoordinator != null ? coreCoordinator.getSearchController() : null; }
    public MuPDFReaderView getDocView() { return mDocView; }

    // Expose recent files controller for adapters/controllers
    public org.opendroidpdf.app.services.RecentFilesService getRecentFilesService() {
        return coreCoordinator != null ? coreCoordinator.getRecentFilesController() : null;
    }
    public org.opendroidpdf.app.document.RecentFilesController getRecentFilesController() {
        return coreCoordinator != null ? coreCoordinator.getRecentFilesController() : null;
    }
    public org.opendroidpdf.app.notes.NotesController getNotesController() { return comp != null ? comp.notesController : null; }
    public org.opendroidpdf.app.document.DocumentNavigationController getDocumentNavigationController() { return comp != null ? comp.documentNavigationController : null; }
    public org.opendroidpdf.app.navigation.DashboardDelegate getDashboardDelegate() { return comp != null ? comp.dashboardDelegate : null; }
    public org.opendroidpdf.app.document.DocumentViewDelegate getDocumentViewDelegate() { return comp != null ? comp.documentViewDelegate : null; }
    public org.opendroidpdf.core.MuPdfController getMuPdfController() { return coreCoordinator != null ? coreCoordinator.getMuPdfController() : null; }
    public void setDocView(MuPDFReaderView docView) {
        this.mDocView = docView;
        if (docView != null && annotationModeStore != null) {
            docView.setAnnotationModeStore(annotationModeStore);
        }
    }
    public ActionBarModeDelegate getActionBarModeDelegate() { return actionBarModeDelegate; }
    public UiStateDelegate getUiStateDelegate() { return comp != null ? comp.uiStateDelegate : null; }

    public void setupDocView() { if (comp != null && comp.documentSetupController != null) comp.documentSetupController.setupDocView(); }

    // Dashboard wrappers for controllers/routers
    public boolean dashboardIsShown() { return comp != null && comp.dashboardDelegate != null && comp.dashboardDelegate.dashboardIsShown(); }
    public void hideDashboard() { if (comp != null && comp.dashboardDelegate != null) comp.dashboardDelegate.hideDashboard(); }

    // Attach the document view to the fragment container
    public void attachDocViewToContainer(android.view.ViewGroup container) { if (comp != null && comp.dashboardDelegate != null) comp.dashboardDelegate.attachDocViewToContainer(container); }

    // Create a new blank note and open it
    public void openNewDocument(final String filename) throws java.io.IOException {
        if (comp != null && comp.notesDelegate != null) comp.notesDelegate.openNewDocument(filename);
    }

    // Simple entry to document picker (delegates to controller)
    public void openDocument() {
        if (comp != null && comp.navigationDelegate != null) comp.navigationDelegate.openDocument();
    }

    // Helpers for DocViewFactory to adjust action bar state without exposing internals
    public org.opendroidpdf.app.ui.ActionBarMode getActionBarMode() { return actionBarModeDelegate.current(); }
    public org.opendroidpdf.app.annotation.PenSettingsController getPenSettingsController() { return comp != null ? comp.penSettingsController : null; }
    public boolean isActionBarModeEdit() { return actionBarModeDelegate.isEdit(); }
    public boolean isActionBarModeAddingTextAnnot() { return actionBarModeDelegate.isAddingTextAnnot(); }
    public boolean isActionBarModeSearchOrHidden() { return actionBarModeDelegate.isSearchOrHidden(); }
    public org.opendroidpdf.app.annotation.AnnotationModeStore getAnnotationModeStore() { return annotationModeStore; }
    public void setAnnotationModeStore(org.opendroidpdf.app.annotation.AnnotationModeStore store) {
        this.annotationModeStore = store;
        if (mDocView != null && store != null) {
            mDocView.setAnnotationModeStore(store);
        }
    }
    public androidx.appcompat.app.AlertDialog.Builder getAlertBuilder() {
        if (alertUiManager != null) mAlertBuilder = alertUiManager.getAlertBuilder();
        return mAlertBuilder;
    }
    public void setAlertBuilder(androidx.appcompat.app.AlertDialog.Builder b) {
        mAlertBuilder = b;
        if (alertUiManager != null) alertUiManager.setAlertBuilder(b);
    }
    public void rememberPreLinkHitViewport(int page, float scale, float x, float y) { if (comp != null && comp.linkBackHelper != null) comp.linkBackHelper.remember(page, scale, x, y); }

    // For StartupBootstrap: set core from last non-config without reinitializing controllers
    public void setCoreFromLastNonConfig(OpenDroidPDFCore last) {
        if (documentLifecycleManager != null) documentLifecycleManager.setCoreFromLastNonConfig(last);
        // Ensure doc identity is available if callers request it during early restore.
        currentDocumentIdentityOrNull();
    }

    public void setSaveFlags(boolean saveOnStop, boolean saveOnDestroy, int numberRecentFiles) {
        if (comp != null && comp.saveFlagController != null) {
            comp.saveFlagController.setSaveFlags(saveOnStop, saveOnDestroy, numberRecentFiles);
        }
    }

    // Apply restored UI state from SavedStateHelper
    public void applySavedUiState(int pageBefore,
                                  float normScale,
                                  float normX,
                                  float normY,
                                  android.os.Parcelable docViewState,
                                  String latestSearch) {
        if (uiStateManager != null)
            uiStateManager.applySavedUiState(pageBefore, normScale, normX, normY, docViewState, latestSearch);
    }
    
    public void checkSaveThenCall(final Callable callable) {
        if (comp != null && comp.documentNavigationController != null) {
            comp.documentNavigationController.checkSaveThenCall(callable);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {        
        if (comp != null && comp.activityResultRouter != null && comp.activityResultRouter.handle(requestCode, resultCode, intent)) return;
        super.onActivityResult(requestCode, resultCode, intent);
    }

    public void showSaveAsActivity() {
        if (comp != null && comp.navigationDelegate != null) comp.navigationDelegate.showSaveAsActivity();
    }

    private void cancelActiveSaveJob() {
        if (comp != null && comp.saveUiDelegate != null) comp.saveUiDelegate.cancelActiveSaveJob();
    }

    public org.opendroidpdf.app.document.SaveUiDelegate getSaveUiDelegate() { return comp != null ? comp.saveUiDelegate : null; }
    
    // save()/saveAs() moved to SaveUiController
    // Accessor for adapters/controllers
    public org.opendroidpdf.app.document.DocumentViewportController getViewportController() { return comp != null ? comp.viewportController : null; }

    public void recordRecent(Uri uri) { if (comp != null && comp.viewportController != null) comp.viewportController.recordRecent(uri); }

    public void cancelRenderThumbnailJob() { if (comp != null && comp.viewportController != null) comp.viewportController.cancelRenderThumbnailJob(); }
    public void saveViewportAndRecentFiles(Uri uri) { if (comp != null && comp.viewportController != null) comp.viewportController.saveViewportAndRecentFiles(uri); }

    public void stopSearchTasks() {
        if (comp != null && comp.searchService != null) {
            comp.searchService.session().stop();
        }
    }
    

    
    @Override
    public Object onRetainCustomNonConfigurationInstance() { //Called if the app is destroyed for a configuration change
        OpenDroidPDFCore mycore = getCore();
        setCoreInstance(null);
        return mycore;
    }
    
    
    @Override
    protected void onSaveInstanceState(Bundle outState) { //Called when the app is destroyed by the system and in various other cases
        super.onSaveInstanceState(outState);
        org.opendroidpdf.app.lifecycle.SavedStateHelper.save(this, outState);
    }        
    
	    // printDoc/shareDoc now handled by ExportController

    // Flush any currently drawn but not yet committed ink on the active page
    // into the MuPDF core to ensure export/print includes the marks. Also
    // force a page appearance update so that saved/printed PDFs contain
    // baked annotation appearance streams (avoids race with render pipeline).
    public void commitPendingInkToCoreBlocking() {
        if (comp != null && comp.inkCommitHostAdapter != null) {
            comp.inkCommitHostAdapter.commitPendingInkToCoreBlocking();
        }
    }

    @Nullable
    public SidecarAnnotationProvider currentSidecarAnnotationProviderOrNull() {
        MuPDFReaderView doc = getDocView();
        if (doc == null) return null;
        android.widget.Adapter adapter = doc.getAdapter();
        if (adapter instanceof MuPDFPageAdapter) {
            return ((MuPDFPageAdapter) adapter).sidecarSessionOrNull();
        }
        return null;
    }

    // Export/intent/notes hosts moved into app/hosts adapters

    // (Removed) deprecated inner host classes replaced by adapters in app/hosts.

    // Toolbar host moved to app/hosts/ToolbarHostAdapter

    public void showInfo(String message) {
        org.opendroidpdf.app.ui.UiUtils.showInfo(this, message);
    }    

    // Adapter utility for export host
    
    public void requestPassword() {
        OpenDroidPDFCore currentCore = getCore();
        if (comp != null && comp.passwordHostAdapter != null && currentCore != null && mAlertBuilder != null) {
            comp.passwordHostAdapter.requestPassword(currentCore, mAlertBuilder);
        }
    }


    // Go-to-page dialog is invoked directly by DocumentToolbarController.


    
    public void setTitle() { if (comp != null && comp.titleHostAdapter != null) comp.titleHostAdapter.setTitle(); }

    // (Legacy) action-bar animation reset helper removed; fullscreen logic handled by FullscreenHostAdapter callers.

    // Fullscreen host moved to app/hosts/FullscreenHostAdapter

    // saveViewport(uri) kept private; adapters should use getViewportController().saveViewport()

    public ArrayList<TemporaryUriPermission> getTemporaryUriPermissions() {
        if (comp != null && comp.tempUriPermissionHostAdapter != null) {
            return comp.tempUriPermissionHostAdapter.list();
        }
        return new ArrayList<TemporaryUriPermission>();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        org.opendroidpdf.app.helpers.StoragePermissionController pc = storagePermissionController();
        if (pc == null) return;
        if (pc.handleRequestPermissionsResult(
            requestCode,
            grantResults,
            new Runnable() { @Override public void run() { openDocumentFromIntent(getIntent()); } },
            new Runnable() { @Override public void run() { Toast.makeText(OpenDroidPDFActivity.this, R.string.cannot_open_document, Toast.LENGTH_LONG).show(); } })) {
            return;
        }
    }

    // dpToPixel/memory helpers moved to UiUtils
}
