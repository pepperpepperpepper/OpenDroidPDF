package org.opendroidpdf;
import java.util.concurrent.Callable;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.app.ActionBar;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import android.text.Editable;
import android.text.TextUtils;
import android.text.format.Time;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.graphics.Bitmap;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.RelativeLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Toast;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import android.graphics.PointF;
import android.widget.ViewAnimator;
import android.widget.TextView;
import android.widget.ImageView;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.Runtime;
import java.lang.Math;
import java.lang.Integer;
import java.util.ArrayList;
import java.util.Set;
import android.view.Gravity;
import android.print.PrintManager;
import android.print.PrintAttributes;
import android.view.Display;

import org.opendroidpdf.app.DashboardFragment;
import org.opendroidpdf.app.DocumentHostFragment;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.document.DocumentToolbarController;
import org.opendroidpdf.app.document.DocumentHostController;
import org.opendroidpdf.app.document.DocumentNavigationController;
import org.opendroidpdf.app.document.DocumentSetupController;
import org.opendroidpdf.app.document.SaveUiController;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.document.RecentFilesController;
import org.opendroidpdf.app.notes.NotesController;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.helpers.IntentRouter;
import org.opendroidpdf.app.helpers.StoragePermissionDialogHelper;
import org.opendroidpdf.app.helpers.UriPermissionHelper;
import org.opendroidpdf.app.helpers.StoragePermissionController;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.search.SearchActions;
import org.opendroidpdf.app.annotation.PenSettingsController;
import org.opendroidpdf.app.preferences.PenPreferences;
import org.opendroidpdf.app.toolbar.ToolbarStateController;
import org.opendroidpdf.app.lifecycle.LifecycleHooks;
import org.opendroidpdf.core.AlertController;
import org.opendroidpdf.app.alert.AlertDialogHelper;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.core.SaveCallback;
import org.opendroidpdf.core.SaveController;
import org.opendroidpdf.app.ui.ActionBarMode;
import org.opendroidpdf.core.SearchController;
import org.opendroidpdf.app.dashboard.DashboardController;
import org.opendroidpdf.app.navigation.NavigationController;
import org.opendroidpdf.app.navigation.BackPressController;
import org.opendroidpdf.app.navigation.BackPressHostAdapter;

public class OpenDroidPDFActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, FilePicker.FilePickerSupport, TemporaryUriPermission.TemporaryUriPermissionProvider, PenSettingsController.Host, DashboardFragment.DashboardHost, DocumentSetupController.Host
{       
    // ActionBarMode moved to org.opendroidpdf.app.ui.ActionBarMode
    private static final String TAG = "OpenDroidPDFActivity";
    private static final String NOTES_DIR_NAME = "OpenDroidPDFNotes";
    private static final String LEGACY_NOTES_DIR_NAME = "PenAndPDFNotes";
    
    private SearchToolbarController searchToolbarController;
    private SearchActions searchActions;
    private String latestTextInSearchBox = "";
    private String textOfLastSearch = "";
    private boolean mSaveOnStop = false;
    private boolean mSaveOnDestroy = false;
    private boolean mIgnoreSaveOnStopThisTime = false;
    private boolean mIgnoreSaveOnDestroyThisTime = false;
    private boolean mDocViewNeedsNewAdapter = false;
    private org.opendroidpdf.app.navigation.LinkBackState linkBackState = new org.opendroidpdf.app.navigation.LinkBackState();
    private int numberRecentFilesInMenu = 20;
    private Uri mLastExportedUri = null;
    private boolean mAutoTestRan = false; // debug-only autotest flag
    private AppServices appServices;

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

    // migrated into StoragePermissionController
    private boolean awaitingManageStoragePermission = false; // kept for compatibility; delegated
    private boolean showingStoragePermissionDialog = false; // kept for compatibility; delegated
    private boolean preparingOptionsMenu = false;
    public boolean isPreparingOptionsMenu() { return preparingOptionsMenu; }

    public void setAwaitingManageStoragePermission(boolean awaiting) {
        if (storagePermissionController != null) storagePermissionController.setAwaitingManageStoragePermission(awaiting);
        this.awaitingManageStoragePermission = awaiting;
    }

    public boolean isAwaitingManageStoragePermission() {
        return storagePermissionController != null ? storagePermissionController.isAwaitingManageStoragePermission() : awaitingManageStoragePermission;
    }

    public boolean isShowingStoragePermissionDialog() {
        return storagePermissionController != null ? storagePermissionController.isShowingStoragePermissionDialog() : showingStoragePermissionDialog;
    }

    public void setShowingStoragePermissionDialog(boolean showing) {
        if (storagePermissionController != null) storagePermissionController.setShowingStoragePermissionDialog(showing);
        this.showingStoragePermissionDialog = showing;
    }

    public boolean ensureStoragePermission(Intent intent) {
        if (storagePermissionController != null) return storagePermissionController.ensureForIntent(this, intent);
        return org.opendroidpdf.app.helpers.StoragePermissionHelper.ensureStoragePermissionForIntent(this, intent);
    }

