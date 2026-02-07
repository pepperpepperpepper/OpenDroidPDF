package org.opendroidpdf;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.os.SystemClock;
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
    private static final long PENDING_TEXT_ANNOT_INSERT_TTL_MS = 90_000L;
    @Nullable private String pendingTextAnnotInsertText;
    private long pendingTextAnnotInsertSetUptimeMs = 0L;
    private boolean pendingOpenExportSheetOnNextDocViewAttached = false;
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
    @Nullable private org.opendroidpdf.app.readaloud.ReadAloudController readAloudController;
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
        try {
            pendingOpenExportSheetOnNextDocViewAttached =
                    intent != null && intent.getBooleanExtra(org.opendroidpdf.app.document.DocumentViewerIntents.EXTRA_OPEN_EXPORT_SHEET, false);
        } catch (Throwable ignore) {
            pendingOpenExportSheetOnNextDocViewAttached = false;
        }
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
        if (readAloudController != null) {
            try { readAloudController.pause(); } catch (Throwable ignore) {}
        }
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
        if (readAloudController != null) {
            try { readAloudController.shutdown(); } catch (Throwable ignore) {}
            readAloudController = null;
        }
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

    public void openAssistant() {
        org.opendroidpdf.app.assistant.AssistantSheetUi.show(this);
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
            boolean res = comp.optionsMenuController.onPrepareOptionsMenu(menu, () -> super.onPrepareOptionsMenu(menu));
            updateQuickActionsBarVisibility();
            return res;
        }
        updateQuickActionsBarVisibility();
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

    /** One-shot preset text for the next tap-to-place text annotation (cleared after use/timeout). */
    public void setPendingTextAnnotationInsertText(@NonNull String text) {
        if (text == null) text = "";
        text = text.trim();
        if (text.isEmpty()) {
            clearPendingTextAnnotationInsertText();
            return;
        }
        pendingTextAnnotInsertText = text;
        pendingTextAnnotInsertSetUptimeMs = SystemClock.uptimeMillis();
    }

    @Nullable
    public String consumePendingTextAnnotationInsertTextOrNull() {
        String text = pendingTextAnnotInsertText;
        if (text == null) return null;
        long ageMs = SystemClock.uptimeMillis() - pendingTextAnnotInsertSetUptimeMs;
        if (ageMs > PENDING_TEXT_ANNOT_INSERT_TTL_MS) {
            pendingTextAnnotInsertText = null;
            return null;
        }
        pendingTextAnnotInsertText = null;
        return text;
    }

    public void clearPendingTextAnnotationInsertText() {
        pendingTextAnnotInsertText = null;
    }

    /** One-shot flag: when set, the viewer will open the Export sheet after the next document load. */
    public boolean consumeOpenExportSheetOnNextDocViewAttached() {
        boolean value = pendingOpenExportSheetOnNextDocViewAttached;
        pendingOpenExportSheetOnNextDocViewAttached = false;
        return value;
    }

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

    @NonNull
    private org.opendroidpdf.app.readaloud.ReadAloudController ensureReadAloudController() {
        if (readAloudController != null) return readAloudController;
        readAloudController = new org.opendroidpdf.app.readaloud.ReadAloudController(new org.opendroidpdf.app.readaloud.ReadAloudController.Host() {
            @NonNull @Override public AppCompatActivity activity() { return OpenDroidPDFActivity.this; }
            @Nullable @Override public MuPDFReaderView docViewOrNull() { return OpenDroidPDFActivity.this.getDocView(); }
            @Override public void invalidateReadAloudUi() {
                try { OpenDroidPDFActivity.this.updateReadAloudBarState(); } catch (Throwable ignore) {}
                try { OpenDroidPDFActivity.this.updateQuickActionsBarVisibility(); } catch (Throwable ignore) {}
                try { OpenDroidPDFActivity.this.invalidateOptionsMenuSafely(); } catch (Throwable ignore) {}
            }
            @Override public void showInfo(@NonNull String message) { OpenDroidPDFActivity.this.showInfo(message); }
        });
        return readAloudController;
    }

    public void requestReadAloud() {
        try {
            if (getDocView() == null) return;
            ensureReadAloudController().toggleFromMenu();
        } catch (Throwable ignore) {
        }
    }

    public void stopReadAloudIfActive() {
        try {
            if (readAloudController != null && readAloudController.isActive()) {
                readAloudController.stop();
            }
        } catch (Throwable ignore) {
        }
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
        bindPageScrubberTab();
        bindNavigationMenuButton();
        bindReaderBottomBars();
        updateQuickActionsBarVisibility();
        maybeShowPageIndicatorNavHint();
    }

    /** Toggle reader chrome (top toolbar + page navigation tab), Acrobat-style. */
    public void toggleReaderChrome() {
        try {
            // Avoid conflicting with true fullscreen behavior (owned by FullscreenController).
            boolean fullscreen =
                    (getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
            if (fullscreen) return;
        } catch (Throwable ignore) {
        }

        MuPDFReaderView docView = getDocView();
        if (docView == null) return;

        androidx.appcompat.app.ActionBar bar = getSupportActionBar();
        if (bar == null) return;

        boolean showing = false;
        try { showing = bar.isShowing(); } catch (Throwable ignore) { showing = false; }

        // Reuse ReadingModeController’s padding logic, but don’t persist the preference.
        org.opendroidpdf.app.ui.ReadingModeController.applyToDocumentView(this, docView, showing);

        try {
            if (showing) {
                actionBarModeDelegate.setHidden();
                android.widget.SeekBar scrubber = findViewById(R.id.page_scrubber);
                android.widget.ImageView preview = findViewById(R.id.page_scrub_preview);
                if (scrubber != null) scrubber.setVisibility(android.view.View.GONE);
                if (preview != null) preview.setVisibility(android.view.View.GONE);
            } else {
                actionBarModeDelegate.setMainIfHidden();
            }
        } catch (Throwable ignore) {
        }

        invalidateOptionsMenuSafely();
        setTitle();
    }

    private void bindReaderBottomBars() {
        bindQuickActionsBar();
        bindReadAloudBar();
        bindSelectionActionsBar();
        bindAnnotActionsBar();
        bindAddTextActionsBar();
    }

    private void bindReadAloudBar() {
        try {
            android.view.View playPause = findViewById(R.id.read_aloud_action_play_pause);
            if (playPause != null) playPause.setOnClickListener(v -> ensureReadAloudController().togglePlayPause());

            android.view.View stop = findViewById(R.id.read_aloud_action_stop);
            if (stop != null) stop.setOnClickListener(v -> {
                if (readAloudController != null) readAloudController.stop();
            });

            updateReadAloudBarState();
        } catch (Throwable ignore) {
        }
    }

    private void updateReadAloudBarState() {
        try {
            boolean playing = readAloudController != null && readAloudController.isPlaying();

            android.widget.ImageView icon = findViewById(R.id.read_aloud_action_play_pause_icon);
            if (icon != null) {
                icon.setImageResource(playing ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp);
            }

            android.widget.TextView label = findViewById(R.id.read_aloud_action_play_pause_label);
            if (label != null) {
                label.setText(playing ? R.string.read_aloud_pause : R.string.read_aloud_play);
            }

            android.view.View playPause = findViewById(R.id.read_aloud_action_play_pause);
            if (playPause != null) {
                playPause.setContentDescription(getString(playing ? R.string.read_aloud_pause : R.string.read_aloud_play));
            }
        } catch (Throwable ignore) {
        }
    }

    private void bindQuickActionsBar() {
        try {
            android.view.View comments = findViewById(R.id.quick_action_comments);
            if (comments != null) comments.setOnClickListener(v -> showCommentsListFromQuickActions());

            android.view.View highlight = findViewById(R.id.quick_action_highlight);
            if (highlight != null) highlight.setOnClickListener(v -> enterTextSelectionFromQuickActions());

            android.view.View draw = findViewById(R.id.quick_action_draw);
            if (draw != null) draw.setOnClickListener(v -> enterDrawingModeFromQuickActions());

            android.view.View addText = findViewById(R.id.quick_action_add_text);
            if (addText != null) addText.setOnClickListener(v -> enterAddTextModeFromQuickActions());

            android.view.View fillSign = findViewById(R.id.quick_action_fill_sign);
            if (fillSign != null) {
                boolean isPdf = comp != null && comp.documentViewHostAdapter != null && comp.documentViewHostAdapter.isPdfDocument();
                fillSign.setEnabled(isPdf);
                fillSign.setAlpha(isPdf ? 1f : 0.5f);
                fillSign.setOnClickListener(isPdf ? (v -> showFillSignFromQuickActions()) : null);
            }

            android.view.View moreTools = findViewById(R.id.quick_action_more_tools);
            if (moreTools != null) moreTools.setOnClickListener(v -> showMoreToolsFromQuickActions());
        } catch (Throwable ignore) {
        }
    }

    private void bindSelectionActionsBar() {
        try {
            android.view.View highlight = findViewById(R.id.selection_action_highlight);
            if (highlight != null) {
                highlight.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_highlight));
                highlight.setOnLongClickListener(v -> {
                    showMarkupColorDialog(
                            SettingsActivity.PREF_HIGHLIGHT_COLOR,
                            21,
                            R.string.highlight_color,
                            R.string.highlight_color_summ,
                            R.string.menu_highlight);
                    return true;
                });
            }

            android.view.View underline = findViewById(R.id.selection_action_underline);
            if (underline != null) {
                underline.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_underline));
                underline.setOnLongClickListener(v -> {
                    showMarkupColorDialog(
                            SettingsActivity.PREF_UNDERLINE_COLOR,
                            3,
                            R.string.underline_color,
                            R.string.underline_color_summ,
                            R.string.menu_underline);
                    return true;
                });
            }

            android.view.View strikeout = findViewById(R.id.selection_action_strikeout);
            if (strikeout != null) {
                strikeout.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_strikeout));
                strikeout.setOnLongClickListener(v -> {
                    showMarkupColorDialog(
                            SettingsActivity.PREF_STRIKEOUT_COLOR,
                            15,
                            R.string.strikeout_color,
                            R.string.strikeout_color_summ,
                            R.string.menu_strikeout);
                    return true;
                });
            }

            android.view.View copy = findViewById(R.id.selection_action_copy);
            if (copy != null) copy.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_copytext));

            android.view.View done = findViewById(R.id.selection_action_done);
            if (done != null) done.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_accept));
        } catch (Throwable ignore) {
        }
    }

    private void bindAnnotActionsBar() {
        try {
            android.view.View cancel = findViewById(R.id.annot_action_cancel);
            if (cancel != null) cancel.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_cancel));

            android.view.View draw = findViewById(R.id.annot_action_draw);
            if (draw != null) draw.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_draw));

            android.view.View erase = findViewById(R.id.annot_action_erase);
            if (erase != null) erase.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_erase));

            android.view.View penSettings = findViewById(R.id.annot_action_pen_settings);
            if (penSettings != null) penSettings.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_pen_settings));

            android.view.View eraserSize = findViewById(R.id.annot_action_eraser_size);
            if (eraserSize != null) eraserSize.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_eraser_size));

            android.view.View done = findViewById(R.id.annot_action_done);
            if (done != null) done.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_accept));
        } catch (Throwable ignore) {
        }
    }

    private void bindAddTextActionsBar() {
        try {
            android.view.View cancel = findViewById(R.id.add_text_action_cancel);
            if (cancel != null) cancel.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_cancel));

            android.view.View color = findViewById(R.id.add_text_action_color);
            if (color != null) color.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_text_annot_color));

            android.view.View paste = findViewById(R.id.add_text_action_paste);
            if (paste != null) paste.setOnClickListener(v -> performAnnotMenuAction(R.id.menu_paste_text_annot));
        } catch (Throwable ignore) {
        }
    }

    private void performAnnotMenuAction(int menuItemId) {
        try {
            if (comp != null && comp.annotationToolbarController != null) {
                comp.annotationToolbarController.performMenuAction(menuItemId);
            }
        } catch (Throwable ignore) {
        } finally {
            try { invalidateOptionsMenuSafely(); } catch (Throwable ignore) {}
            updateQuickActionsBarVisibility();
        }
    }

    private void showMarkupColorDialog(@NonNull String prefKey,
                                      int defaultIndex,
                                      int titleResId,
                                      int summaryResId,
                                      int toolLabelResId) {
        try {
            org.opendroidpdf.app.annotation.AnnotationColorPickerDialog.show(
                    this,
                    titleResId,
                    summaryResId,
                    toolLabelResId,
                    prefKey,
                    defaultIndex);
        } catch (Throwable ignore) {
        }
    }

    private void updateQuickActionsBarVisibility() {
        try {
            android.view.View quick = findViewById(R.id.reader_quick_actions_bar);
            android.view.View readAloud = findViewById(R.id.reader_read_aloud_bar);
            android.view.View selection = findViewById(R.id.reader_selection_actions_bar);
            android.view.View annot = findViewById(R.id.reader_annot_actions_bar);
            android.view.View addText = findViewById(R.id.reader_add_text_actions_bar);
            if (quick == null && readAloud == null && selection == null && annot == null && addText == null) return;

            boolean chromeVisible = false;
            try {
                androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
                chromeVisible = actionBar != null && actionBar.isShowing();
            } catch (Throwable ignore) {
                chromeVisible = false;
            }

            boolean showBars = chromeVisible && !dashboardIsShown();
            boolean showReadAloud =
                    (readAloudController != null && readAloudController.isActive())
                            && hasDocumentLoaded()
                            && !dashboardIsShown();
            org.opendroidpdf.app.ui.ActionBarMode mode = getActionBarMode();

            setBottomBarVisibility(readAloud, showReadAloud);
            if (showReadAloud) {
                setBottomBarVisibility(quick, false);
                setBottomBarVisibility(selection, false);
                setBottomBarVisibility(annot, false);
                setBottomBarVisibility(addText, false);
            } else {
                setBottomBarVisibility(quick, showBars && mode == org.opendroidpdf.app.ui.ActionBarMode.Main);
                setBottomBarVisibility(selection, showBars && mode == org.opendroidpdf.app.ui.ActionBarMode.Selection);
                setBottomBarVisibility(annot, showBars && mode == org.opendroidpdf.app.ui.ActionBarMode.Annot);
                setBottomBarVisibility(addText, showBars && mode == org.opendroidpdf.app.ui.ActionBarMode.AddingTextAnnot);
            }

            android.view.View navMenu = findViewById(R.id.navigation_menu_button);
            if (navMenu != null) navMenu.setVisibility((showBars && hasDocumentLoaded()) ? android.view.View.VISIBLE : android.view.View.GONE);

            updateAnnotActionsBarState();
            updateAddTextActionsBarState();

            // The document-host insets listeners depend on whether the bottom bar is visible.
            android.view.View root = findViewById(R.id.document_host_root);
            if (root != null) {
                try { androidx.core.view.ViewCompat.requestApplyInsets(root); } catch (Throwable ignore) {}
            }
        } catch (Throwable ignore) {
        }
    }

    private void bindNavigationMenuButton() {
        try {
            android.view.View button = findViewById(R.id.navigation_menu_button);
            if (button == null) return;
            button.setOnClickListener(v -> {
                if (comp != null && comp.documentToolbarController != null) {
                    comp.documentToolbarController.showNavigationMenuSheet();
                }
            });
        } catch (Throwable ignore) {
        }
    }

    private static void setBottomBarVisibility(@Nullable android.view.View bar, boolean show) {
        if (bar == null) return;
        int desired = show ? android.view.View.VISIBLE : android.view.View.GONE;
        if (bar.getVisibility() != desired) bar.setVisibility(desired);
    }

    private void updateAnnotActionsBarState() {
        try {
            android.view.View bar = findViewById(R.id.reader_annot_actions_bar);
            if (bar == null || bar.getVisibility() != android.view.View.VISIBLE) return;

            boolean drawing = annotationModeStore != null && annotationModeStore.isDrawingModeActive();
            boolean erasing = annotationModeStore != null && annotationModeStore.isErasingModeActive();

            android.view.View draw = findViewById(R.id.annot_action_draw);
            if (draw != null) draw.setAlpha(drawing ? 1f : (erasing ? 0.55f : 1f));

            android.view.View erase = findViewById(R.id.annot_action_erase);
            if (erase != null) erase.setAlpha(erasing ? 1f : (drawing ? 0.55f : 1f));

            android.view.View penSettings = findViewById(R.id.annot_action_pen_settings);
            if (penSettings != null) penSettings.setVisibility(drawing ? android.view.View.VISIBLE : android.view.View.GONE);

            android.view.View eraserSize = findViewById(R.id.annot_action_eraser_size);
            if (eraserSize != null) eraserSize.setVisibility(erasing ? android.view.View.VISIBLE : android.view.View.GONE);
        } catch (Throwable ignore) {
        }
    }

    private void updateAddTextActionsBarState() {
        try {
            android.view.View bar = findViewById(R.id.reader_add_text_actions_bar);
            if (bar == null || bar.getVisibility() != android.view.View.VISIBLE) return;

            android.view.View paste = findViewById(R.id.add_text_action_paste);
            if (paste != null) {
                boolean enabled = org.opendroidpdf.app.annotation.TextAnnotationClipboard.hasPayload();
                paste.setEnabled(enabled);
                paste.setAlpha(enabled ? 1f : 0.5f);
            }
        } catch (Throwable ignore) {
        }
    }

    private void showCommentsListFromQuickActions() {
        try {
            MuPDFReaderView doc = getDocView();
            org.opendroidpdf.core.MuPdfRepository repo = getRepository();
            if (doc == null || repo == null) return;
            org.opendroidpdf.app.sidecar.SidecarAnnotationProvider provider =
                    (comp != null && comp.documentViewHostAdapter != null)
                            ? comp.documentViewHostAdapter.sidecarAnnotationProviderOrNull()
                            : null;
            new org.opendroidpdf.app.comments.CommentsListController().show(this, doc, repo, provider);
        } catch (Throwable ignore) {
        }
    }

    private void enterTextSelectionFromQuickActions() {
        try {
            if (comp != null && comp.drawingService != null) {
                try { comp.drawingService.switchToViewingMode(); } catch (Throwable ignore) {}
            }
            MuPDFReaderView doc = getDocView();
            if (doc != null) {
                try { doc.requestMode(org.opendroidpdf.app.reader.gesture.ReaderMode.SELECTING); } catch (Throwable ignore) {}
            }
            try { showInfo(getString(org.opendroidpdf.R.string.tap_text_to_select)); } catch (Throwable ignore) {}
            updateQuickActionsBarVisibility();
        } catch (Throwable ignore) {
        }
    }

    private void enterDrawingModeFromQuickActions() {
        try {
            if (annotationModeStore != null) annotationModeStore.enterDrawingMode();
            updateQuickActionsBarVisibility();
        } catch (Throwable ignore) {
        }
    }

    private void enterAddTextModeFromQuickActions() {
        try {
            org.opendroidpdf.PageView pageView = getSelectedPageView();
            if (pageView != null) {
                try { pageView.deselectText(); } catch (Throwable ignore) {}
                if (pageView instanceof org.opendroidpdf.MuPDFPageView) {
                    try { ((org.opendroidpdf.MuPDFPageView) pageView).deselectAnnotation(); } catch (Throwable ignore) {}
                }
            }
            if (annotationModeStore != null) annotationModeStore.enterAddingTextMode();
            try { showInfo(getString(org.opendroidpdf.R.string.tap_to_add_annotation)); } catch (Throwable ignore) {}
            updateQuickActionsBarVisibility();
        } catch (Throwable ignore) {
        }
    }

    private void showFillSignFromQuickActions() {
        try {
            MuPDFReaderView docView = getDocView();
            if (docView == null) return;
            if (comp != null && comp.documentViewHostAdapter != null && !comp.documentViewHostAdapter.isPdfDocument()) return;

            final CharSequence[] items = new CharSequence[] {
                    getString(org.opendroidpdf.R.string.fill_sign_action_signature),
                    getString(org.opendroidpdf.R.string.fill_sign_action_initials),
                    getString(org.opendroidpdf.R.string.fill_sign_action_checkmark),
                    getString(org.opendroidpdf.R.string.fill_sign_action_cross),
                    getString(org.opendroidpdf.R.string.fill_sign_action_date),
                    getString(org.opendroidpdf.R.string.fill_sign_action_name),
            };

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(org.opendroidpdf.R.string.fill_sign_dialog_title)
                    .setItems(items, (d, which) -> {
                        org.opendroidpdf.app.fillsign.FillSignAction action;
                        switch (which) {
                            case 0: action = org.opendroidpdf.app.fillsign.FillSignAction.SIGNATURE; break;
                            case 1: action = org.opendroidpdf.app.fillsign.FillSignAction.INITIALS; break;
                            case 2: action = org.opendroidpdf.app.fillsign.FillSignAction.CHECKMARK; break;
                            case 3: action = org.opendroidpdf.app.fillsign.FillSignAction.CROSS; break;
                            case 4: action = org.opendroidpdf.app.fillsign.FillSignAction.DATE; break;
                            case 5: action = org.opendroidpdf.app.fillsign.FillSignAction.NAME; break;
                            default: action = null;
                        }
                        if (action != null) {
                            try { docView.requestFillSignAction(action); } catch (Throwable ignore) {}
                        }
                    })
                    .show();
        } catch (Throwable ignore) {
        }
    }

    private void showMoreToolsFromQuickActions() {
        try {
            if (comp != null && comp.documentToolbarController != null) {
                comp.documentToolbarController.showMoreToolsHubSheet();
                return;
            }
        } catch (Throwable ignore) {
        }

        try {
            androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) {
                toolbar.showOverflowMenu();
                return;
            }
        } catch (Throwable ignore) {
        }

        try {
            if (comp != null && comp.annotationToolbarController != null) {
                comp.annotationToolbarController.showAnnotateSheet();
            }
        } catch (Throwable ignore) {
        }
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
            indicator.setOnLongClickListener(v -> {
                markPageIndicatorNavHintSeen();
                try {
                    android.widget.SeekBar scrubber = findViewById(R.id.page_scrubber);
                    android.widget.ImageView preview = findViewById(R.id.page_scrub_preview);
                    if (scrubber == null) return true;
                    boolean show = scrubber.getVisibility() != android.view.View.VISIBLE;
                    scrubber.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
                    if (!show && preview != null) preview.setVisibility(android.view.View.GONE);
                } catch (Throwable ignore) {
                }
                return true;
            });
        } catch (Throwable ignore) {
        }
    }

    private void bindPageScrubber() {
        try {
            android.widget.SeekBar scrubber = findViewById(R.id.page_scrubber);
            android.widget.TextView indicator = findViewById(R.id.page_indicator);
            android.widget.ImageView preview = findViewById(R.id.page_scrub_preview);
            android.widget.TextView tab = findViewById(R.id.page_scrubber_tab);
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

            org.opendroidpdf.app.navigation.PageScrubberBinder.bind(
                    scrubber,
                    docView,
                    totalPages,
                    initialPage,
                    preview,
                    getMuPdfController(),
                    (pageIndex, pages, fromUser) -> {
                        if (!fromUser) return;
                        if (indicator != null) {
                            indicator.setText(String.format(java.util.Locale.getDefault(), "%d / %d  ▾", pageIndex + 1, pages));
                        }
                        if (tab != null) {
                            tab.setText(String.format(java.util.Locale.getDefault(), "%d", pageIndex + 1));
                            tab.setContentDescription(
                                    String.format(java.util.Locale.getDefault(), "%s: %d / %d",
                                            getString(R.string.page_scrubber_tab),
                                            pageIndex + 1,
                                            pages));
                        }
                    },
                    this::markPageIndicatorNavHintSeen);
        } catch (Throwable ignore) {
        }
    }

    private void bindPageScrubberTab() {
        try {
            android.widget.TextView tab = findViewById(R.id.page_scrubber_tab);
            android.widget.SeekBar driver = findViewById(R.id.page_scrubber_tab_driver);
            android.view.View host = findViewById(R.id.document_host_container);
            android.widget.TextView indicator = findViewById(R.id.page_indicator);
            android.widget.ImageView preview = findViewById(R.id.page_scrub_preview);
            MuPDFReaderView docView = getDocView();
            if (tab == null || driver == null || docView == null) return;

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

            org.opendroidpdf.app.navigation.PageScrubberBinder.bind(
                    driver,
                    docView,
                    totalPages,
                    initialPage,
                    preview,
                    getMuPdfController(),
                    (pageIndex, pages, fromUser) -> {
                        if (!fromUser) return;
                        try {
                            tab.setText(String.format(java.util.Locale.getDefault(), "%d", pageIndex + 1));
                            tab.setContentDescription(
                                    String.format(java.util.Locale.getDefault(), "%s: %d / %d",
                                            getString(R.string.page_scrubber_tab),
                                            pageIndex + 1,
                                            pages));
                        } catch (Throwable ignore) {
                        }
                        try {
                            if (indicator != null) {
                                indicator.setText(String.format(java.util.Locale.getDefault(), "%d / %d  ▾", pageIndex + 1, pages));
                            }
                        } catch (Throwable ignore) {
                        }
                    },
                    this::markPageIndicatorNavHintSeen);

            tab.setOnClickListener(v -> {
                markPageIndicatorNavHintSeen();
                org.opendroidpdf.app.dialog.Dialogs.showGoToPage(this, mAlertBuilder, getDocView());
            });

            final int touchSlop = android.view.ViewConfiguration.get(tab.getContext()).getScaledTouchSlop();
            tab.setOnTouchListener(new android.view.View.OnTouchListener() {
                private boolean scrubbing = false;
                private float downRawY = 0f;
                private int mappingHeight = 0;
                private int mappingTopOnScreen = 0;
                private final int[] mappingLoc = new int[2];
                private int lastTarget = -1;

                private void updateMappingMetrics() {
                    android.view.View mappingHost = host != null ? host : tab;
                    try {
                        mappingHeight = mappingHost.getHeight();
                        mappingHost.getLocationOnScreen(mappingLoc);
                        mappingTopOnScreen = mappingLoc[1];
                    } catch (Throwable ignore) {
                        mappingHeight = 0;
                        mappingTopOnScreen = 0;
                    }
                }

                private int mapRawYToPageIndex(float rawY) {
                    if (mappingHeight <= 0) updateMappingMetrics();
                    if (mappingHeight <= 0) return 0;
                    float y = rawY - (float) mappingTopOnScreen;
                    if (y < 0f) y = 0f;
                    if (y > (float) mappingHeight) y = (float) mappingHeight;
                    float frac = y / (float) mappingHeight;
                    int max = Math.max(0, totalPages - 1);
                    int idx = Math.round(frac * (float) max);
                    if (idx < 0) idx = 0;
                    if (idx > max) idx = max;
                    return idx;
                }

                @Override
                public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                    if (event == null) return false;
                    int action = event.getActionMasked();
                    if (action == android.view.MotionEvent.ACTION_DOWN) {
                        downRawY = event.getRawY();
                        scrubbing = false;
                        updateMappingMetrics();
                        lastTarget = mapRawYToPageIndex(downRawY);
                        try { v.getParent().requestDisallowInterceptTouchEvent(true); } catch (Throwable ignore) {}
                        return true;
                    }
                    if (action == android.view.MotionEvent.ACTION_MOVE) {
                        float dy = Math.abs(event.getRawY() - downRawY);
                        if (!scrubbing && dy <= touchSlop) return true;
                        int target = mapRawYToPageIndex(event.getRawY());
                        if (!scrubbing) {
                            scrubbing = true;
                            markPageIndicatorNavHintSeen();
                            lastTarget = target;
                            org.opendroidpdf.app.navigation.PageScrubberBinder.beginUserScrub(driver, target);
                            return true;
                        }
                        if (target != lastTarget) {
                            lastTarget = target;
                            org.opendroidpdf.app.navigation.PageScrubberBinder.updateUserScrub(driver, target);
                        }
                        return true;
                    }
                    if (action == android.view.MotionEvent.ACTION_UP) {
                        if (scrubbing) {
                            int target = mapRawYToPageIndex(event.getRawY());
                            org.opendroidpdf.app.navigation.PageScrubberBinder.endUserScrub(driver, target);
                            scrubbing = false;
                            return true;
                        }
                        return v.performClick();
                    }
                    if (action == android.view.MotionEvent.ACTION_CANCEL) {
                        if (scrubbing) {
                            int target = mapRawYToPageIndex(event.getRawY());
                            org.opendroidpdf.app.navigation.PageScrubberBinder.endUserScrub(driver, target);
                            scrubbing = false;
                        }
                        return false;
                    }
                    return false;
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
