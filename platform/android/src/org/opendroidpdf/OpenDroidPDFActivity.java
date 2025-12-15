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

import org.opendroidpdf.app.DashboardFragment;
import org.opendroidpdf.app.DocumentHostFragment;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.document.DocumentHostController;
import org.opendroidpdf.app.document.DocumentNavigationController;
import org.opendroidpdf.app.document.DocumentSetupController;
import org.opendroidpdf.app.document.CoreInstanceCoordinator;
import org.opendroidpdf.app.document.DocumentLifecycleManager;
import org.opendroidpdf.app.document.DocumentToolbarController;
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
import org.opendroidpdf.app.search.SearchStateDelegate;
import org.opendroidpdf.app.annotation.AnnotationSelectionController;
import org.opendroidpdf.app.annotation.PenSettingsController;
import org.opendroidpdf.app.preferences.PenPreferences;
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
import org.opendroidpdf.app.services.ServiceLocator;
import java.util.concurrent.Callable;

public class OpenDroidPDFActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, FilePicker.FilePickerSupport, TemporaryUriPermission.TemporaryUriPermissionProvider, PenSettingsController.Host, DashboardFragment.DashboardHost, org.opendroidpdf.app.lifecycle.ActivityCompositionOwner {
    private static final String TAG = "OpenDroidPDFActivity";

    private ActivityComposition.Composition comp;
    private Uri mLastExportedUri = null;
    private AppServices appServices;
    private ServiceLocator serviceLocator;

    public SearchStateDelegate getSearchStateDelegate() { return comp != null ? comp.searchStateDelegate : null; }

    public void setLastExportedUri(Uri uri) { mLastExportedUri = uri; }
    public Uri getLastExportedUri() { return mLastExportedUri; }
    
    private final int    OUTLINE_REQUEST=0;
    private final int    PRINT_REQUEST=1;
    private final int    FILEPICK_REQUEST = 2;
    private final int    SAVEAS_REQUEST=3;
    private final int    EDIT_REQUEST = 4;

    public final static int    STORAGE_PERMISSION_REQUEST = 1001;
    public final static int    MANAGE_STORAGE_REQUEST = 1002;

    // Expose request codes for composition/adapters
    public int getEditRequestCode() { return EDIT_REQUEST; }
    public int getSaveAsRequestCode() { return SAVEAS_REQUEST; }
    public int getOutlineRequestCode() { return OUTLINE_REQUEST; }
    public int getPrintRequestCode() { return PRINT_REQUEST; }
    public int getManageStorageRequestCode() { return MANAGE_STORAGE_REQUEST; }

    public void setAwaitingManageStoragePermission(boolean awaiting) {
        if (serviceLocator != null) serviceLocator.permissions().setAwaitingManageStoragePermission(awaiting);
    }

    public boolean isAwaitingManageStoragePermission() {
        return serviceLocator != null && serviceLocator.permissions().isAwaitingManageStoragePermission();
    }

    public boolean isShowingStoragePermissionDialog() {
        return serviceLocator != null && serviceLocator.permissions().isShowingStoragePermissionDialog();
    }

    public void setShowingStoragePermissionDialog(boolean showing) {
        if (serviceLocator != null) serviceLocator.permissions().setShowingStoragePermissionDialog(showing);
    }

    public boolean ensureStoragePermission(Intent intent) {
        if (serviceLocator != null) return serviceLocator.permissions().ensureStoragePermissionForIntent(this, intent);
        return org.opendroidpdf.app.helpers.StoragePermissionHelper.ensureStoragePermissionForIntent(this, intent);
    }

    public boolean hasCore() { return facade != null && facade.hasCore(); }

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
    private AnnotationSelectionController annotationSelectionController = new AnnotationSelectionController();
    private AlertDialog.Builder mAlertBuilder;
    private FilePicker mFilePicker;
    private IntentResumeDelegate intentResumeDelegate;
    private LifecycleHooks lifecycleHooks;

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
    private Uri currentDocumentUri() { return facade != null ? facade.currentDocumentUri() : null; }

    private String currentDocumentName() { return facade != null ? facade.currentDocumentName() : getString(R.string.app_name); }

    private boolean canSaveToCurrentUri(OpenDroidPDFActivity activity) { return facade != null && facade.canSaveToCurrentUri(); }
    public boolean canSaveToCurrentUri() { return canSaveToCurrentUri(this); }

    public boolean hasUnsavedChanges() { return facade != null && facade.hasUnsavedChanges(); }
    

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
            serviceLocator = new ServiceLocator(comp, comp.documentNavigationController, documentLifecycleManager, comp.saveFlagController, comp.exportController);
            uiStateManager = new org.opendroidpdf.app.ui.UiStateManager(this, comp);
            alertUiManager = new org.opendroidpdf.app.ui.AlertUiManager(this, comp);
            facade = new ActivityFacade(this, documentLifecycleManager, uiStateManager, alertUiManager);
		
                // Preferences, alert builder, non-config core, and debug hooks
            org.opendroidpdf.app.lifecycle.StartupBootstrap.bootstrap(this);
            
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

