package org.opendroidpdf;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
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

import com.google.android.material.snackbar.Snackbar;

import org.opendroidpdf.app.DashboardFragment;
import org.opendroidpdf.app.DocumentHostFragment;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.document.DocumentHostController;
import org.opendroidpdf.app.document.DocumentNavigationController;
	import org.opendroidpdf.app.document.DocumentSetupController;
	import org.opendroidpdf.app.document.CoreInstanceCoordinator;
	import org.opendroidpdf.app.document.DocumentLifecycleManager;
	import org.opendroidpdf.app.document.DocumentToolbarController;
	import org.opendroidpdf.app.document.DocumentIdentity;
	import org.opendroidpdf.app.document.DocumentIdentityResolver;
	import org.opendroidpdf.app.document.RecentFilesController;
import org.opendroidpdf.app.document.SaveUiDelegate;
import org.opendroidpdf.app.notes.NotesController;
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
import org.opendroidpdf.app.hosts.ActivityLifecycleHostAdapter;
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
import org.opendroidpdf.app.navigation.DashboardDelegate;
import org.opendroidpdf.app.navigation.LinkBackDelegate;
import org.opendroidpdf.app.navigation.LinkBackHelper;
import org.opendroidpdf.app.hosts.TempUriPermissionHostAdapter;
import org.opendroidpdf.app.debug.DebugDelegate;
import org.opendroidpdf.app.diagnostics.CrashReportPrompter;
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
    private boolean pageIndicatorHintShownThisSession = false;
    @Nullable private Runnable postSaveAsAction;

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

    public void setCurrentUserFacingDocument(@Nullable Uri uri, @Nullable String displayName) {
        if (documentLifecycleManager != null) documentLifecycleManager.setCurrentUserFacingDocument(uri, displayName);
    }

    @Nullable
    public Uri currentUserFacingUriOrNull() {
        return documentLifecycleManager != null ? documentLifecycleManager.currentUserFacingUriOrNull() : null;
    }

    @Nullable
    public String currentUserFacingDisplayNameOrNull() {
        return documentLifecycleManager != null ? documentLifecycleManager.currentUserFacingDisplayNameOrNull() : null;
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

    public void setCurrentDocumentOrigin(@NonNull org.opendroidpdf.app.document.DocumentOrigin origin) {
        if (documentLifecycleManager != null) documentLifecycleManager.setCurrentDocumentOrigin(origin);
    }

    @NonNull
    public org.opendroidpdf.app.document.DocumentOrigin currentDocumentOrigin() {
        if (documentLifecycleManager != null) return documentLifecycleManager.currentDocumentOrigin();
        return org.opendroidpdf.app.document.DocumentOrigin.NATIVE;
    }

    /** Disables "Save to current URI" for policy reasons (e.g., imported documents). */
    public void setSaveToCurrentUriDisabledByPolicy(boolean disabled) {
        if (documentLifecycleManager != null) documentLifecycleManager.setSaveToCurrentUriDisabledByPolicy(disabled);
        invalidateOptionsMenuSafely();
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
            documentLifecycleManager = new DocumentLifecycleManager(
                    new org.opendroidpdf.app.hosts.DocumentLifecycleHostAdapter(this),
                    coreCoordinator,
                    comp);
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
            uiStateManager = new org.opendroidpdf.app.ui.UiStateManager(comp);
            alertUiManager = new org.opendroidpdf.app.ui.AlertUiManager(comp);
	            facade = new ActivityFacade(documentLifecycleManager, uiStateManager, alertUiManager);
			
	                // Preferences, alert builder, non-config core, and debug hooks
		            preferencesSubscription = org.opendroidpdf.app.lifecycle.StartupBootstrap.bootstrap(
		                    new org.opendroidpdf.app.hosts.StartupBootstrapHostAdapter(this),
		                    comp.preferencesCoordinator,
		                    new org.opendroidpdf.app.hosts.DebugActionsHostAdapter(this));
	            
	            org.opendroidpdf.app.lifecycle.SavedStateHelper.restore(
	                    new org.opendroidpdf.app.hosts.SavedStateHostAdapter(this),
	                    savedInstanceState);

                CrashReportPrompter.maybePrompt(this, OpenDroidPDFApp.previousSession());
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

    public void runAutotestIfNeeded(final Intent intent) {
        if (comp == null) return;
        org.opendroidpdf.core.MuPdfRepository repo = getRepository();
        if (mDocView == null || repo == null) return;
        comp.debugDelegate.runAutotestIfNeeded(
                new org.opendroidpdf.app.hosts.DebugAutotestHostAdapter(this, repo, mDocView),
                intent);
    }
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
        try {
            if (isFinishing()) {
                org.opendroidpdf.app.diagnostics.SessionDiagnostics.markCleanExit(this);
            }
        } catch (Throwable ignore) {}
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

    public org.opendroidpdf.PageView getSelectedPageView() {
        if (mDocView == null) return null;
        android.view.View sel = mDocView.getSelectedView();
        return (sel instanceof org.opendroidpdf.PageView) ? (org.opendroidpdf.PageView) sel : null;
    }

    // DashboardFragment.DashboardHost
    @Override public void onOpenDocumentRequested() { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onOpenDocumentRequested(); }
    @Override public void onCreateNewDocumentRequested() { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onCreateNewDocumentRequested(); }
    @Override public void onOpenSettingsRequested() { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onOpenSettingsRequested(); }
    @Override public void onRecentEntryRequested(final org.opendroidpdf.app.services.recent.RecentEntry entry) { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onRecentEntryRequested(entry); }
    @Override public org.opendroidpdf.app.services.RecentFilesService recentFilesService() { return comp != null && comp.dashboardHostAdapter != null ? comp.dashboardHostAdapter.recentFilesService() : getRecentFilesService(); }
    @Override public boolean canReadFromUri(@androidx.annotation.NonNull android.net.Uri uri) { return comp != null && comp.dashboardHostAdapter != null ? comp.dashboardHostAdapter.canReadFromUri(uri) : org.opendroidpdf.OpenDroidPDFCore.canReadFromUri(this, uri); }

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
	    public org.opendroidpdf.app.annotation.EraserSettingsController getEraserSettingsController() { return comp != null ? comp.eraserSettingsController : null; }
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

    /** Restores the last non-config instance (core + policy metadata) after configuration changes. */
    public void restoreFromLastNonConfig(Object last) {
        if (last instanceof org.opendroidpdf.app.document.RetainedDocumentCore) {
            org.opendroidpdf.app.document.RetainedDocumentCore r =
                    (org.opendroidpdf.app.document.RetainedDocumentCore) last;
            setCoreFromLastNonConfig(r.core);
            setCurrentDocumentOrigin(r.origin);
            setSaveToCurrentUriDisabledByPolicy(r.saveToCurrentUriDisabledByPolicy);
            setCurrentDocumentIdentity(r.identity);
            setCurrentUserFacingDocument(r.userFacingUri, r.userFacingDisplayName);
            return;
        }
        if (last instanceof OpenDroidPDFCore) {
            setCoreFromLastNonConfig((OpenDroidPDFCore) last);
        }
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

    public void setPostSaveAsAction(@Nullable Runnable action) { postSaveAsAction = action; }
    public void clearPostSaveAsAction() { postSaveAsAction = null; }
    public void runPostSaveAsActionIfSet() {
        Runnable action = postSaveAsAction;
        postSaveAsAction = null;
        if (action != null) {
            try { action.run(); } catch (Throwable ignore) {}
        }
    }
    public void onSaveAsActivityResult(int resultCode) {
        if (resultCode != android.app.Activity.RESULT_OK) {
            clearPostSaveAsAction();
        }
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
        if (mycore == null) return null;
        boolean saveDisabled = documentLifecycleManager != null && documentLifecycleManager.isSaveToCurrentUriDisabledByPolicy();
        org.opendroidpdf.app.document.RetainedDocumentCore retained =
                new org.opendroidpdf.app.document.RetainedDocumentCore(
                        mycore,
                        currentDocumentOrigin(),
                        saveDisabled,
                        currentDocumentIdentityOrNull(),
                        currentUserFacingUriOrNull(),
                        currentUserFacingDisplayNameOrNull());
        setCoreInstance(null);
        return retained;
    }
    
    
    @Override
    protected void onSaveInstanceState(Bundle outState) { //Called when the app is destroyed by the system and in various other cases
        super.onSaveInstanceState(outState);
        org.opendroidpdf.app.lifecycle.SavedStateHelper.save(
                new org.opendroidpdf.app.hosts.SavedStateHostAdapter(this),
                outState);
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


    
    public void setTitle() {
        if (comp != null && comp.titleHostAdapter != null) comp.titleHostAdapter.setTitle();
        bindPageIndicator();
        bindPageScrubber();
        maybeShowPageIndicatorNavHint();
    }

    private void bindPageIndicator() {
        try {
            android.view.View indicator = findViewById(org.opendroidpdf.R.id.page_indicator);
            if (indicator == null) return;
            indicator.setOnClickListener(v -> {
                markPageIndicatorNavHintSeen();
                if (comp != null && comp.documentToolbarController != null) {
                    comp.documentToolbarController.showNavigateViewSheet();
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private void bindPageScrubber() {
        try {
            android.widget.SeekBar scrubber = findViewById(R.id.page_scrubber);
            android.widget.TextView indicator = findViewById(R.id.page_indicator);
            MuPDFReaderView docView = getDocView();
            if (scrubber == null || docView == null) return;

            int pageCount = 0;
            try {
                android.widget.Adapter adapter = docView.getAdapter();
                pageCount = adapter != null ? adapter.getCount() : 0;
            } catch (Throwable ignore) {
                pageCount = 0;
            }
            if (pageCount <= 1) return;
            final int totalPages = pageCount;

            int initialPage = 0;
            try { initialPage = docView.getSelectedItemPosition(); } catch (Throwable ignore) { initialPage = 0; }
            initialPage = Math.max(0, Math.min(totalPages - 1, initialPage));

	            scrubber.setMax(Math.max(0, totalPages - 1));
	            if (scrubber.getProgress() != initialPage) scrubber.setProgress(initialPage);

	            // Optional: thumbnail-only preview while dragging (minimap-style), then render the full
	            // page on release. This avoids expensive page switches/renders while the user is still
	            // scrubbing back-and-forth.
			            final android.widget.ImageView preview = findViewById(R.id.page_scrub_preview);
			            final org.opendroidpdf.core.MuPdfController muPdfController = getMuPdfController();
			            if (preview != null && muPdfController != null) {
			                final long previewMaxPixels = 25_000L;
			                final boolean logPreviewMetrics = android.util.Log.isLoggable("ScrubPreview", android.util.Log.DEBUG);
			                final long[] lastPreviewRequestAtMs = new long[] { 0L };
			                final Object cookieLock = new Object();
			                final Object cacheLock = new Object();
			                final android.util.LruCache<Integer, android.graphics.Bitmap> previewCache = new android.util.LruCache<>(64);
			                final int[] requestedTarget = new int[] { -1 };
			                final int[] activeRenderTarget = new int[] { -1 };
			                final long[] activeRenderStartedAtMs = new long[] { 0L };
			                final int[] pendingTarget = new int[] { -1 };
			                final int[] generation = new int[] { 0 };
			                final int[] activeRenderGen = new int[] { 0 };
		                final MuPDFCore.Cookie[] renderCookie = new MuPDFCore.Cookie[] { null };
		                final kotlinx.coroutines.Job[] renderJob = new kotlinx.coroutines.Job[] { null };
		                final int[] settleTarget = new int[] { -1 };
		                final int[] settleAttempts = new int[] { 0 };
		                final Runnable[] settleToFullRes = new Runnable[] { null };

		                final Runnable[] renderPreview = new Runnable[] { null };
		                renderPreview[0] = new Runnable() {
		                    @Override public void run() {
		                        int target = pendingTarget[0];
		                        if (target < 0) return;

		                        android.graphics.Bitmap cached = null;
		                        synchronized (cacheLock) { cached = previewCache.get(target); }
		                        if (cached != null) {
		                            pendingTarget[0] = -1;
		                            try {
		                                preview.setImageBitmap(cached);
		                                preview.setVisibility(android.view.View.VISIBLE);
		                                if (logPreviewMetrics) {
		                                    long dt = android.os.SystemClock.uptimeMillis() - lastPreviewRequestAtMs[0];
		                                    android.util.Log.d("ScrubPreview", "show page=" + (target + 1) + " dtMs=" + dt + " cached=true");
		                                }
		                            } catch (Throwable ignore) {}
		                            return;
		                        }

		                        // Coalesce renders: if one is already in flight, keep the latest target queued and
		                        // start it when the current render finishes (avoids cancel/redo thrash while dragging).
		                        try {
		                            kotlinx.coroutines.Job job = renderJob[0];
		                            if (job != null && job.isActive()) return;
		                        } catch (Throwable ignore) {}

		                        pendingTarget[0] = -1;
		                        final int gen = ++generation[0];
		                        activeRenderGen[0] = gen;
		                        final int renderTarget = target;
		                        activeRenderTarget[0] = renderTarget;
		                        activeRenderStartedAtMs[0] = android.os.SystemClock.uptimeMillis();

		                        android.graphics.PointF size = null;
		                        try { size = muPdfController.pageSize(renderTarget); } catch (Throwable ignore) { size = null; }
		                        float ratio = 1.294f;
		                        if (size != null && size.x > 0f && size.y > 0f) {
		                            ratio = size.y / size.x;
		                        }
	                        ratio = Math.max(0.15f, Math.min(8.0f, ratio));
	                        int w = 1;
	                        int h = 1;
	                        try {
	                            double ww = Math.sqrt((double) previewMaxPixels / (double) ratio);
	                            w = Math.max(1, (int) Math.round(ww));
	                            h = Math.max(1, (int) Math.round(w * ratio));
		                        } catch (Throwable ignore) {
		                            w = 160;
		                            h = 210;
		                        }
		                        final int fw = w;
		                        final int fh = h;
		                        final MuPDFCore.Cookie cookie = muPdfController.newRenderCookie();
		                        synchronized (cookieLock) {
		                            renderCookie[0] = cookie;
		                        }
		                        renderJob[0] = AppCoroutines.launchIo(AppCoroutines.ioScope(), new Runnable() {
		                            @Override public void run() {
		                                android.graphics.Bitmap bm = null;
		                                try {
			                                    bm = android.graphics.Bitmap.createBitmap(fw, fh, android.graphics.Bitmap.Config.RGB_565);
			                                    muPdfController.drawPage(bm, renderTarget, fw, fh, 0, 0, fw, fh, cookie);
			                                } catch (Throwable ignore) {
			                                    bm = null;
			                                } finally {
		                                    synchronized (cookieLock) {
		                                        if (renderCookie[0] == cookie) {
	                                            renderCookie[0] = null;
		                                        }
		                                        try { cookie.destroy(); } catch (Throwable ignore) {}
		                                    }
		                                }
		                                if (cookie.aborted()) bm = null;
			                                final android.graphics.Bitmap ready = bm;
			                                try {
			                                    preview.post(() -> {
			                                        if (ready != null) {
			                                            synchronized (cacheLock) { previewCache.put(renderTarget, ready); }
			                                        }
				                                        if (ready != null && generation[0] == gen && requestedTarget[0] == renderTarget) {
				                                            try {
				                                                preview.setImageBitmap(ready);
				                                                preview.setVisibility(android.view.View.VISIBLE);
				                                                if (logPreviewMetrics) {
				                                                    long dt = android.os.SystemClock.uptimeMillis() - lastPreviewRequestAtMs[0];
				                                                    android.util.Log.d("ScrubPreview", "show page=" + (renderTarget + 1) + " dtMs=" + dt + " cached=false");
				                                                }
				                                            } catch (Throwable ignore) {}
				                                        }
			                                        try {
			                                            if (activeRenderGen[0] == gen) {
			                                                renderJob[0] = null;
			                                                activeRenderGen[0] = 0;
			                                                activeRenderTarget[0] = -1;
			                                                activeRenderStartedAtMs[0] = 0L;
			                                            }
			                                            if (renderPreview[0] != null) renderPreview[0].run();
			                                        } catch (Throwable ignore) {}
			                                    });
			                                } catch (Throwable ignore) {
		                                }
		                            }
		                        });
		                    }
		                };

		                scrubber.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
		                    @Override public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
		                        int clamped = Math.max(0, Math.min(totalPages - 1, progress));
		                        if (!fromUser) return;
		                        requestedTarget[0] = clamped;
		                        lastPreviewRequestAtMs[0] = android.os.SystemClock.uptimeMillis();
				                        if (indicator != null) {
				                            indicator.setText(String.format(java.util.Locale.getDefault(), "%d / %d  ▾", clamped + 1, totalPages));
				                        }
				                        pendingTarget[0] = clamped;
			                        try {
			                            final long abortAfterMs = 50L;
			                            final int abortDeltaPages = 1;
			                            kotlinx.coroutines.Job job = renderJob[0];
			                            if (job != null && job.isActive()) {
			                                int active = activeRenderTarget[0];
			                                long started = activeRenderStartedAtMs[0];
			                                long now = android.os.SystemClock.uptimeMillis();
			                                if (active >= 0 && active != clamped && started > 0L) {
			                                    if ((now - started) >= abortAfterMs && Math.abs(clamped - active) >= abortDeltaPages) {
			                                        synchronized (cookieLock) {
			                                            MuPDFCore.Cookie cookie = renderCookie[0];
			                                            if (cookie != null) {
			                                                try { cookie.abort(); } catch (Throwable ignore) {}
			                                            }
			                                        }
			                                        try { AppCoroutines.cancel(job); } catch (Throwable ignore) {}
			                                    }
			                                }
			                            }
			                        } catch (Throwable ignore) {}
			                        if (renderPreview[0] != null) renderPreview[0].run();
			                    }

				                    @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
				                        markPageIndicatorNavHintSeen();
				                        try { if (seekBar != null && settleToFullRes[0] != null) seekBar.removeCallbacks(settleToFullRes[0]); } catch (Throwable ignore) {}
				                        settleTarget[0] = -1;
				                        settleAttempts[0] = 0;
			                        try { docView.setScrubbing(true); } catch (Throwable ignore) {}
			                        try { preview.setVisibility(android.view.View.GONE); } catch (Throwable ignore) {}
			                        generation[0]++; // ignore late thumbnail updates from a previous drag
		                        synchronized (cookieLock) {
		                            MuPDFCore.Cookie cookie = renderCookie[0];
		                            if (cookie != null) {
		                                try { cookie.abort(); } catch (Throwable ignore) {}
		                            }
		                        }
			                        try { AppCoroutines.cancel(renderJob[0]); } catch (Throwable ignore) {}
				                        renderJob[0] = null;
					                        activeRenderGen[0] = 0;
					                        activeRenderTarget[0] = -1;
					                        activeRenderStartedAtMs[0] = 0L;
					                        requestedTarget[0] = Math.max(0, Math.min(totalPages - 1, seekBar != null ? seekBar.getProgress() : 0));
					                        pendingTarget[0] = Math.max(0, Math.min(totalPages - 1, seekBar != null ? seekBar.getProgress() : 0));
					                        lastPreviewRequestAtMs[0] = android.os.SystemClock.uptimeMillis();
					                        if (renderPreview[0] != null) renderPreview[0].run();
					                    }

		                    @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
		                        markPageIndicatorNavHintSeen();
		                        int target = 0;
		                        try { target = seekBar != null ? seekBar.getProgress() : 0; } catch (Throwable ignore) { target = 0; }
		                        target = Math.max(0, Math.min(totalPages - 1, target));
		                        generation[0]++; // ignore late thumbnail updates from in-flight renders; prioritize final target
		                        synchronized (cookieLock) {
		                            MuPDFCore.Cookie cookie = renderCookie[0];
		                            if (cookie != null) {
		                                try { cookie.abort(); } catch (Throwable ignore) {}
		                            }
		                        }
		                        try { AppCoroutines.cancel(renderJob[0]); } catch (Throwable ignore) {}
			                        renderJob[0] = null;
				                        activeRenderGen[0] = 0;
					                        activeRenderTarget[0] = -1;
					                        activeRenderStartedAtMs[0] = 0L;
					                        requestedTarget[0] = target;
					                        pendingTarget[0] = target;
					                        lastPreviewRequestAtMs[0] = android.os.SystemClock.uptimeMillis();
					                        if (renderPreview[0] != null) renderPreview[0].run(); // ensure preview matches the final target

			                        settleTarget[0] = target;
	                        settleAttempts[0] = 0;
	                        try { docView.setScrubbing(true); } catch (Throwable ignore) {}
	                        try { docView.setDisplayedViewIndex(target, true); } catch (Throwable ignore) {}
	                        try { docView.setNormalizedScroll(0.0f, 0.0f); } catch (Throwable ignore) {}

	                        if (settleToFullRes[0] == null) {
	                            settleToFullRes[0] = new Runnable() {
	                                @Override public void run() {
	                                    int want = settleTarget[0];
	                                    if (want < 0) return;
	                                    int tries = settleAttempts[0]++;
	                                    if (tries > 30) {
	                                        settleTarget[0] = -1;
	                                        try { docView.setScrubbing(false); } catch (Throwable ignore) {}
	                                        try { preview.setVisibility(android.view.View.GONE); } catch (Throwable ignore) {}
	                                        generation[0]++; // ignore late thumbnail updates
	                                        synchronized (cookieLock) {
	                                            MuPDFCore.Cookie cookie = renderCookie[0];
	                                            if (cookie != null) {
	                                                try { cookie.abort(); } catch (Throwable ignore) {}
	                                            }
	                                        }
	                                        try { AppCoroutines.cancel(renderJob[0]); } catch (Throwable ignore) {}
	                                        renderJob[0] = null;
	                                        return;
	                                    }
	                                    int cur = -1;
	                                    try { cur = docView.getSelectedItemPosition(); } catch (Throwable ignore) { cur = -1; }
	                                    if (cur == want) {
	                                        settleTarget[0] = -1;
	                                        try { docView.setScrubbing(false); } catch (Throwable ignore) {}
	                                        try { preview.setVisibility(android.view.View.GONE); } catch (Throwable ignore) {}
	                                        generation[0]++; // ignore late thumbnail updates
	                                        try {
	                                            android.view.View v = docView.getSelectedView();
	                                            if (v instanceof org.opendroidpdf.MuPDFView) {
	                                                ((org.opendroidpdf.MuPDFView) v).redraw(true);
	                                            }
	                                            if (v instanceof org.opendroidpdf.PageView) {
	                                                ((org.opendroidpdf.PageView) v).loadDeferredPageDataAfterScrub();
	                                            }
	                                        } catch (Throwable ignore) {
	                                        }
	                                        return;
	                                    }
	                                    try { if (seekBar != null) seekBar.postDelayed(this, 50); } catch (Throwable ignore) {}
	                                }
	                            };
	                        }
	                        try { if (seekBar != null) seekBar.postDelayed(settleToFullRes[0], 50); } catch (Throwable ignore) {}
	                    }
	                });
	                return;
	            }

	            // Live scrubbing: while dragging, navigate with a small throttle so the visible page
	            // tracks the thumb without issuing a full page switch for every tiny movement.
	            final int scrubThrottleMs = 30;
	            final int[] pendingTarget = new int[] { -1 };
            final int[] lastRequestedTarget = new int[] { initialPage };
            final long[] lastRequestUptimeMs = new long[] { 0L };
            final int[] settleTarget = new int[] { -1 };
            final int[] settleAttempts = new int[] { 0 };
            final Runnable[] settleToFullRes = new Runnable[] { null };
            final Runnable throttledNavigate = new Runnable() {
                @Override
                public void run() {
                    int target = pendingTarget[0];
                    if (target < 0) return;
                    pendingTarget[0] = -1;
                    lastRequestUptimeMs[0] = android.os.SystemClock.uptimeMillis();
                    int cur = -1;
                    try { cur = docView.getSelectedItemPosition(); } catch (Throwable ignore) { cur = -1; }
                    if (cur == target) {
                        lastRequestedTarget[0] = target;
                        return;
                    }
                    lastRequestedTarget[0] = target;
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        android.util.Log.d("Scrubber", "navigate target=" + target
                                + " cur=" + cur
                                + " scrubbing=" + docView.isScrubbing()
                                + " t=" + lastRequestUptimeMs[0]);
                    }
                    try { docView.setDisplayedViewIndex(target, true); } catch (Throwable ignore) {}
                }
            };

            scrubber.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    int clamped = Math.max(0, Math.min(totalPages - 1, progress));
                    if (!fromUser) {
                        // Keep our throttle state aligned with programmatic updates (swipes/buttons),
                        // otherwise we can accidentally skip legitimate user scrubs.
                        lastRequestedTarget[0] = clamped;
                        return;
                    }
                    if (indicator != null) {
                        indicator.setText(String.format(java.util.Locale.getDefault(), "%d / %d  ▾", clamped + 1, totalPages));
                    }
                    pendingTarget[0] = clamped;
                    long now = android.os.SystemClock.uptimeMillis();
                    long since = now - lastRequestUptimeMs[0];
                    try { seekBar.removeCallbacks(throttledNavigate); } catch (Throwable ignore) {}
                    if (since >= scrubThrottleMs) {
                        throttledNavigate.run();
                    } else {
                        try { seekBar.postDelayed(throttledNavigate, scrubThrottleMs - since); } catch (Throwable ignore) {}
                    }
                }

                @Override
                public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                    markPageIndicatorNavHintSeen();
                    try { if (seekBar != null && settleToFullRes[0] != null) seekBar.removeCallbacks(settleToFullRes[0]); } catch (Throwable ignore) {}
                    settleTarget[0] = -1;
                    settleAttempts[0] = 0;
                    try { docView.setScrubbing(true); } catch (Throwable ignore) {}
                    try { lastRequestedTarget[0] = docView.getSelectedItemPosition(); } catch (Throwable ignore) { lastRequestedTarget[0] = Math.max(0, Math.min(totalPages - 1, seekBar != null ? seekBar.getProgress() : 0)); }
                    lastRequestUptimeMs[0] = 0L;
                }

                @Override
                public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                    markPageIndicatorNavHintSeen();
                    int target = 0;
                    try { target = seekBar != null ? seekBar.getProgress() : 0; } catch (Throwable ignore) { target = 0; }
                    target = Math.max(0, Math.min(totalPages - 1, target));
                    try { seekBar.removeCallbacks(throttledNavigate); } catch (Throwable ignore) {}
                    pendingTarget[0] = target;
                    throttledNavigate.run();
                    try { docView.setNormalizedScroll(0.0f, 0.0f); } catch (Throwable ignore) {}

                    settleTarget[0] = target;
                    settleAttempts[0] = 0;
                    if (settleToFullRes[0] == null) {
                        settleToFullRes[0] = new Runnable() {
                            @Override public void run() {
                                int want = settleTarget[0];
                                if (want < 0) return;
                                int tries = settleAttempts[0]++;
                                if (tries > 30) {
                                    settleTarget[0] = -1;
                                    try { docView.setScrubbing(false); } catch (Throwable ignore) {}
                                    return;
                                }
                                int cur = -1;
                                try { cur = docView.getSelectedItemPosition(); } catch (Throwable ignore) { cur = -1; }
	                                if (cur == want) {
	                                    settleTarget[0] = -1;
	                                    try { docView.setScrubbing(false); } catch (Throwable ignore) {}
	                                    try {
	                                        android.view.View v = docView.getSelectedView();
	                                        if (v instanceof org.opendroidpdf.MuPDFView) {
	                                            ((org.opendroidpdf.MuPDFView) v).redraw(true);
	                                        }
	                                        if (v instanceof org.opendroidpdf.PageView) {
	                                            ((org.opendroidpdf.PageView) v).loadDeferredPageDataAfterScrub();
	                                        }
	                                    } catch (Throwable ignore) {
	                                    }
	                                    return;
	                                }
                                try { if (seekBar != null) seekBar.postDelayed(this, 50); } catch (Throwable ignore) {}
                            }
                        };
                    }
                    try { if (seekBar != null) seekBar.postDelayed(settleToFullRes[0], 50); } catch (Throwable ignore) {}
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private SharedPreferences uiPrefs() {
        Context app = getApplicationContext();
        return app.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
    }

    private void markPageIndicatorNavHintSeen() {
        try {
            uiPrefs().edit().putBoolean(SettingsActivity.PREF_SEEN_PAGE_INDICATOR_NAV_HINT, true).apply();
        } catch (Throwable ignore) {
        }
    }

    private int currentDocumentPageCount() {
        try {
            MuPDFReaderView dv = getDocView();
            if (dv == null) return 0;
            android.widget.Adapter adapter = dv.getAdapter();
            return adapter != null ? adapter.getCount() : 0;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private void maybeShowPageIndicatorNavHint() {
        if (pageIndicatorHintShownThisSession) return;
        try {
            if (uiPrefs().getBoolean(SettingsActivity.PREF_SEEN_PAGE_INDICATOR_NAV_HINT, false)) return;
            if (currentDocumentPageCount() <= 1) return;

            View indicator = findViewById(R.id.page_indicator);
            if (indicator == null || indicator.getVisibility() != View.VISIBLE) return;

            View anchor = findViewById(R.id.main_layout);
            if (anchor == null) anchor = findViewById(android.R.id.content);
            if (anchor == null) return;

            Snackbar sb = Snackbar.make(anchor, getString(R.string.page_indicator_nav_hint), Snackbar.LENGTH_LONG);
            try { sb.setAnchorView(indicator); } catch (Throwable ignore) {}
            sb.show();
            pageIndicatorHintShownThisSession = true;
        } catch (Throwable ignore) {
        }
    }

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