    public boolean hasCore() {
        return core != null;
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
    
    private OpenDroidPDFCore    core;
    private MuPdfRepository muPdfRepository;
    private MuPdfController muPdfController;
    private SearchController searchController;
    private MuPDFReaderView mDocView;
    private AnnotationToolbarController annotationToolbarController;
    private DocumentToolbarController documentToolbarController;
    private DocumentNavigationController documentNavigationController;
    private DocumentSetupController documentSetupController;
    private PenPreferences penPreferences;
    private PenSettingsController penSettingsController;
    private ExportController exportController;
    private NotesController notesController;
    private IntentRouter intentRouter;
    private ToolbarStateController toolbarStateController;
    private org.opendroidpdf.app.ui.FullscreenController fullscreenController;
    Parcelable mDocViewParcelable;
    // Password prompt now handled via PasswordDialogHelper
    private ActionBarMode  mActionBarMode = ActionBarMode.Empty;
    private boolean selectedAnnotationIsEditable = false;
    private SearchTaskManager   mSearchTaskManager;
    private AlertDialog.Builder mAlertBuilder;
    private boolean    mLinkHighlight = false;
    // MuPDF alert plumbing moved to AlertDialogHelper.
    private boolean mReflow = false;
    private AlertController alertController;
    // thumbnail render state moved to RecentFilesController
    private FilePicker mFilePicker;
    private final SaveController saveController = new SaveController(); // kept for other hosts; SaveUiController wraps usage
    private AlertDialogHelper alertDialogHelper;
    private SaveUiController saveUiController;
    
    private ArrayList<TemporaryUriPermission> temporaryUriPermissions = new ArrayList<TemporaryUriPermission>();

    private DashboardController dashboardController;
    private DocumentHostController documentHostController;
    private NavigationController navigationController;
    private BackPressController backPressController;
    private org.opendroidpdf.app.helpers.ActivityResultRouter activityResultRouter;
    private StoragePermissionController storagePermissionController;
    private RecentFilesController recentFilesController;
    private LifecycleHooks lifecycleHooks;
    private org.opendroidpdf.app.document.DocumentViewportController viewportController;
    private org.opendroidpdf.app.hosts.DashboardHostAdapter dashboardHostAdapter;
    private org.opendroidpdf.app.hosts.PasswordHostAdapter passwordHostAdapter;

    public void setCoreInstance(OpenDroidPDFCore newCore) {
        destroyAlertWaiter();
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
                    new org.opendroidpdf.app.hosts.AlertHostAdapter(this, mAlertBuilder),
                    alertController);
            recentFilesController = new RecentFilesController(this, muPdfRepository, muPdfController);
        } else {
            muPdfRepository = null;
            muPdfController = null;
            searchController = null;
            alertController = null;
            alertDialogHelper = null;
            if (recentFilesController != null) {
                recentFilesController.shutdown();
                recentFilesController = null;
            }
        }
    }

    // Alert host moved to org.opendroidpdf.app.hosts.AlertHostAdapter

    private boolean hasRepository() {
        return muPdfRepository != null;
    }

    // Exposed for host adapters/controllers
    public org.opendroidpdf.core.MuPdfRepository getRepository() {
        return muPdfRepository;
    }

    @Nullable
    private Uri currentDocumentUri() {
        if (muPdfRepository != null) {
            return muPdfRepository.getDocumentUri();
        }
        return core != null ? core.getUri() : null;
    }

    private String currentDocumentName() {
        if (muPdfRepository != null) {
            return muPdfRepository.getDocumentName();
        }
        return core != null ? core.getFileName() : getString(R.string.app_name);
    }

    private boolean canSaveToCurrentUri(OpenDroidPDFActivity activity) {
        return core != null && core.canSaveToCurrentUri(activity);
    }
    public boolean canSaveToCurrentUri() { return canSaveToCurrentUri(this); }

    public boolean hasUnsavedChanges() {
        if (muPdfRepository != null) {
            return muPdfRepository.hasUnsavedChanges();
        }
        return core != null && core.hasChanges();
    }
    
		
/**
 * Code from http://stackoverflow.com/questions/13209494/how-to-get-the-full-file-path-from-uri
 * 
 * Get a file path from a Uri. This will get the the path for Storage Access
 * Framework Documents, as well as the _data field for the MediaStore and
 * other file-based ContentProviders.
 *
 * @param context The context.
 * @param uri The Uri to query.
 * @author paulburke
 */