    public void resetDocumentStateForIntent() { if (serviceLocator != null) serviceLocator.navigation().resetDocumentStateForIntent(); }

    public boolean isUriInAppPrivateStorage(Uri uri) { return org.opendroidpdf.app.util.PathUtils.isUriInAppPrivateStorage(this, uri); }

    public void openDocumentFromIntent(Intent intent) {
        Log.i(TAG, "openDocumentFromIntent(): data=" + intent.getData() + " type=" + intent.getType());
        if (serviceLocator != null) serviceLocator.navigation().openDocumentFromIntent(intent);
    }

    public void runAutotestIfNeeded(final Intent intent) { if (comp != null) comp.debugDelegate.runAutotestIfNeeded(this, mDocView, getRepository(), intent); }
    public boolean isAutoTestRanFlag() { return comp != null && comp.debugDelegate.isAutoTestRan(); }
    public void markAutoTestRanFlag() { if (comp != null) comp.debugDelegate.markAutoTestRan(); }

    @Override
    protected void onPause() {
        super.onPause();
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
        getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS)
                .unregisterOnSharedPreferenceChangeListener(this);
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

    public BackPressController.Mode getBackPressMode() { return ActionBarBackPressModeMapper.toBack(actionBarModeDelegate.current()); }
    public void setBackPressMode(BackPressController.Mode mode) { actionBarModeDelegate.set(ActionBarBackPressModeMapper.toActionBar(mode)); }
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
        SharedPreferences prefs = comp != null && comp.penPreferences != null
            ? comp.penPreferences.prefs()
            : getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS);
        onSharedPreferenceChanged(prefs, key);
    }

    @Override
    public Context getContext() {
        return this;
    }

    public androidx.appcompat.app.AppCompatActivity getActivity() {
        return this;
    }

    public boolean isSelectedAnnotationEditable() { return annotationSelectionController.isSelectedAnnotationEditable(); }
    public boolean isDrawingModeActive() { return mDocView != null && mDocView.getMode() == MuPDFReaderView.Mode.Drawing; }
    public boolean isErasingModeActive() { return mDocView != null && mDocView.getMode() == MuPDFReaderView.Mode.Erasing; }

    @Override
    public void finalizePendingInkBeforePenSettingChange() {
        org.opendroidpdf.AnnotationModeController.finalizePendingInkBeforePenSettingChange(mDocView);
    }

    // DashboardFragment.DashboardHost
    @Override public void onOpenDocumentRequested() { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onOpenDocumentRequested(); }
    @Override public void onCreateNewDocumentRequested() { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onCreateNewDocumentRequested(); }
    @Override public void onOpenSettingsRequested() { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onOpenSettingsRequested(); }
    @Override public void onRecentFileRequested(final RecentFile recentFile) { if (comp != null && comp.dashboardHostAdapter != null) comp.dashboardHostAdapter.onRecentFileRequested(recentFile); }

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
    
        
    public void setupSearchTaskManager() { // Is called during onResume()
        if (documentLifecycleManager != null) documentLifecycleManager.setupSearchTaskManager(mDocView);
    }

    public OpenDroidPDFCore getCore() { return coreCoordinator != null ? coreCoordinator.getCore() : null; }
    public AlertDialog.Builder alertBuilder() { return mAlertBuilder; }
    public SearchController getSearchController() { return coreCoordinator != null ? coreCoordinator.getSearchController() : null; }
    public MuPDFReaderView getDocView() { return mDocView; }

    // Expose recent files controller for adapters/controllers
    public org.opendroidpdf.app.document.RecentFilesController getRecentFilesController() {
        return coreCoordinator != null ? coreCoordinator.getRecentFilesController() : null;
    }
    public org.opendroidpdf.app.services.ServiceLocator.ExportService getExportService() {
        return serviceLocator != null ? serviceLocator.export() : null;
    }
    public org.opendroidpdf.app.notes.NotesController getNotesController() { return comp != null ? comp.notesController : null; }
    public org.opendroidpdf.app.document.DocumentNavigationController getDocumentNavigationController() { return comp != null ? comp.documentNavigationController : null; }
    public org.opendroidpdf.app.navigation.DashboardDelegate getDashboardDelegate() { return comp != null ? comp.dashboardDelegate : null; }
    public org.opendroidpdf.app.document.DocumentViewDelegate getDocumentViewDelegate() { return comp != null ? comp.documentViewDelegate : null; }
    public org.opendroidpdf.core.MuPdfController getMuPdfController() { return coreCoordinator != null ? coreCoordinator.getMuPdfController() : null; }
    public Uri getCurrentDocumentUri() { return coreCoordinator != null ? coreCoordinator.currentDocumentUri() : null; }
    public void setDocView(MuPDFReaderView docView) { this.mDocView = docView; }
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
    public void openDocument() { if (serviceLocator != null) serviceLocator.navigation().openDocument(); }

    // Helpers for DocViewFactory to adjust action bar state without exposing internals
    public org.opendroidpdf.app.ui.ActionBarMode getActionBarMode() { return actionBarModeDelegate.current(); }
    public org.opendroidpdf.app.annotation.PenSettingsController getPenSettingsController() { return comp != null ? comp.penSettingsController : null; }
    public boolean isActionBarModeEdit() { return actionBarModeDelegate.isEdit(); }
    public boolean isActionBarModeAddingTextAnnot() { return actionBarModeDelegate.isAddingTextAnnot(); }
    public boolean isActionBarModeSearchOrHidden() { return actionBarModeDelegate.isSearchOrHidden(); }
    public void setSelectedAnnotationEditable(boolean editable) { annotationSelectionController.setSelectedAnnotationEditable(editable); }
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
    }

    public void setSaveFlags(boolean saveOnStop, boolean saveOnDestroy, int numberRecentFiles) {
        if (comp != null && comp.saveFlagController != null) {
            comp.saveFlagController.setSaveFlags(saveOnStop, saveOnDestroy, numberRecentFiles);
        }
    }

    // Apply restored UI state from SavedStateHelper
    public void applySavedUiState(ActionBarMode mode,
                                  int pageBefore,
                                  float normScale,
                                  float normX,
                                  float normY,
                                  android.os.Parcelable docViewState,
                                  String latestSearch) {
        if (uiStateManager != null)
            uiStateManager.applySavedUiState(mode, pageBefore, normScale, normX, normY, docViewState, latestSearch, actionBarModeDelegate);
    }
    
    public void checkSaveThenCall(final Callable callable) { if (serviceLocator != null) serviceLocator.navigation().checkSaveThenCall(callable); }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {        
        if (serviceLocator != null) serviceLocator.permissions().resetAwaiting();
        if (comp != null && comp.activityResultRouter != null && comp.activityResultRouter.handle(requestCode, resultCode, intent)) return;
        super.onActivityResult(requestCode, resultCode, intent);
    }

    public void showSaveAsActivity() { if (serviceLocator != null) serviceLocator.navigation().showSaveAsActivity(); }

    private void cancelActiveSaveJob() {
        if (comp != null && comp.saveUiDelegate != null) comp.saveUiDelegate.cancelActiveSaveJob();
    }

    public org.opendroidpdf.app.document.SaveUiDelegate getSaveUiDelegate() { return comp != null ? comp.saveUiDelegate : null; }
    
    // save()/saveAs() moved to SaveUiController
    // Accessor for adapters/controllers
    public org.opendroidpdf.app.document.DocumentViewportController getViewportController() { return comp != null ? comp.viewportController : null; }


    public void saveRecentFiles(SharedPreferences prefs, final SharedPreferences.Editor edit, Uri uri) {
        if (comp != null && comp.viewportController != null) comp.viewportController.saveRecentFiles(prefs, edit, uri);
    }

    public void cancelRenderThumbnailJob() { if (comp != null && comp.viewportController != null) comp.viewportController.cancelRenderThumbnailJob(); }
    public void saveViewportAndRecentFiles(Uri uri) { if (comp != null && comp.viewportController != null) comp.viewportController.saveViewportAndRecentFiles(uri); }

    public void stopSearchTasks() {
        if (comp != null && comp.documentSetupController != null) {
            org.opendroidpdf.SearchTaskManager mgr = comp.documentSetupController.getSearchTaskManager();
            if (mgr != null) mgr.stop();
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
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key) {
        org.opendroidpdf.PreferenceApplier.handlePreferenceChanged(this, sharedPref, key);
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
    public void markIgnoreSaveOnStop() { if (comp != null && comp.saveFlagController != null) comp.saveFlagController.markIgnoreSaveOnStop(); }

    
    public void requestPassword() {
        OpenDroidPDFCore currentCore = getCore();
        if (comp != null && comp.passwordHostAdapter != null && currentCore != null && mAlertBuilder != null) {
            comp.passwordHostAdapter.requestPassword(currentCore, mAlertBuilder);
        }
    }


    // Go-to-page dialog is invoked directly by DocumentToolbarController.


    

    @Override
    public void onBackPressed() {
        boolean consumed = comp != null && comp.backPressController != null && comp.backPressController.onBackPressed();
        if (!consumed) super.onBackPressed();
    }

    // Mapping moved to ActionBarBackPressModeMapper

    
    @Override
    public void performPickFor(FilePicker picker) {
        mFilePicker = picker;
        Intent intent = new Intent(this, OpenDroidPDFFileChooser.class);
        startActivityForResult(intent, FILEPICK_REQUEST);
    }

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
        if (serviceLocator != null &&
            serviceLocator.permissions().handleRequestPermissionsResult(
                requestCode,
                grantResults,
                new Runnable() { @Override public void run() { openDocumentFromIntent(getIntent()); } },
                new Runnable() { @Override public void run() { Toast.makeText(OpenDroidPDFActivity.this, R.string.cannot_open_document, Toast.LENGTH_LONG).show(); } })) {
            return; // handled
        }
    }

    // dpToPixel/memory helpers moved to UiUtils
}