// Moved to UriPathResolver to shrink this activity
	
    public void createAlertWaiter() {
        destroyAlertWaiter();
        if (alertDialogHelper != null) {
            alertDialogHelper.start();
        }
    }

    // MuPDF alert UI handled by AlertDialogHelper

    public void destroyAlertWaiter() {
        if (alertDialogHelper != null) {
            alertDialogHelper.stop();
        }
    }

    
    @Override
    public void onCreate(Bundle savedInstanceState)
        {
                //Treat the bundle with the SaveInstanceStateManager before calling through to super
            savedInstanceState = SaveInstanceStateManager.recoverBundleIfNecessary(savedInstanceState, getClass().getClassLoader());
            
            super.onCreate(savedInstanceState);

			//Initialize the layout
        setContentView(R.layout.main);
        Toolbar myToolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
            annotationToolbarController = new AnnotationToolbarController(new org.opendroidpdf.app.hosts.AnnotationToolbarHostAdapter(this));
            searchToolbarController = new SearchToolbarController(new org.opendroidpdf.app.hosts.SearchToolbarHostAdapter(this));
            searchActions = new SearchActions();
            documentToolbarController = new DocumentToolbarController(
                    new org.opendroidpdf.app.hosts.DocumentToolbarHostAdapter(this));

            org.opendroidpdf.app.lifecycle.ActivityComposition.Composition comp =
                    org.opendroidpdf.app.lifecycle.ActivityComposition.setup(this);
            appServices = comp.appServices;
            penPreferences = comp.penPreferences;
            penSettingsController = comp.penSettingsController;
            exportController = comp.exportController;
            notesController = comp.notesController;
            intentRouter = comp.intentRouter;
            toolbarStateController = comp.toolbarStateController;
            dashboardController = comp.dashboardController;
            documentHostController = comp.documentHostController;
            storagePermissionController = comp.storagePermissionController;
            navigationController = comp.navigationController;
            activityResultRouter = comp.activityResultRouter;
            documentNavigationController = comp.documentNavigationController;
            documentSetupController = comp.documentSetupController;
            viewportController = new org.opendroidpdf.app.document.DocumentViewportController(
                    new org.opendroidpdf.app.hosts.ViewportHostAdapter(this));
            dashboardHostAdapter = new org.opendroidpdf.app.hosts.DashboardHostAdapter(this, documentNavigationController);
            passwordHostAdapter = new org.opendroidpdf.app.hosts.PasswordHostAdapter(this);
		
                // Preferences, alert builder, non-config core, and debug hooks
            org.opendroidpdf.app.lifecycle.StartupBootstrap.bootstrap(this);
            
                // Restore dynamic UI state from saved instance (action bar mode, scroll, etc.)
            org.opendroidpdf.app.lifecycle.SavedStateHelper.restore(this, savedInstanceState);
        }
    
    @Override
    protected void onResume()
        {
            super.onResume();

            Intent intent = getIntent();
            String action = intent != null ? intent.getAction() : null;
            Uri data = intent != null ? intent.getData() : null;
            Log.i(TAG, "onResume(): action=" + action + " data=" + data);

            if (intentRouter != null && intentRouter.handleOnResume(intent)) {
                invalidateOptionsMenuSafely();
                return;
            }

            invalidateOptionsMenuSafely();
        }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) {
            return;
        }
        setIntent(intent);
        Log.i(TAG, "onNewIntent(): action=" + intent.getAction() + " data=" + intent.getData());
        if (intentRouter != null && intentRouter.handleOnNewIntent(intent)) {
            return;
        }
    }

    // Small accessors used by host adapters
    public SearchTaskManager getSearchTaskManager() { return mSearchTaskManager; }

    public void resetDocumentStateForIntent() {
        if (core != null) {
            core.onDestroy();
            setCoreInstance(null);
        }
        mDocViewNeedsNewAdapter = true;
    }

    public boolean isUriInAppPrivateStorage(Uri uri) {
        return org.opendroidpdf.app.util.PathUtils.isUriInAppPrivateStorage(this, uri);
    }

    private void showStoragePermissionExplanation(@StringRes int messageResId, final Runnable onContinue)
    {
        showingStoragePermissionDialog = StoragePermissionDialogHelper.show(this, showingStoragePermissionDialog, messageResId, onContinue);
    }

    public void openDocumentFromIntent(Intent intent)
    {
        Log.i(TAG, "openDocumentFromIntent(): data=" + intent.getData() + " type=" + intent.getType());
        if (documentNavigationController != null) {
            documentNavigationController.openDocumentFromIntent(intent);
        }
    }

    public void runAutotestIfNeeded(final Intent intent) {
        if (mDocView == null || muPdfRepository == null) return;
        org.opendroidpdf.DebugAutotestRunner.runIfNeeded(
                new org.opendroidpdf.app.hosts.DebugAutotestHostAdapter(this, muPdfRepository, mDocView),
                intent);
    }

    // Expose tiny helpers for debug host adapter
    public boolean isAutoTestRanFlag() { return mAutoTestRan; }
    public void markAutoTestRanFlag() { mAutoTestRan = true; }

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
        if (alertController != null) { alertController.shutdown(); alertController = null; }
        ensureSaveUiController();
        saveUiController.cancelActiveSaveJob();
        if (searchToolbarController != null) { searchToolbarController.detach(); }
    }

    private void ensureLifecycleHooks() {
        if (lifecycleHooks != null) return;
        lifecycleHooks = new LifecycleHooks(new LifecycleHooks.Host() {
            @Override public void stopSearchTasks() {
                if (mSearchTaskManager != null) mSearchTaskManager.stop();
            }
            @Override public boolean hasCore() { return core != null; }
            @Override public android.net.Uri coreUri() { return core != null ? core.getUri() : null; }
            @Override public void saveViewport(@NonNull android.net.Uri uri) { OpenDroidPDFActivity.this.saveViewport(uri); }
            @Override public void coreStopAlerts() { if (core != null) core.stopAlerts(); }
            @Override public void destroyAlertWaiter() { OpenDroidPDFActivity.this.destroyAlertWaiter(); }

            @Override public boolean isChangingConfigurations() { return OpenDroidPDFActivity.this.isChangingConfigurations(); }
            @Override public boolean hasUnsavedChanges() { return OpenDroidPDFActivity.this.hasUnsavedChanges(); }
            @Override public boolean getSaveOnStop() { return mSaveOnStop; }
            @Override public boolean getIgnoreSaveOnStopThisTime() { return mIgnoreSaveOnStopThisTime; }
            @Override public void clearIgnoreSaveOnStopFlag() { mIgnoreSaveOnStopThisTime = false; }
            @Override public boolean canSaveToCurrentUri() { return OpenDroidPDFActivity.this.canSaveToCurrentUri(OpenDroidPDFActivity.this); }
            @Override public void saveInBackground(Callable<?> ok, Callable<?> err) { OpenDroidPDFActivity.this.saveInBackground(ok, err); }
            @Override public void showInfo(@NonNull String message) { OpenDroidPDFActivity.this.showInfo(message); }
            @Override public void cancelRenderThumbnailJob() { OpenDroidPDFActivity.this.cancelRenderThumbnailJob(); }
            @Override public boolean getSaveOnDestroy() { return mSaveOnDestroy; }
            @Override public boolean getIgnoreSaveOnDestroyThisTime() { return mIgnoreSaveOnDestroyThisTime; }
            @Override public void clearIgnoreSaveOnDestroyFlag() { mIgnoreSaveOnDestroyThisTime = false; }
            @Override public void destroyCoreNow() {
                if (core != null) {
                    core.onDestroy();
                    setCoreInstance(null);
                }
            }
        });
    }

    // Expose small helpers for back-press adapter mapping and finish flags
    public BackPressController.Mode getBackPressMode() { return ActionBarBackPressModeMapper.toBack(mActionBarMode); }
    public void setBackPressMode(BackPressController.Mode mode) { mActionBarMode = ActionBarBackPressModeMapper.toActionBar(mode); }
    public void setIgnoreSaveFlagsForFinish() { mIgnoreSaveOnStopThisTime = true; mIgnoreSaveOnDestroyThisTime = true; }
    public SearchToolbarController getSearchToolbarController() { return searchToolbarController; }
    // Removed one-line public wrappers; corresponding methods are now public
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) //Inflates the options menu
        {
            super.onCreateOptionsMenu(menu);

            if(dashboardIsShown())
                mActionBarMode = ActionBarMode.Empty;
            
            final MenuInflater inflater = getMenuInflater();
            if (toolbarStateController != null) {
                // Reset search text when entering Search mode, mirroring legacy behavior
                if (mActionBarMode == ActionBarMode.Search) {
                    textOfLastSearch = "";
                }
                toolbarStateController.onCreateOptionsMenuFromActionBarMode(
                        mActionBarMode,
                        menu,
                        inflater,
                        documentToolbarController,
                        annotationToolbarController,
                        searchToolbarController);
            } else {
                // Fallback: inflate an empty menu to avoid NPEs if controller is unavailable
                inflater.inflate(R.menu.empty_menu, menu);
            }
            return true;
        }

    // SearchToolbarController.Host — search state + UI hooks implemented here
    public void setLatestSearchQuery(@NonNull CharSequence query) {
        latestTextInSearchBox = query != null ? query.toString() : "";
    }

    public @NonNull CharSequence getTextOfLastSearch() {
        return textOfLastSearch != null ? textOfLastSearch : "";
    }

    public void setTextOfLastSearch(@NonNull CharSequence query) {
        textOfLastSearch = query != null ? query.toString() : "";
    }



    // search helpers are handled by SearchToolbarHostAdapter; direct view accessors removed

    public void setViewingMode() { if (mDocView != null) mDocView.setMode(MuPDFReaderView.Mode.Viewing); }

    public void exitSearchModeToMain() { mActionBarMode = ActionBarMode.Main; }

    public void stopSearchTaskIfRunning() { if (mSearchTaskManager != null) mSearchTaskManager.stop(); }

    public void performSearch(int direction) { search(direction); }

    // Small helpers used by search adapters
    public @NonNull CharSequence getLatestSearchQuery() { return latestTextInSearchBox; }
    public void onSearchNavigate(int direction) {
        if (!TextUtils.isEmpty(latestTextInSearchBox)) {
            hideKeyboard();
            search(direction);
        }
    }

    // ink color dialog handled by PenSettingsController via adapters

    public boolean isCurrentNoteDocument() {
        Intent intent = getIntent();
        if (intent == null || intent.getData() == null) {
            return false;
        }
        String encodedPath = intent.getData().getEncodedPath();
        if (encodedPath == null) {
            return false;
        }
        File recentFile = new File(Uri.decode(encodedPath));
        File notesDir = getNotesDir(this);
        return notesDir != null
            && recentFile != null
            && recentFile.getAbsolutePath().startsWith(notesDir.getAbsolutePath());
    }

    public boolean hasDocumentLoaded() {
        return hasRepository();
    }

    public boolean isViewingNoteDocument() {
        return isCurrentNoteDocument();
    }

    public boolean isLinkBackAvailable() {
        return linkBackState.isAvailable();
    }

    // Expose narrow accessors for link-back adapter
    public int getLinkBackPage() { return linkBackState.page(); }
    public float getLinkBackScale() { return linkBackState.scale(); }
    public float getLinkBackX() { return linkBackState.normX(); }
    public float getLinkBackY() { return linkBackState.normY(); }
    public void clearLinkBackTarget() { linkBackState.clear(); }

    // requestGoToPageDialog removed; DocumentToolbarController renders the dialog directly.

    // pen size dialog handled by AnnotationToolbarHostAdapter

    @Override
    public void onPenPreferenceChanged(String key) {
        SharedPreferences prefs = penPreferences != null
            ? penPreferences.prefs()
            : getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS);
        onSharedPreferenceChanged(prefs, key);
    }

    @Override
    public Context getContext() {
        return this;
    }

    // DocumentToolbarController.Host hook
    public androidx.appcompat.app.AppCompatActivity getActivity() {
        return this;
    }

    

    // annotation info handled via adapters calling showInfo()

    public boolean isSelectedAnnotationEditable() { return selectedAnnotationIsEditable; }
    public boolean isDrawingModeActive() { return mDocView != null && mDocView.getMode() == MuPDFReaderView.Mode.Drawing; }
    public boolean isErasingModeActive() { return mDocView != null && mDocView.getMode() == MuPDFReaderView.Mode.Erasing; }

    // hasDocumentView() not needed; adapters check getDocView() directly

    // annotation mode helpers are handled by AnnotationToolbarHostAdapter

    @Override
    public void finalizePendingInkBeforePenSettingChange() {
        org.opendroidpdf.AnnotationModeController.finalizePendingInkBeforePenSettingChange(mDocView);
    }

    // DashboardFragment.DashboardHost
    @Override public void onOpenDocumentRequested() { if (dashboardHostAdapter != null) dashboardHostAdapter.onOpenDocumentRequested(); }
    @Override public void onCreateNewDocumentRequested() { if (dashboardHostAdapter != null) dashboardHostAdapter.onCreateNewDocumentRequested(); }
    @Override public void onOpenSettingsRequested() { if (dashboardHostAdapter != null) dashboardHostAdapter.onOpenSettingsRequested(); }
    @Override public void onRecentFileRequested(final RecentFile recentFile) { if (dashboardHostAdapter != null) dashboardHostAdapter.onRecentFileRequested(recentFile); }

    @Override
    public boolean isMemoryLow() {
        if (dashboardHostAdapter != null) return dashboardHostAdapter.isMemoryLow();
        return org.opendroidpdf.app.ui.UiUtils.isMemoryLow(this);
    }

    @Override
    public int maxRecentFiles() {
        return numberRecentFilesInMenu;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { //Handle clicks in the options menu
        if (org.opendroidpdf.BuildConfig.DEBUG) {
            if (org.opendroidpdf.app.debug.DebugActionsController.onOptionsItemSelected(this, item)) {
                return true;
            }
        }
        if (documentToolbarController != null &&
            documentToolbarController.handleMenuItem(item)) {
            return true;
        }

        if (annotationToolbarController != null &&
            annotationToolbarController.handleOptionsItem(item)) {
            return true;
        }

        if (searchToolbarController != null &&
            searchToolbarController.handleMenuItem(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        preparingOptionsMenu = true;
        try {
            if (toolbarStateController != null) {
                toolbarStateController.onPrepareOptionsMenu(menu);
            }
            return super.onPrepareOptionsMenu(menu);
        } finally {
            preparingOptionsMenu = false;
        }
    }

    public void invalidateOptionsMenuSafely() {
        if (!preparingOptionsMenu) {
            try { invalidateOptionsMenu(); } catch (Throwable ignore) {}
        }
    }

    // mapToolbarMode removed; ToolbarStateController handles ActionBarMode mapping directly.

	public void tryToTakePersistablePermissions(Intent intent) {
        Uri uri = intent.getData();
        UriPermissionHelper.tryTakePersistablePermissions(this, uri);
	}
	

    public void setupCore() { // Called during onResume()
        if (core == null) {
            mDocViewNeedsNewAdapter = true;
            final Intent intent = getIntent();
            final Uri uri = intent != null ? intent.getData() : null;
            Log.i(TAG, "setupCore(): intent=" + intent + " uri=" + uri);
            if (documentSetupController != null) {
                documentSetupController.setupCore(this, uri);
            }
        }
    }
    
        
    public void setupSearchTaskManager() { // Is called during onResume()
        if (documentSetupController != null && mDocView != null) {
            documentSetupController.setupSearchTaskManager(mDocView);
        } else {
            // Controller should always be present; if not, clear any prior manager.
            mSearchTaskManager = null;
        }
    }

    // DocumentSetupController.Host
    @Override
    public OpenDroidPDFCore getCore() { return core; }

    @Override
    public AlertDialog.Builder alertBuilder() { return mAlertBuilder; }

    @Override
    public SearchController getSearchController() { return searchController; }

    @Override
    public void setSearchTaskManager(SearchTaskManager mgr) { mSearchTaskManager = mgr; }

    @Override
    public MuPDFReaderView getDocView() { return mDocView; }

    // Expose recent files controller for adapters/controllers
    public org.opendroidpdf.app.document.RecentFilesController getRecentFilesController() { return recentFilesController; }
    public org.opendroidpdf.app.document.ExportController getExportController() { return exportController; }
    public org.opendroidpdf.app.notes.NotesController getNotesController() { return notesController; }
    public org.opendroidpdf.app.document.DocumentNavigationController getDocumentNavigationController() { return documentNavigationController; }

    @Override
    public void onSearchTaskReady(SearchTaskManager mgr) { /* no-op hook for now */ }

    @Override
    public void onDocViewReady() { /* no-op hook for now; activity already wires listeners */ }
    
    public void setupDocView() { // delegate orchestration
        if (documentSetupController != null) {
            documentSetupController.setupDocView(this);
            return;
        }
        // Defensive fallback: controller missing; skipping setup
    }

    // DocumentSetupController.Host — lifecycle hooks used during setup
    public void onDocViewAttached() {
        if (mDocView == null) return;
        // Keep mode in sync and clear stale search markers once attached
        mDocView.setMode(mDocView.getMode());
        mDocView.clearSearchResults();
    }

    // DocumentSetupController.Host — container and view creation
    public android.view.ViewGroup ensureDocumentContainer() {
        return navigationController != null ? navigationController.ensureDocumentContainer() : null;
    }

    public void createDocViewIfNeeded() {
        if (core == null || mDocView != null) return;
        mDocView = org.opendroidpdf.DocViewFactory.create(this);
        mDocViewNeedsNewAdapter = true;
    }

    // Dashboard wrappers for controllers/routers
    public boolean dashboardIsShown() {
        return navigationController != null && navigationController.dashboardIsShown();
    }

    public void showDashboard() {
        if (navigationController != null) navigationController.showDashboard();
    }

    public void hideDashboard() {
        if (navigationController != null) navigationController.hideDashboard();
    }

    // Attach the document view to the fragment container
    public void attachDocViewToContainer(android.view.ViewGroup container) {
        if (navigationController != null) navigationController.attachDocViewToContainer(container, mDocView);
    }

    // Create a new blank note and open it
    public void openNewDocument(final String filename) throws java.io.IOException {
        java.io.File dir = getNotesDir(this);
        java.io.File file = new java.io.File(dir, filename);
        final android.net.Uri uri = android.net.Uri.fromFile(file);
        OpenDroidPDFCore.createEmptyDocument(this, uri);
        checkSaveThenCall(new java.util.concurrent.Callable<Void>() {
            public Void call() {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, uri, getApplicationContext(), OpenDroidPDFActivity.class);
                intent.putExtra(android.content.Intent.EXTRA_TITLE, filename);
                intent.setFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION|android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION|android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                hideDashboard();
                finish();
                return null;
            }});
    }

    // Simple entry to document picker (delegates to controller)
    public void openDocument() {
        if (documentNavigationController != null) {
            documentNavigationController.openDocument();
        }
    }

    // DocumentSetupController.Host: action bar/top padding info
    public int getActionBarHeightPx() {
        try {
            if (getSupportActionBar() != null && getSupportActionBar().isShowing()) {
                TypedValue tv = new TypedValue();
                if (getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
                    return TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
                }
            }
        } catch (Throwable ignore) { }
        return 0;
    }

    // DocumentSetupController.Host: adapter/viewport/prefs wiring
    public void ensureDocAdapter() {
        if (mDocView == null) return;
        if (mDocViewNeedsNewAdapter) {
            if (muPdfController == null && muPdfRepository != null) {
                muPdfController = new MuPdfController(muPdfRepository);
            }
            mDocView.setAdapter(new MuPDFPageAdapter(this, this, muPdfController));
            mDocViewNeedsNewAdapter = false;
        }
    }

    public void restoreViewportIfAny() { if (viewportController != null) viewportController.restoreViewport(); }

    public void restoreDocViewStateIfAny() {
        if (mDocViewParcelable != null && mDocView != null) {
            mDocView.onRestoreInstanceState(mDocViewParcelable);
        }
        mDocViewParcelable = null;
    }

    public void syncDocViewPreferences() {
        if (mDocView == null) return;
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        mDocView.onSharedPreferenceChanged(prefs, "");
    }

    // Helpers for DocViewFactory to adjust action bar state without exposing internals
    public void setActionBarModeMain() { mActionBarMode = ActionBarMode.Main; }
    public void setActionBarModeAnnot() { mActionBarMode = ActionBarMode.Annot; }
    public void setActionBarModeSelection() { mActionBarMode = ActionBarMode.Selection; }
    public void setActionBarModeAddingTextAnnot() { mActionBarMode = ActionBarMode.AddingTextAnnot; }
    public void setActionBarModeEdit() { mActionBarMode = ActionBarMode.Edit; }
    public void setActionBarModeSearch() { mActionBarMode = ActionBarMode.Search; }
    public org.opendroidpdf.app.ui.ActionBarMode getActionBarMode() { return mActionBarMode; }
    public org.opendroidpdf.app.annotation.PenSettingsController getPenSettingsController() { return penSettingsController; }
    public boolean isActionBarModeEdit() { return mActionBarMode == ActionBarMode.Edit; }
    public boolean isActionBarModeAddingTextAnnot() { return mActionBarMode == ActionBarMode.AddingTextAnnot; }
    public boolean isActionBarModeSearchOrHidden() { return mActionBarMode == ActionBarMode.Search || mActionBarMode == ActionBarMode.Hidden; }
    public void setSelectedAnnotationEditable(boolean editable) { selectedAnnotationIsEditable = editable; }
    public androidx.appcompat.app.AlertDialog.Builder getAlertBuilder() { return mAlertBuilder; }
    public void setAlertBuilder(androidx.appcompat.app.AlertDialog.Builder b) { mAlertBuilder = b; }
    public void rememberPreLinkHitViewport(int page, float scale, float x, float y) {
        linkBackState.remember(page, scale, x, y);
    }

    // For StartupBootstrap: set core from last non-config without reinitializing controllers
    public void setCoreFromLastNonConfig(OpenDroidPDFCore last) {
        core = last;
        if (core != null) mDocViewNeedsNewAdapter = true;
    }

    // Apply restored UI state from SavedStateHelper
    public void applySavedUiState(ActionBarMode mode,
                                  int pageBefore,
                                  float normScale,
                                  float normX,
                                  float normY,
                                  android.os.Parcelable docViewState,
                                  String latestSearch) {
        mActionBarMode = (mode != null) ? mode : ActionBarMode.Main;
        linkBackState.remember(pageBefore, normScale, normX, normY);
        mDocViewParcelable = docViewState;
        if (latestSearch != null) latestTextInSearchBox = latestSearch;
        invalidateOptionsMenuSafely();
    }
    
    public void checkSaveThenCall(final Callable callable) {
        if (documentNavigationController != null) {
            documentNavigationController.checkSaveThenCall(callable);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {        
        if (storagePermissionController != null) storagePermissionController.resetAwaiting(); else awaitingManageStoragePermission = false;
        if (activityResultRouter != null && activityResultRouter.handle(requestCode, resultCode, intent)) return;
        super.onActivityResult(requestCode, resultCode, intent);
    }

    public void showSaveAsActivity() {
        if (documentNavigationController != null) {
            mIgnoreSaveOnStopThisTime = true;
            documentNavigationController.showSaveAsActivity();
        }
    }

    private void saveAsInBackground(final Uri uri, final Callable successCallable, final Callable failureCallable) {
        ensureSaveUiController();
        saveUiController.saveAsInBackground(uri, successCallable, failureCallable);
    }

    // Public wrapper for host adapters with wildcard Callable types
    public void saveAsInBackgroundCompat(final Uri uri, final java.util.concurrent.Callable<?> successCallable, final java.util.concurrent.Callable<?> failureCallable) {
        ensureSaveUiController();
        saveUiController.saveAsInBackground(uri, (java.util.concurrent.Callable) successCallable, (java.util.concurrent.Callable) failureCallable);
    }
    
    public void saveInBackground(final Callable successCallable, final Callable failureCallable) {
        ensureSaveUiController();
        saveUiController.saveInBackground(successCallable, failureCallable);
    }

    public void callInBackgroundAndShowDialog(final String message, final Callable<Exception> saveCallable, final Callable successCallable, final Callable failureCallable) {
        ensureSaveUiController();
        saveUiController.callInBackgroundAndShowDialog(message, saveCallable, successCallable, failureCallable);
    }

    private void cancelActiveSaveJob() {
        ensureSaveUiController();
        saveUiController.cancelActiveSaveJob();
    }
    
    // save()/saveAs() moved to SaveUiController

    private void ensureSaveUiController() {
        if (saveUiController != null) return;
        saveUiController = new SaveUiController(new org.opendroidpdf.app.hosts.SaveUiHostAdapter(this));
    }
    private void restoreViewport() {
        if (core == null) return;
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        org.opendroidpdf.app.document.ViewportHelper.restoreViewport(mDocView, recentFilesController, prefs, core.getUri());
    }


    private void setViewport(SharedPreferences prefs, Uri uri) {
        org.opendroidpdf.app.document.ViewportHelper.setViewport(mDocView, recentFilesController, prefs, uri);
    }

    
    private void setViewport(int page, float normalizedscale, float normalizedxscroll, float normalizedyscroll) {
        org.opendroidpdf.app.document.ViewportHelper.setViewport(mDocView, recentFilesController, page, normalizedscale, normalizedxscroll, normalizedyscroll);
    }

    // Accessor for adapters/controllers
    public org.opendroidpdf.app.document.DocumentViewportController getViewportController() { return viewportController; }


    public void saveRecentFiles(SharedPreferences prefs, final SharedPreferences.Editor edit, Uri uri) {
        if (viewportController != null) viewportController.saveRecentFiles(prefs, edit, uri);
    }

    private void cancelRenderThumbnailJob() { if (viewportController != null) viewportController.cancelRenderThumbnailJob(); }
    
    
    public void saveViewportAndRecentFiles(Uri uri) {
        if (viewportController != null) viewportController.saveViewportAndRecentFiles(uri);
    }
    

    private void saveViewport(Uri uri) { if (viewportController != null) viewportController.saveViewport(uri); }

    
    @Override
    public Object onRetainCustomNonConfigurationInstance() { //Called if the app is destroyed for a configuration change
        OpenDroidPDFCore mycore = core;
        setCoreInstance(null);
        return mycore;
    }
    
    
    @Override
    protected void onSaveInstanceState(Bundle outState) { //Called when the app is destroyed by the system and in various other cases
        super.onSaveInstanceState(outState);

        outState.putString("ActionBarMode", mActionBarMode.toString());
        outState.putInt("PageBeforeInternalLinkHit", linkBackState.page());
        outState.putFloat("NormalizedScaleBeforeInternalLinkHit", linkBackState.scale());
        outState.putFloat("NormalizedXScrollBeforeInternalLinkHit", linkBackState.normX());
        outState.putFloat("NormalizedYScrollBeforeInternalLinkHit", linkBackState.normY());
        if(mDocView != null) outState.putParcelable("mDocView", mDocView.onSaveInstanceState());
        outState.putString("latestTextInSearchBox", latestTextInSearchBox);

            //Treat the bundle with the SaveInstanceStateManager before saving it
        SaveInstanceStateManager.saveBundleIfNecessary(outState);
    }        
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key) {
        org.opendroidpdf.PreferenceApplier.State st = org.opendroidpdf.PreferenceApplier.compute(this, getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS));
        org.opendroidpdf.PreferenceApplier.applyKeepScreenOn(this, st.keepScreenOn);
        mSaveOnStop = st.saveOnStop;
        mSaveOnDestroy = st.saveOnDestroy;
        numberRecentFilesInMenu = st.numberRecentFiles;
        org.opendroidpdf.PreferenceApplier.applyToViews(sharedPref, key, mDocView, core, this);
    }

    
    // printDoc/shareDoc now handled by ExportController

    // Flush any currently drawn but not yet committed ink on the active page
    // into the MuPDF core to ensure export/print includes the marks. Also
    // force a page appearance update so that saved/printed PDFs contain
    // baked annotation appearance streams (avoids race with render pipeline).
    public void commitPendingInkToCoreBlocking() {
        org.opendroidpdf.InkCommitHelper.commitPendingInkToCoreBlocking(new org.opendroidpdf.InkCommitHelper.Host() {
            @Override public @NonNull org.opendroidpdf.core.MuPdfRepository getRepository() { return muPdfRepository; }
            @Override public @NonNull MuPDFReaderView getDocView() { return mDocView; }
            @Override public void runOnUiThread(@NonNull Runnable r) { OpenDroidPDFActivity.this.runOnUiThread(r); }
            @Override public void invalidateOptionsMenu() { OpenDroidPDFActivity.this.invalidateOptionsMenuSafely(); }
        });
    }

    // Export/intent/notes hosts moved into app/hosts adapters

    // (Removed) deprecated inner host classes replaced by adapters in app/hosts.

    // Toolbar host moved to app/hosts/ToolbarHostAdapter

    public void showInfo(String message) {
        org.opendroidpdf.app.ui.UiUtils.showInfo(this, message);
    }    

    // Adapter utility for export host
    public void markIgnoreSaveOnStop() { mIgnoreSaveOnStopThisTime = true; }

    
    public void requestPassword() {
        if (passwordHostAdapter != null && core != null && mAlertBuilder != null) {
            passwordHostAdapter.requestPassword(core, mAlertBuilder);
        }
    }


    // Go-to-page dialog is invoked directly by DocumentToolbarController.


    
    
    private void search(int direction) {
        if (searchActions == null) searchActions = new SearchActions();
        searchActions.search(new org.opendroidpdf.app.hosts.SearchHostAdapter(this), direction);
    }
    

    @Override
    public void onBackPressed() {
        if (backPressController == null) {
            backPressController = new BackPressController(new BackPressHostAdapter(this));
        }
        boolean consumed = backPressController.onBackPressed();
        if (!consumed) super.onBackPressed();
    }

    // Mapping moved to ActionBarBackPressModeMapper

    
    @Override
    public void performPickFor(FilePicker picker) {
        mFilePicker = picker;
        Intent intent = new Intent(this, OpenDroidPDFFileChooser.class);
        startActivityForResult(intent, FILEPICK_REQUEST);
    }

    public void setTitle() {
        org.opendroidpdf.app.ui.TitleHelper.setTitle(this, mDocView, core);
    }
    
    public void hideKeyboard() { org.opendroidpdf.app.ui.KeyboardHelper.hide(this); }
	

    private void enterFullscreen() {
        if (fullscreenController == null) fullscreenController = new org.opendroidpdf.app.ui.FullscreenController();
        fullscreenController.enterFullscreen(new org.opendroidpdf.app.hosts.FullscreenHostAdapter(this));
    }
            
    public void exitFullScreen() {
        if (fullscreenController == null) fullscreenController = new org.opendroidpdf.app.ui.FullscreenController();
        fullscreenController.exitFullscreen(new org.opendroidpdf.app.hosts.FullscreenHostAdapter(this));
    }

    // (Legacy) action-bar animation reset helper removed; fullscreen logic moved to FullscreenController.

    // Fullscreen host moved to app/hosts/FullscreenHostAdapter

    // Public helpers for host adapters
    public void setActionBarModeHidden() { mActionBarMode = ActionBarMode.Hidden; }
    public void setActionBarModeMainIfHidden() { if (mActionBarMode == ActionBarMode.Hidden) mActionBarMode = ActionBarMode.Main; }
    // saveViewport(uri) kept private; adapters should use getViewportController().saveViewport()

    public static File getNotesDir(Context context) {
        return org.opendroidpdf.app.storage.NotesStorage.ensureNotesDir(
                context,
                NOTES_DIR_NAME,
                LEGACY_NOTES_DIR_NAME
        );
    }
    
    public ArrayList<TemporaryUriPermission> getTemporaryUriPermissions() {
        return temporaryUriPermissions;
    }

    public void rememberTemporaryUriPermission(Intent intent) {
        temporaryUriPermissions.add(new TemporaryUriPermission(intent));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (storagePermissionController != null &&
            storagePermissionController.handleRequestPermissionsResult(
                requestCode,
                grantResults,
                new Runnable() { @Override public void run() { openDocumentFromIntent(getIntent()); } },
                new Runnable() { @Override public void run() { Toast.makeText(OpenDroidPDFActivity.this, R.string.cannot_open_document, Toast.LENGTH_LONG).show(); } })) {
            return; // handled
        }
    }

    // dpToPixel/memory helpers moved to UiUtils
}
