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
import android.widget.RelativeLayout;
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
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.document.RecentFilesController;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.helpers.IntentRouter;
import org.opendroidpdf.app.helpers.StoragePermissionDialogHelper;
import org.opendroidpdf.app.helpers.UriPermissionHelper;
import org.opendroidpdf.app.helpers.StoragePermissionController;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.annotation.PenSettingsController;
import org.opendroidpdf.app.preferences.PenPreferences;
import org.opendroidpdf.app.toolbar.ToolbarStateController;
import org.opendroidpdf.core.AlertController;
import org.opendroidpdf.app.alert.AlertDialogHelper;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.core.SaveCallback;
import org.opendroidpdf.core.SaveController;
import org.opendroidpdf.core.SearchController;
import org.opendroidpdf.app.dashboard.DashboardController;

public class OpenDroidPDFActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, FilePicker.FilePickerSupport, TemporaryUriPermission.TemporaryUriPermissionProvider, AnnotationToolbarController.Host, SearchToolbarController.Host, DocumentToolbarController.Host, PenSettingsController.Host, DashboardFragment.DashboardHost, DocumentSetupController.Host
{       
    enum ActionBarMode {Main, Annot, Edit, Search, Selection, Hidden, AddingTextAnnot, Empty};
    private static final String TAG = "OpenDroidPDFActivity";
    private static final String NOTES_DIR_NAME = "OpenDroidPDFNotes";
    private static final String LEGACY_NOTES_DIR_NAME = "PenAndPDFNotes";
    
    private SearchToolbarController searchToolbarController;
    private String latestTextInSearchBox = "";
    private String textOfLastSearch = "";
    private boolean mSaveOnStop = false;
    private boolean mSaveOnDestroy = false;
    private boolean mIgnoreSaveOnStopThisTime = false;
    private boolean mIgnoreSaveOnDestroyThisTime = false;
    private boolean mDocViewNeedsNewAdapter = false;
    private int mPageBeforeInternalLinkHit = -1;
    private float mNormalizedScaleBeforeInternalLinkHit = 1.0f;
    private float mNormalizedXScrollBeforeInternalLinkHit = 0;
    private float mNormalizedYScrollBeforeInternalLinkHit = 0;
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

    // migrated into StoragePermissionController
    private boolean awaitingManageStoragePermission = false; // kept for compatibility; delegated
    private boolean showingStoragePermissionDialog = false; // kept for compatibility; delegated

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
    private IntentRouter intentRouter;
    private ToolbarStateController toolbarStateController;
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
    private AlertDialog mAlertDialog; // retained for other non-MuPDF dialogs
    private FilePicker mFilePicker;
    private final SaveController saveController = new SaveController();
    private AlertDialogHelper alertDialogHelper;
    private SaveController.SaveJob activeSaveJob;
    
    private ArrayList<TemporaryUriPermission> temporaryUriPermissions = new ArrayList<TemporaryUriPermission>();

    private DashboardController dashboardController;
    private DocumentHostController documentHostController;
    private org.opendroidpdf.app.helpers.ActivityResultRouter activityResultRouter;
    private StoragePermissionController storagePermissionController;
    private RecentFilesController recentFilesController;

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
            alertDialogHelper = new AlertDialogHelper(new AlertHost(), alertController);
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

    // Host for AlertDialogHelper
    private final class AlertHost implements AlertDialogHelper.Host {
        @Override public @NonNull androidx.appcompat.app.AlertDialog.Builder alertBuilder() { return mAlertBuilder; }
        @Override public boolean isFinishing() { return OpenDroidPDFActivity.this.isFinishing(); }
        @Override public @NonNull String t(int resId) { return getString(resId); }
    }

    private boolean hasRepository() {
        return muPdfRepository != null;
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

    private boolean hasUnsavedChanges() {
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
        if (mAlertDialog != null) { // legacy dialogs
            try { mAlertDialog.cancel(); } catch (Throwable ignore) {}
            mAlertDialog = null;
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
            annotationToolbarController = new AnnotationToolbarController(this);
            searchToolbarController = new SearchToolbarController(this);
            documentToolbarController = new DocumentToolbarController(this);
            documentNavigationController = new DocumentNavigationController(this, new NavigationHost(), EDIT_REQUEST, SAVEAS_REQUEST);
            documentSetupController = new DocumentSetupController(this);
            appServices = AppServices.init(getApplication());
            penPreferences = appServices.penPreferences();
            penSettingsController = new PenSettingsController(penPreferences, this);
            exportController = new ExportController(new ExportHost());
            intentRouter = new IntentRouter(new IntentHost());
            toolbarStateController = new ToolbarStateController(new ToolbarHost());
            dashboardController = new DashboardController(getSupportFragmentManager(), R.id.content_fragment_container);
            documentHostController = new DocumentHostController(getSupportFragmentManager(), R.id.content_fragment_container);
            storagePermissionController = new StoragePermissionController();
            activityResultRouter = new org.opendroidpdf.app.helpers.ActivityResultRouter(new org.opendroidpdf.app.helpers.ActivityResultRouter.Host() {
                @Override public int EDIT_REQUEST() { return EDIT_REQUEST; }
                @Override public int OUTLINE_REQUEST() { return OUTLINE_REQUEST; }
                @Override public int PRINT_REQUEST() { return PRINT_REQUEST; }
                @Override public int SAVEAS_REQUEST() { return SAVEAS_REQUEST; }
                @Override public int MANAGE_STORAGE_REQUEST() { return MANAGE_STORAGE_REQUEST; }
                @Override public void overridePendingTransition(int enter, int exit) { OpenDroidPDFActivity.this.overridePendingTransition(enter, exit); }
                @Override public void hideDashboard() { OpenDroidPDFActivity.this.hideDashboard(); }
                @Override public void setIntent(Intent intent) { OpenDroidPDFActivity.this.setIntent(intent); }
                @Override public Intent getIntent() { return OpenDroidPDFActivity.this.getIntent(); }
                @Override public void openDocumentFromIntent(Intent intent) { OpenDroidPDFActivity.this.openDocumentFromIntent(intent); }
                @Override public boolean canResumeAfterManageStorage() { return (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) && android.os.Environment.isExternalStorageManager(); }
                @Override public void showToast(int resId) { android.widget.Toast.makeText(OpenDroidPDFActivity.this, resId, android.widget.Toast.LENGTH_LONG).show(); }
                @Override public void setDisplayedViewIndex(int pageIndex) { if (mDocView!=null) mDocView.setDisplayedViewIndex(pageIndex); }
                @Override public void documentNavigation_onActivityResultSaveAs(int resultCode, Intent intent) { if (documentNavigationController!=null) documentNavigationController.onActivityResultSaveAs(resultCode, intent); }
            });
		
                //Set default preferences on first start
            PreferenceManager.setDefaultValues(this, SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS, R.xml.preferences, false);
            SettingsActivity.ensurePreferencesNamespace(this);
            
            getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS).registerOnSharedPreferenceChangeListener(this);
            onSharedPreferenceChanged(getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS),""); //Call this once so I don't need to duplicate code
            
                //Get various data from the bundle
            if(savedInstanceState != null)
            {   
                mActionBarMode = ActionBarMode.valueOf(savedInstanceState.getString("ActionBarMode", ActionBarMode.Main.toString ()));
                mPageBeforeInternalLinkHit = savedInstanceState.getInt("PageBeforeInternalLinkHit", mPageBeforeInternalLinkHit);
                mNormalizedScaleBeforeInternalLinkHit = savedInstanceState.getFloat("NormalizedScaleBeforeInternalLinkHit", mNormalizedScaleBeforeInternalLinkHit); 
                mNormalizedXScrollBeforeInternalLinkHit = savedInstanceState.getFloat("NormalizedXScrollBeforeInternalLinkHit", mNormalizedXScrollBeforeInternalLinkHit);
                mNormalizedYScrollBeforeInternalLinkHit = savedInstanceState.getFloat("NormalizedYScrollBeforeInternalLinkHit", mNormalizedYScrollBeforeInternalLinkHit);
                mDocViewParcelable = savedInstanceState.getParcelable("mDocView");

                latestTextInSearchBox = savedInstanceState.getString("latestTextInSearchBox", latestTextInSearchBox);
                invalidateOptionsMenu();
            }
            
			mAlertBuilder = new AlertDialog.Builder(this);
            
                //Get the core saved with onRetainNonConfigurationInstance()
            if (core == null) {
                core = (OpenDroidPDFCore)getLastCustomNonConfigurationInstance();
                if(core != null) mDocViewNeedsNewAdapter = true;
            }

            // Debug-only broadcast hooks
            if (org.opendroidpdf.BuildConfig.DEBUG) {
                org.opendroidpdf.app.debug.DebugActionsController.registerDebugBroadcasts(this);
            }
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
                invalidateOptionsMenu();
                return;
            }

            invalidateOptionsMenu();
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

    private void resetDocumentStateForIntent() {
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

    private void openDocumentFromIntent(Intent intent)
    {
        Log.i(TAG, "openDocumentFromIntent(): data=" + intent.getData() + " type=" + intent.getType());
        if (documentNavigationController != null) {
            documentNavigationController.openDocumentFromIntent(intent);
        }
    }

    private void runAutotestIfNeeded(final Intent intent) {
        if (!BuildConfig.DEBUG) return;
        if (mAutoTestRan) return;
        if (intent == null || !intent.getBooleanExtra("autotest", false)) return;
        if (mDocView == null) return;
        mAutoTestRan = true;
        AppCoroutines.launchMainDelayed(AppCoroutines.mainScope(), 1000, new Runnable() {
            @Override public void run() {
                try {
                    if (intent.getBooleanExtra("autotest_red", false)) {
                        SharedPreferences sp = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
                        SharedPreferences.Editor ed = sp.edit();
                        ed.putString(SettingsActivity.PREF_INK_COLOR, "15"); // ColorPalette index for Red
                        ed.apply();
                        onSharedPreferenceChanged(sp, SettingsActivity.PREF_INK_COLOR);
                    }
                    MuPDFView v = (MuPDFView) mDocView.getSelectedView();
                    if (v instanceof MuPDFPageView) {
                        MuPDFPageView pv = (MuPDFPageView) v;
                        mDocView.setMode(MuPDFReaderView.Mode.Drawing);
                        int w = Math.max(1, pv.getWidth());
                        int h = Math.max(1, pv.getHeight());
                        float m = Math.min(w, h) * 0.2f;
                        pv.startDraw(m, m);
                        pv.continueDraw(w - m, m);
                        pv.continueDraw(w - m, h - m);
                        pv.continueDraw(m, h - m);
                        pv.continueDraw(m, m);
                        pv.finishDraw();
                        try {
                            PointF[][] arcs = pv.getDraw();
                            if (arcs != null && arcs.length > 0 && muPdfRepository != null) {
                                muPdfRepository.addInkAnnotation(mDocView.getSelectedItemPosition(), arcs);
                                muPdfRepository.markDocumentDirty();
                            }
                        } catch (Throwable ignore) {}
                        pv.cancelDraw();
                        mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                    }
                    commitPendingInkToCoreBlocking();
                    android.util.Log.i(getString(R.string.app_name), "AUTOTEST_HAS_CHANGES="+hasUnsavedChanges());
                    Uri exported = muPdfRepository != null ? muPdfRepository.exportDocument(getApplicationContext()) : null;
                    if (exported == null) {
                        Log.e(getString(R.string.app_name), "AUTOTEST_EXPORT_FAILED");
                        return;
                    }
                    java.io.InputStream in = getContentResolver().openInputStream(exported);
                    java.io.File outFile = new java.io.File(getFilesDir(), "autotest-output.pdf");
                    java.io.OutputStream out = new java.io.FileOutputStream(outFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) { out.write(buf, 0, len); }
                    in.close(); out.close();
                    Log.i(getString(R.string.app_name), "AUTOTEST_OUTPUT=" + outFile.getAbsolutePath()+" bytes="+outFile.length());
                } catch (Throwable t) {
                    Log.e(getString(R.string.app_name), "AUTOTEST_ERROR=" + t);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        
            //Stop searches
        if (mSearchTaskManager != null) mSearchTaskManager.stop();        

        if (core != null)
        {
                //Save the Viewport and update the recent files list
            saveViewport(core.getUri());
                //Stop receiving alerts
            core.stopAlerts();
            destroyAlertWaiter();
        }
    }
    

    @Override
    protected void onStop() {
        super.onStop();
            //Save only during onStop() as this can take some time
        if(core != null && hasUnsavedChanges() && !isChangingConfigurations())
        {
			if(mSaveOnStop && !mIgnoreSaveOnStopThisTime && canSaveToCurrentUri(this))
            {
                saveInBackground(null,
                                 new Callable<Void>() {
                                     @Override
                                     public Void call() {
                                         showInfo(getString(R.string.error_saveing));
                                         return null;
                                     }
                                 }
                                 );
            }
        }
        mIgnoreSaveOnStopThisTime = false;

        cancelRenderThumbnailJob();
    }
    
    
    @Override
    protected void onDestroy() {//There is no guarantee that this is ever called!!!
        super.onDestroy();
            
		getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS).unregisterOnSharedPreferenceChangeListener(this);            
		if(core != null && hasUnsavedChanges() && !isChangingConfigurations())
		{
			SharedPreferences sharedPref = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS);
			if(mSaveOnDestroy && !mIgnoreSaveOnDestroyThisTime && canSaveToCurrentUri(this))
			{
                saveInBackground(
                    new Callable() {
                        @Override
                        public Void call() {
                            if(core!=null)
                            {
                                core.onDestroy();
                                setCoreInstance(null);
                            }
                            return null;
                        }
                    },
                    new Callable<Void>() {
                        @Override
                        public Void call() {
                            showInfo(getString(R.string.error_saveing));
                            if(core!=null)
                            {
                                core.onDestroy(); //Destroy even if not saved as we have no choice
                                setCoreInstance(null);
                            }
                            return null;
                        }
                    }
                                 );
			}
		}
		mIgnoreSaveOnDestroyThisTime = false;
		destroyAlertWaiter();
		if (alertController != null) {
			alertController.shutdown();
			alertController = null;
		}
		activeSaveJob = null;
		if (searchToolbarController != null) {
            searchToolbarController.detach();
        }
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) //Inflates the options menu
        {
            super.onCreateOptionsMenu(menu);

            if(dashboardIsShown())
                mActionBarMode = ActionBarMode.Empty;
            
            final MenuInflater inflater = getMenuInflater();
            if (toolbarStateController != null) {
                org.opendroidpdf.app.toolbar.ToolbarStateController.Mode mode = mapToolbarMode(mActionBarMode);
                // Reset search text when entering Search mode, mirroring legacy behavior
                if (mode == org.opendroidpdf.app.toolbar.ToolbarStateController.Mode.Search) {
                    textOfLastSearch = "";
                }
                toolbarStateController.onCreateOptionsMenu(
                        mode,
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
    @Override
    public void setLatestSearchQuery(@NonNull CharSequence query) {
        latestTextInSearchBox = query != null ? query.toString() : "";
    }

    @Override
    public @NonNull CharSequence getTextOfLastSearch() {
        return textOfLastSearch != null ? textOfLastSearch : "";
    }

    @Override
    public void setTextOfLastSearch(@NonNull CharSequence query) {
        textOfLastSearch = query != null ? query.toString() : "";
    }

    @Override
    public boolean hasDocView() { return mDocView != null; }

    @Override
    public void requestDocViewFocus() { if (mDocView != null) mDocView.requestFocus(); }

    @Override
    public void clearSearchResults() { if (mDocView != null) mDocView.clearSearchResults(); }

    @Override
    public void resetupChildren() { if (mDocView != null) mDocView.resetupChildren(); }

    @Override
    public void setViewingMode() { if (mDocView != null) mDocView.setMode(MuPDFReaderView.Mode.Viewing); }

    @Override
    public void exitSearchModeToMain() { mActionBarMode = ActionBarMode.Main; }

    @Override
    public void stopSearchTaskIfRunning() { if (mSearchTaskManager != null) mSearchTaskManager.stop(); }

    @Override
    public void performSearch(int direction) { search(direction); }

    @Override
    public void showInkColorDialog() {
        // Delegate to unified PenSettingsController dialog (size + color).
        // This reduces Activity code and keeps pen UI in one place.
        if (penSettingsController != null) {
            penSettingsController.show();
        }
    }

    private boolean isCurrentNoteDocument() {
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

    @Override
    public boolean hasDocumentLoaded() {
        return hasRepository();
    }

    @Override
    public boolean isViewingNoteDocument() {
        return isCurrentNoteDocument();
    }

    @Override
    public boolean isLinkBackAvailable() {
        return mPageBeforeInternalLinkHit >= 0;
    }

    @Override
    public void requestAddBlankPage() {
        if (muPdfRepository == null || mDocView == null) {
            return;
        }
        if (muPdfRepository.insertBlankPageAtEnd()) {
            int lastPage = Math.max(0, muPdfRepository.getPageCount() - 1);
            mDocView.setDisplayedViewIndex(lastPage, true);
            mDocView.setScale(1.0f);
            mDocView.setNormalizedScroll(0.0f, 0.0f);
            invalidateOptionsMenu();
        }
    }

    @Override
    public void requestFullscreen() {
        enterFullscreen();
    }

    @Override
    public void requestSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
    }

    @Override
    public void requestPrint() {
        if (exportController != null) {
            exportController.printDoc();
        }
    }

    @Override
    public void requestShare() {
        if (exportController != null) {
            exportController.shareDoc();
        }
    }

    @Override
    public void requestSearchMode() {
        if (mDocView == null) {
            return;
        }
        mActionBarMode = ActionBarMode.Search;
        mDocView.setMode(MuPDFReaderView.Mode.Searching);
        invalidateOptionsMenu();
    }

    @Override
    public void requestDashboard() {
        showDashboard();
    }

    @Override
    public void requestDeleteNote() {
        if (core == null) {
            return;
        }
        core.deleteDocument(this);
        Intent restartIntent = new Intent(this, OpenDroidPDFActivity.class);
        restartIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        restartIntent.setAction(Intent.ACTION_MAIN);
        startActivity(restartIntent);
        finish();
    }

    @Override
    public void requestSaveDialog() {
        showSaveDialog();
    }

    @Override
    public void requestGoToPageDialog() {
        showGoToPageDialoge();
    }

    @Override
    public void requestLinkBackNavigation() {
        boolean applied = org.opendroidpdf.app.navigation.NavigationUiHelper.applyLinkBack(
                this,
                mPageBeforeInternalLinkHit,
                mNormalizedScaleBeforeInternalLinkHit,
                mNormalizedXScrollBeforeInternalLinkHit,
                mNormalizedYScrollBeforeInternalLinkHit);
        if (applied) {
            mPageBeforeInternalLinkHit = -1;
        }
        invalidateOptionsMenu();
    }

    @Override
    public void showPenSizeDialog() {
        if (penSettingsController != null) {
            penSettingsController.show();
        }
    }

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

    @Override
    public ComponentName getSearchComponent() {
        return getComponentName();
    }

    @Override
    public CharSequence getLatestSearchQuery() {
        return latestTextInSearchBox;
    }

    @Override
    public void onSearchNavigate(int direction) {
        if (TextUtils.isEmpty(latestTextInSearchBox)) {
            return;
        }
        hideKeyboard();
        search(direction);
    }

    @Override
    public void showAnnotationInfo(@NonNull String message) {
        showInfo(message);
    }

    @Override
    public boolean isSelectedAnnotationEditable() {
        return selectedAnnotationIsEditable;
    }

    @Override
    public boolean hasDocumentView() {
        return mDocView != null;
    }

    @Override
    public boolean isDrawingModeActive() {
        return mDocView != null && mDocView.getMode() == MuPDFReaderView.Mode.Drawing;
    }

    @Override
    public boolean isErasingModeActive() {
        return mDocView != null && mDocView.getMode() == MuPDFReaderView.Mode.Erasing;
    }

    @Override
    public void switchToDrawingMode() {
        if (mDocView != null) {
            mDocView.setMode(MuPDFReaderView.Mode.Drawing);
        }
    }

    @Override
    public void switchToErasingMode() {
        if (mDocView != null) {
            mDocView.setMode(MuPDFReaderView.Mode.Erasing);
        }
    }

    @Override
    public void switchToViewingMode() {
        if (mDocView != null) {
            mDocView.setMode(MuPDFReaderView.Mode.Viewing);
        }
    }

    @Override
    public void switchToAddingTextMode() {
        if (mDocView != null) {
            mDocView.setMode(MuPDFReaderView.Mode.AddingTextAnnot);
        }
    }

    @Override
    public void notifyStrokeCountChanged(int strokeCount) {
        if (mDocView != null) {
            mDocView.onNumberOfStrokesChanged(strokeCount);
        }
    }

    @Override
    public void cancelAnnotationMode() {
        if (mDocView == null) {
            return;
        }
        switch (mActionBarMode) {
            case Annot:
            case Edit:
            case AddingTextAnnot:
                mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                break;
            default:
                break;
        }
    }

    @Override
    public void confirmAnnotationChanges() {
        if (mDocView == null) {
            return;
        }
        PageView pageView = getActivePageView();
        switch (mActionBarMode) {
            case Annot:
                if (pageView != null) {
                    pageView.saveDraw();
                    mDocView.onNumberOfStrokesChanged(pageView.getDrawingSize());
                }
                break;
            case Edit:
                if (pageView != null) {
                    pageView.deselectAnnotation();
                }
                break;
            default:
                break;
        }
        if (mActionBarMode == ActionBarMode.Annot ||
            mActionBarMode == ActionBarMode.Edit) {
            mDocView.setMode(MuPDFReaderView.Mode.Viewing);
        }
    }

    @Override
    public PageView getActivePageView() {
        if (mDocView == null) {
            return null;
        }
        View selected = mDocView.getSelectedView();
        if (selected instanceof PageView) {
            return (PageView) selected;
        }
        return null;
    }

    @Override
    public void finalizePendingInkBeforePenSettingChange() {
        if (mDocView == null) {
            return;
        }
        try {
            MuPDFView view = (MuPDFView) mDocView.getSelectedView();
            if (view instanceof MuPDFPageView) {
                MuPDFPageView pageView = (MuPDFPageView) view;
                PointF[][] pending = pageView.getDraw();
                if (pending != null && pending.length > 0) {
                    pageView.saveDraw();
                    mDocView.onNumberOfStrokesChanged(pageView.getDrawingSize());
                }
            }
        } catch (Throwable ignore) {
        }
    }

    // DashboardFragment.DashboardHost
    @Override
    public void onOpenDocumentRequested() {
        openDocument();
    }

    @Override
    public void onCreateNewDocumentRequested() {
        showOpenNewDocumentDialoge();
    }

    @Override
    public void onOpenSettingsRequested() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
    }

    @Override
    public void onRecentFileRequested(final RecentFile recentFile) {
        checkSaveThenCall(new Callable<Void>() {
            @Override
            public Void call() {
                Intent intent = new Intent(Intent.ACTION_VIEW, recentFile.getUri(), OpenDroidPDFActivity.this, OpenDroidPDFActivity.class);
                intent.putExtra(Intent.EXTRA_TITLE, recentFile.getDisplayName());
                startActivity(intent);
                overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                hideDashboard();
                finish();
                return null;
            }
        });
    }

    @Override
    public boolean isMemoryLow() {
        return memoryLow();
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
        if (toolbarStateController != null) {
            toolbarStateController.onPrepareOptionsMenu(menu);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private org.opendroidpdf.app.toolbar.ToolbarStateController.Mode mapToolbarMode(ActionBarMode mode) {
        switch (mode) {
            case Main: return org.opendroidpdf.app.toolbar.ToolbarStateController.Mode.Main;
            case Annot: return org.opendroidpdf.app.toolbar.ToolbarStateController.Mode.Annot;
            case Edit: return org.opendroidpdf.app.toolbar.ToolbarStateController.Mode.Edit;
            case Search: return org.opendroidpdf.app.toolbar.ToolbarStateController.Mode.Search;
            case Selection: return org.opendroidpdf.app.toolbar.ToolbarStateController.Mode.Selection;
            case Hidden: return org.opendroidpdf.app.toolbar.ToolbarStateController.Mode.Hidden;
            case AddingTextAnnot: return org.opendroidpdf.app.toolbar.ToolbarStateController.Mode.AddingTextAnnot;
            case Empty:
            default:
                return org.opendroidpdf.app.toolbar.ToolbarStateController.Mode.Empty;
        }
    }

	private void tryToTakePersistablePermissions(Intent intent) {
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
            // Fallback to legacy behavior if controller is unavailable
            if (searchController == null) {
                mSearchTaskManager = null;
                return;
            }
            mSearchTaskManager = new SearchTaskManager(this, searchController) {
                @Override
                protected void onTextFound(SearchResult result) {
                    mDocView.addSearchResult(result);
                }

                @Override
                protected void goToResult(SearchResult result) {
                    mDocView.resetupChildren();
                    if (mDocView.getSelectedItemPosition() != result.getPageNumber())
                        mDocView.setDisplayedViewIndex(result.getPageNumber());
                    RectF resultRect = result.getFocusedSearchBox();
                    if (resultRect != null) {
                        mDocView.doNextScrollWithCenter();
                        mDocView.setDocRelXScroll(resultRect.left);
                        mDocView.setDocRelYScroll(resultRect.top);
                    }
                }
            };
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
        final org.opendroidpdf.app.DocumentHostFragment hostFragment = documentHostController != null ? documentHostController.ensureFragment() : null;
        return hostFragment != null ? hostFragment.getDocumentContainer() : null;
    }

    public void createDocViewIfNeeded() {
        if (core == null || mDocView != null) return;
        mDocView = org.opendroidpdf.DocViewFactory.create(this);
        mDocViewNeedsNewAdapter = true;
    }

    // Dashboard wrappers for controllers/routers
    public boolean dashboardIsShown() {
        return dashboardController != null && dashboardController.isDashboardShown();
    }

    public void showDashboard() {
        if (dashboardController != null) dashboardController.showDashboard();
    }

    public void hideDashboard() {
        if (dashboardController != null) dashboardController.hideDashboard();
    }

    // Attach the document view to the fragment container
    public void attachDocViewToContainer(android.view.ViewGroup container) {
        if (container == null || mDocView == null) return;
        try {
            if (mDocView.getParent() instanceof android.view.ViewGroup) {
                ((android.view.ViewGroup) mDocView.getParent()).removeView(mDocView);
            }
        } catch (Throwable ignore) {}
        container.removeAllViews();
        container.addView(mDocView,
                new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
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

    public void restoreViewportIfAny() {
        restoreViewport();
    }

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
    public boolean isActionBarModeEdit() { return mActionBarMode == ActionBarMode.Edit; }
    public boolean isActionBarModeAddingTextAnnot() { return mActionBarMode == ActionBarMode.AddingTextAnnot; }
    public boolean isActionBarModeSearchOrHidden() { return mActionBarMode == ActionBarMode.Search || mActionBarMode == ActionBarMode.Hidden; }
    public void setSelectedAnnotationEditable(boolean editable) { selectedAnnotationIsEditable = editable; }
    public androidx.appcompat.app.AlertDialog.Builder getAlertBuilder() { return mAlertBuilder; }
    public void rememberPreLinkHitViewport(int page, float scale, float x, float y) {
        mPageBeforeInternalLinkHit = page;
        mNormalizedScaleBeforeInternalLinkHit = scale;
        mNormalizedXScrollBeforeInternalLinkHit = x;
        mNormalizedYScrollBeforeInternalLinkHit = y;
    }
    
    public void checkSaveThenCall(final Callable callable) {
        if (documentNavigationController != null) {
            documentNavigationController.checkSaveThenCall(callable);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {        
        if (storagePermissionController != null) storagePermissionController.resetAwaiting(); else awaitingManageStoragePermission = false;
        if (activityResultRouter != null && activityResultRouter.handle(requestCode, resultCode, intent)) {
            super.onActivityResult(requestCode, resultCode, intent);
            return;
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void showSaveAsActivity() {
        if (documentNavigationController != null) {
            mIgnoreSaveOnStopThisTime = true;
            documentNavigationController.showSaveAsActivity();
        }
    }

    private void saveAsInBackground(final Uri uri, final Callable successCallable, final Callable failureCallable) {
        callInBackgroundAndShowDialog(getString(R.string.saving),
                new Callable<Exception>() {
                    @Override
                    public Exception call() {
                        // Ensure any in-progress ink strokes are committed before saving
                        commitPendingInkToCoreBlocking();
                        return saveAs(uri);
                    }
                },
                successCallable, failureCallable);
    }
    
    private void saveInBackground(final Callable successCallable, final Callable failureCallable) {
        callInBackgroundAndShowDialog(getString(R.string.saving),
                new Callable<Exception>() {
                    @Override
                    public Exception call() {
                        // Ensure any in-progress ink strokes are committed before saving
                        commitPendingInkToCoreBlocking();
                        return save();
                    }
                },
                successCallable, failureCallable);
    }

    private void callInBackgroundAndShowDialog(final String messege, final Callable<Exception> saveCallable, final Callable successCallable, final Callable failureCallable) {
        final AlertDialog waitWhileSavingDialog = mAlertBuilder.create();
        waitWhileSavingDialog.setTitle(messege);
        waitWhileSavingDialog.setCancelable(false);
        waitWhileSavingDialog.setCanceledOnTouchOutside(false);
        final View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null, false);
        waitWhileSavingDialog.setView(progressView);
        if (!isFinishing()) {
            waitWhileSavingDialog.show();
        }
        cancelActiveSaveJob();
        activeSaveJob = saveController.run(saveCallable, new SaveCallback() {
            @Override
            public void onComplete(Exception result) {
                if (waitWhileSavingDialog.isShowing()) {
                    try {
                        waitWhileSavingDialog.dismiss();
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                activeSaveJob = null;
                if (result == null) {
                    if (BuildConfig.DEBUG) {
                        String label = messege != null ? messege.toLowerCase(java.util.Locale.ROOT) : "";
                        if (label.contains("save")) {
                            Uri u = currentDocumentUri();
                            Log.i(TAG, "DEBUG_SAVE_COMPLETE uri=" + (u != null ? u.toString() : "null"));
                        } else if (label.contains("share")) {
                            Uri u = getLastExportedUri();
                            Log.i(TAG, "DEBUG_SHARE_READY uri=" + (u != null ? u.toString() : "null"));
                        } else if (label.contains("print")) {
                            Uri u = getLastExportedUri();
                            Log.i(TAG, "DEBUG_PRINT_READY uri=" + (u != null ? u.toString() : "null"));
                        } else {
                            Log.i(TAG, "DEBUG_BG_DONE label=" + messege);
                        }
                    }
                    if (successCallable != null) {
                        try {
                            successCallable.call();
                        } catch (Exception e) {
                            showInfo(getString(R.string.error_saveing)+": "+e);
                        }
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        String label = messege != null ? messege.toLowerCase(java.util.Locale.ROOT) : "";
                        if (label.contains("save")) {
                            Log.e(TAG, "DEBUG_SAVE_FAILED e=" + result);
                        } else if (label.contains("share")) {
                            Log.e(TAG, "DEBUG_SHARE_FAILED e=" + result);
                        } else if (label.contains("print")) {
                            Log.e(TAG, "DEBUG_PRINT_FAILED e=" + result);
                        } else {
                            Log.e(TAG, "DEBUG_BG_FAILED label=" + messege + " e=" + result);
                        }
                    }
                    showInfo(getString(R.string.error_saveing)+": "+result);
                    if (failureCallable != null) {
                        try {
                            failureCallable.call();
                        } catch (Exception e) {
                            showInfo(getString(R.string.error_saveing)+": "+e);
                        }
                    }
                }
            }
        });
    }

    private void cancelActiveSaveJob() {
        if (activeSaveJob != null) {
            activeSaveJob.cancel();
            activeSaveJob = null;
        }
    }
    
    private synchronized Exception saveAs(Uri uri) {
        if (muPdfRepository == null)
            return new Exception("repository is not ready");
        try
        {
            muPdfRepository.saveCopy(this, uri);
        }
        catch(Exception e)
        {
            Log.e(getString(R.string.app_name), "Exception during saveAs(): "+e);
            return e;
        }
            //Set the uri of this intent to the new file path
        getIntent().setData(uri);
            //Save the viewport under the new name
        saveViewportAndRecentFiles(muPdfRepository.getDocumentUri());
		//Try to take permissions
	tryToTakePersistablePermissions(getIntent());
        rememberTemporaryUriPermission(getIntent());
        return null;
    }
    

    private synchronized Exception save() {
        if (muPdfRepository == null) return new Exception("repository is not ready");
        try
        {
            muPdfRepository.saveDocument(this);
        }
        catch(Exception e)
        {
            Log.e(getString(R.string.app_name), "Exception during save(): "+e);
            return e;
        }
            //Save the viewport
        saveViewportAndRecentFiles(muPdfRepository.getDocumentUri());
        return null;
    }
    
            
    private void saveViewport(SharedPreferences.Editor edit, String path) {
        if(mDocView == null) return;
        if(path == null) path = "/nopath";
        edit.putInt("page"+path, mDocView.getSelectedItemPosition());
        edit.putFloat("normalizedscale"+path, mDocView.getNormalizedScale());
        edit.putFloat("normalizedxscroll"+path, mDocView.getNormalizedXScroll());
        edit.putFloat("normalizedyscroll"+path, mDocView.getNormalizedYScroll());
        edit.commit();
    }


    private void restoreViewport() {
        if (core == null || mDocView == null) return;
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        if (recentFilesController != null) {
            recentFilesController.restoreViewport(mDocView, prefs, core.getUri());
        }
    }


    private void setViewport(SharedPreferences prefs, Uri uri) {
        if (recentFilesController != null && mDocView != null) {
            recentFilesController.restoreViewport(mDocView, prefs, uri);
        }
    }

    
    private void setViewport(int page, float normalizedscale, float normalizedxscroll, float normalizedyscroll) {
        if (recentFilesController != null) {
            recentFilesController.setViewport(mDocView, page, normalizedscale, normalizedxscroll, normalizedyscroll);
        }
    }

    // Public wrapper for navigation/helpers to adjust the viewport.
    public void applyViewport(int page, float normalizedscale, float normalizedxscroll, float normalizedyscroll) {
        setViewport(page, normalizedscale, normalizedxscroll, normalizedyscroll);
    }


    private void saveRecentFiles(SharedPreferences prefs, final SharedPreferences.Editor edit, Uri uri) {
        if (recentFilesController != null) {
            recentFilesController.saveRecentFiles(prefs, edit, uri);
        }
    }

    private void cancelRenderThumbnailJob() {
        if (recentFilesController != null) {
            recentFilesController.cancelRenderThumbnailJob();
        }
    }
    
    
    private void saveViewportAndRecentFiles(Uri uri) {
        if (uri == null) return;
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        if (recentFilesController != null) {
            recentFilesController.saveViewportAndRecentFiles(mDocView, prefs, uri);
        }
    }
    

    private void saveViewport(Uri uri) {
        if (uri == null) return;
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        SharedPreferences.Editor edit = prefs.edit();
        if (recentFilesController != null) recentFilesController.saveViewport(mDocView, edit, uri.toString());
        edit.apply();
    }

    
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
        outState.putInt("PageBeforeInternalLinkHit", mPageBeforeInternalLinkHit);
        outState.putFloat("NormalizedScaleBeforeInternalLinkHit", mNormalizedScaleBeforeInternalLinkHit);
        outState.putFloat("NormalizedXScrollBeforeInternalLinkHit", mNormalizedXScrollBeforeInternalLinkHit);
        outState.putFloat("NormalizedYScrollBeforeInternalLinkHit", mNormalizedYScrollBeforeInternalLinkHit);
        if(mDocView != null) outState.putParcelable("mDocView", mDocView.onSaveInstanceState());
        outState.putString("latestTextInSearchBox", latestTextInSearchBox);

            //Treat the bundle with the SaveInstanceStateManager before saving it
        SaveInstanceStateManager.saveBundleIfNecessary(outState);
    }        
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key) {
            //Take care of some preference changes directly
        if (sharedPref.getBoolean(SettingsActivity.PREF_KEEP_SCREEN_ON, false ))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSaveOnStop = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS).getBoolean(SettingsActivity.PREF_SAVE_ON_STOP, true);
        mSaveOnDestroy = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS).getBoolean(SettingsActivity.PREF_SAVE_ON_DESTROY, true);

        try{
            numberRecentFilesInMenu = Integer.parseInt(getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS).getString(SettingsActivity.PREF_NUMBER_RECENT_FILES, "20"));
        }
        catch(NumberFormatException ex) {
            numberRecentFilesInMenu = Integer.parseInt(getResources().getString(R.string.number_recent_files_default));
        }    
            
            //Also notify other classes and members of the preference change
        ReaderView.onSharedPreferenceChanged(sharedPref, key);
        PageView.onSharedPreferenceChanged(sharedPref, key, this);
        if (mDocView != null) {
            mDocView.onSharedPreferenceChanged(sharedPref, key);
        }
        if(core != null) core.onSharedPreferenceChanged(sharedPref, key);
    }    

    
    // printDoc/shareDoc now handled by ExportController

    // Flush any currently drawn but not yet committed ink on the active page
    // into the MuPDF core to ensure export/print includes the marks. Also
    // force a page appearance update so that saved/printed PDFs contain
    // baked annotation appearance streams (avoids race with render pipeline).
    private void commitPendingInkToCoreBlocking() {
        if (muPdfRepository == null || mDocView == null) return;

        final AtomicReference<PointF[][]> arcsRef = new AtomicReference<>(null);
        final AtomicInteger pageIndexRef = new AtomicInteger(-1);
        final AtomicReference<MuPDFPageView> pvRef = new AtomicReference<>(null);
        final CountDownLatch latch = new CountDownLatch(1);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    MuPDFView v = (MuPDFView) mDocView.getSelectedView();
                    if (v instanceof MuPDFPageView) {
                        MuPDFPageView pv = (MuPDFPageView) v;
                        pvRef.set(pv);
                        // Snapshot current drawing and clear it from the overlay
                        PointF[][] arcs = pv.getDraw();
                        if (arcs != null && arcs.length > 0) {
                            arcsRef.set(arcs);
                            pageIndexRef.set(mDocView.getSelectedItemPosition());
                            pv.cancelDraw();
                        }
                    }
                } catch (Throwable ignore) {
                } finally {
                    latch.countDown();
                }
            }
        });

        try { latch.await(500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}

        PointF[][] arcs = arcsRef.get();
        int pageIndex = pageIndexRef.get();
        if (pageIndex >= 0) {
            if (arcs != null) {
                try {
                    muPdfRepository.addInkAnnotation(pageIndex, arcs);
                    muPdfRepository.markDocumentDirty();
                    final PointF[][] arcsForUndo = arcs;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MuPDFPageView pv = pvRef.get();
                            if (pv != null) {
                                pv.recordCommittedInkForUndo(arcsForUndo);
                                pv.loadAnnotations();
                            }
                            invalidateOptionsMenu();
                        }
                    });
                } catch (Throwable ignored) {
                    // If this fails, fallback is that export proceeds with committed state only
                }
            }
            // Ensure annotation appearance streams are updated before export/save/print.
            try {
                muPdfRepository.refreshAnnotationAppearance(pageIndex);
            } catch (Throwable ignore) {
            }
        }

        // Also wait briefly for any previously accepted stroke to finish committing
        MuPDFPageView pv = pvRef.get();
        if (pv != null) {
            try { pv.awaitInkCommit(1000); } catch (Throwable ignore) {}
        }
    }

    private class ExportHost implements ExportController.Host {
        @Override
        public MuPdfRepository getRepository() {
            return muPdfRepository;
        }

        @Override
        public void showInfo(String message) {
            OpenDroidPDFActivity.this.showInfo(message);
        }

        @Override
        public String currentDocumentName() {
            return OpenDroidPDFActivity.this.currentDocumentName();
        }

        @Override
        public void setLastExportedUri(Uri uri) {
            OpenDroidPDFActivity.this.setLastExportedUri(uri);
        }

        @Override
        public Uri getLastExportedUri() {
            return OpenDroidPDFActivity.this.getLastExportedUri();
        }

        @Override
        public void markIgnoreSaveOnStop() {
            mIgnoreSaveOnStopThisTime = true;
        }

        @Override
        public Context getContext() {
            return OpenDroidPDFActivity.this;
        }

        @Override
        public android.content.ContentResolver getContentResolver() {
            return OpenDroidPDFActivity.this.getContentResolver();
        }

        @Override
        public void callInBackgroundAndShowDialog(String message, Callable<Exception> background, Callable<Void> success, Callable<Void> failure) {
            OpenDroidPDFActivity.this.callInBackgroundAndShowDialog(message, background, success, failure);
        }

        @Override
        public void commitPendingInkToCoreBlocking() {
            OpenDroidPDFActivity.this.commitPendingInkToCoreBlocking();
        }
    }

    private class IntentHost implements IntentRouter.Host {
        @Override
        public boolean hasCore() {
            return OpenDroidPDFActivity.this.hasCore();
        }

        @Override
        public void showDashboard() {
            OpenDroidPDFActivity.this.showDashboard();
        }

        @Override
        public void openDocumentFromIntent(Intent intent) {
            OpenDroidPDFActivity.this.openDocumentFromIntent(intent);
        }

        @Override
        public void resetDocumentStateForIntent() {
            OpenDroidPDFActivity.this.resetDocumentStateForIntent();
        }

        @Override
        public boolean ensureStoragePermission(Intent intent) {
            return OpenDroidPDFActivity.this.ensureStoragePermission(intent);
        }
    }

    private class NavigationHost implements DocumentNavigationController.Host {
        @Override
        public boolean hasUnsavedChanges() {
            return OpenDroidPDFActivity.this.hasUnsavedChanges();
        }

        @Override
        public boolean canSaveToCurrentUri() {
            return OpenDroidPDFActivity.this.canSaveToCurrentUri(OpenDroidPDFActivity.this);
        }

        @Override
        public void saveInBackground(Callable<?> success, Callable<?> failure) {
            OpenDroidPDFActivity.this.saveInBackground((Callable) success, (Callable) failure);
        }

        @Override
        public void saveAsInBackground(Uri uri, Callable<?> success, Callable<?> failure) {
            OpenDroidPDFActivity.this.saveAsInBackground(uri, (Callable) success, (Callable) failure);
        }

        @Override
        public void callInBackgroundAndShowDialog(String message, Callable<Exception> saveCallable, Callable<?> success, Callable<?> failure) {
            OpenDroidPDFActivity.this.callInBackgroundAndShowDialog(message, saveCallable, (Callable) success, (Callable) failure);
        }

        @Override
        public void commitPendingInkToCoreBlocking() {
            OpenDroidPDFActivity.this.commitPendingInkToCoreBlocking();
        }

        @Override
        public void showInfo(String message) {
            OpenDroidPDFActivity.this.showInfo(message);
        }

        @Override
        public AlertDialog.Builder alertBuilder() {
            return OpenDroidPDFActivity.this.mAlertBuilder;
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode) {
            OpenDroidPDFActivity.this.startActivityForResult(intent, requestCode);
        }

        @Override
        public void overridePendingTransition(int enterAnim, int exitAnim) {
            OpenDroidPDFActivity.this.overridePendingTransition(enterAnim, exitAnim);
        }

        @Override
        public void hideDashboard() {
            OpenDroidPDFActivity.this.hideDashboard();
        }

        @Override
        public OpenDroidPDFCore getCore() {
            return OpenDroidPDFActivity.this.core;
        }

        @Override
        public void setCoreInstance(OpenDroidPDFCore core) {
            OpenDroidPDFActivity.this.setCoreInstance(core);
        }

        @Override
        public void finish() {
            OpenDroidPDFActivity.this.finish();
        }

        @Override
        public void checkSaveThenCall(Callable<?> callable) {
            OpenDroidPDFActivity.this.checkSaveThenCall(callable);
        }

        @Override
        public void setTitle() {
            OpenDroidPDFActivity.this.setTitle();
        }

        @Override
        public File getNotesDir() {
            return OpenDroidPDFActivity.getNotesDir(OpenDroidPDFActivity.this);
        }

        @Override
        public void openNewDocument(String filename) throws java.io.IOException {
            OpenDroidPDFActivity.this.openNewDocument(filename);
        }

        @Override
        public void setupCore() {
            OpenDroidPDFActivity.this.setupCore();
        }

        @Override
        public void setupDocView() {
            OpenDroidPDFActivity.this.setupDocView();
        }

        @Override
        public void setupSearchTaskManager() {
            OpenDroidPDFActivity.this.setupSearchTaskManager();
        }

        @Override
        public void tryToTakePersistablePermissions(Intent intent) {
            OpenDroidPDFActivity.this.tryToTakePersistablePermissions(intent);
        }

        @Override
        public void rememberTemporaryUriPermission(Intent intent) {
            OpenDroidPDFActivity.this.rememberTemporaryUriPermission(intent);
        }

        @Override
        public void saveRecentFiles(SharedPreferences prefs, SharedPreferences.Editor edit, Uri uri) {
            OpenDroidPDFActivity.this.saveRecentFiles(prefs, edit, uri);
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return OpenDroidPDFActivity.this.getSharedPreferences(name, mode);
        }

        @Override
        public void runAutotestIfNeeded(Intent intent) {
            OpenDroidPDFActivity.this.runAutotestIfNeeded(intent);
        }

        @Override
        public OpenDroidPDFActivity getActivity() {
            return OpenDroidPDFActivity.this;
        }
    }

    private class ToolbarHost implements org.opendroidpdf.app.toolbar.ToolbarStateController.Host {
        @Override
        public boolean hasOpenDocument() {
            return core != null;
        }

        @Override
        public boolean canUndo() {
            MuPDFPageView pv = currentPageView();
            return pv != null && pv.canUndo();
        }

        @Override
        public boolean hasUnsavedChanges() {
            return muPdfRepository != null && muPdfRepository.hasUnsavedChanges();
        }

        @Override
        public boolean hasLinkTarget() {
            return mPageBeforeInternalLinkHit >= 0;
        }

        @Override
        public void invalidateOptionsMenu() {
            OpenDroidPDFActivity.this.invalidateOptionsMenu();
        }

    }

    public void showInfo(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }    

    
    public void requestPassword() {
        androidx.appcompat.app.AlertDialog.Builder builder = mAlertBuilder != null ? mAlertBuilder : new androidx.appcompat.app.AlertDialog.Builder(this);
        org.opendroidpdf.app.dialog.PasswordDialogHelper.show(this, builder, new org.opendroidpdf.app.dialog.PasswordDialogHelper.Callback() {
            @Override public boolean onPasswordEntered(String password) {
                return core != null && core.authenticatePassword(password);
            }
            @Override public void onCancelled() { finish(); }
        });
    }


    private void showGoToPageDialoge() {
        androidx.appcompat.app.AlertDialog.Builder builder = mAlertBuilder != null ? mAlertBuilder : new androidx.appcompat.app.AlertDialog.Builder(this);
        org.opendroidpdf.app.dialog.GoToPageDialog.show(this, builder, mDocView);
    }

    private void showSaveDialog() {
        if (documentNavigationController != null) {
            documentNavigationController.promptSaveOrSaveAs();
        }
    }


    private void showOpenNewDocumentDialoge() {
        if (documentNavigationController != null) {
            documentNavigationController.showOpenNewDocumentDialog();
        }
    }

    
    
    private void search(int direction) {
        if(mDocView.hasSearchResults() && textOfLastSearch.equals(latestTextInSearchBox))
            mDocView.goToNextSearchResult(direction);
        else
        {
            mSearchTaskManager.start(latestTextInSearchBox, direction, mDocView.getSelectedItemPosition());
            textOfLastSearch = latestTextInSearchBox;
        }
    }
    

    @Override
    public void onBackPressed() {
        org.opendroidpdf.app.navigation.BackPressController controller = new org.opendroidpdf.app.navigation.BackPressController(new org.opendroidpdf.app.navigation.BackPressController.Host() {
            @Override public boolean isActionBarHidden() { return getSupportActionBar() != null && !getSupportActionBar().isShowing(); }
            @Override public void exitFullScreen() { OpenDroidPDFActivity.this.exitFullScreen(); }
            @Override public boolean dashboardIsShown() { return OpenDroidPDFActivity.this.dashboardIsShown() && mDocView != null; }
            @Override public void hideDashboard() { OpenDroidPDFActivity.this.hideDashboard(); }
            @Override public org.opendroidpdf.app.navigation.BackPressController.Mode getMode() { return mapToolbarModeToBackMode(mActionBarMode); }
            @Override public void setMode(org.opendroidpdf.app.navigation.BackPressController.Mode mode) { mActionBarMode = mapBackModeToToolbarMode(mode); invalidateOptionsMenu(); }
            @Override public void hideKeyboard() { OpenDroidPDFActivity.this.hideKeyboard(); textOfLastSearch = ""; }
            @Override public void clearSearchQuery() { if (searchToolbarController != null) searchToolbarController.clearQuery(); }
            @Override public void clearSearchResults() { if (mDocView!=null) { mDocView.clearSearchResults(); } }
            @Override public void resetupChildren() { if (mDocView!=null) mDocView.resetupChildren(); }
            @Override public void setViewingMode() { if (mDocView!=null) mDocView.setMode(MuPDFReaderView.Mode.Viewing); }
            @Override public void deselectTextOnCurrentPage() { try { MuPDFView v=(MuPDFView)mDocView.getSelectedView(); if (v!=null) v.deselectText(); } catch (Throwable ignored) {} }
            @Override public boolean hasUnsavedChanges() { return core != null && hasUnsavedChanges(); }
            @Override public boolean canSaveToCurrentUri() { return OpenDroidPDFActivity.this.canSaveToCurrentUri(OpenDroidPDFActivity.this); }
            @Override public void saveInBackground(Callable<?> ok, Callable<?> err) { OpenDroidPDFActivity.this.saveInBackground(ok, err); }
            @Override public void showSaveAsActivity() { OpenDroidPDFActivity.this.showSaveAsActivity(); }
            @Override public void finish() { mIgnoreSaveOnStopThisTime = true; mIgnoreSaveOnDestroyThisTime = true; OpenDroidPDFActivity.this.finish(); }
            @Override public androidx.appcompat.app.AlertDialog.Builder alertBuilder() { return mAlertBuilder; }
            @Override public String t(int resId) { return getString(resId); }
        });
        boolean consumed = controller.onBackPressed();
        if (!consumed) super.onBackPressed();
    }

    private org.opendroidpdf.app.navigation.BackPressController.Mode mapToolbarModeToBackMode(ActionBarMode m) {
        switch (m) {
            case Annot: return org.opendroidpdf.app.navigation.BackPressController.Mode.Annot;
            case Edit: return org.opendroidpdf.app.navigation.BackPressController.Mode.Edit;
            case Search: return org.opendroidpdf.app.navigation.BackPressController.Mode.Search;
            case Selection: return org.opendroidpdf.app.navigation.BackPressController.Mode.Selection;
            case Hidden: return org.opendroidpdf.app.navigation.BackPressController.Mode.Hidden;
            case AddingTextAnnot: return org.opendroidpdf.app.navigation.BackPressController.Mode.AddingTextAnnot;
            case Empty: return org.opendroidpdf.app.navigation.BackPressController.Mode.Empty;
            default: return org.opendroidpdf.app.navigation.BackPressController.Mode.Main;
        }
    }
    private ActionBarMode mapBackModeToToolbarMode(org.opendroidpdf.app.navigation.BackPressController.Mode m) {
        switch (m) {
            case Annot: return ActionBarMode.Annot;
            case Edit: return ActionBarMode.Edit;
            case Search: return ActionBarMode.Search;
            case Selection: return ActionBarMode.Selection;
            case Hidden: return ActionBarMode.Hidden;
            case AddingTextAnnot: return ActionBarMode.AddingTextAnnot;
            case Empty: return ActionBarMode.Empty;
            default: return ActionBarMode.Main;
        }
    }

    
    @Override
    public void performPickFor(FilePicker picker) {
        mFilePicker = picker;
        Intent intent = new Intent(this, OpenDroidPDFFileChooser.class);
        startActivityForResult(intent, FILEPICK_REQUEST);
    }

    public void setTitle() {
        if (core == null || mDocView == null)  return;
        int pageNumber = mDocView.getSelectedItemPosition();
        int totalPages = core.countPages();
        String title = "";
        if (totalPages > 0) {
            title = String.format(Locale.getDefault(), "%d/%d", pageNumber + 1, totalPages);
        }
		String subtitle = "";
		if(core.getFileName() != null) subtitle+=core.getFileName();
        androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
		if(actionBar != null){
			actionBar.setTitle(title);
			actionBar.setSubtitle(subtitle);
		}
    }
    
    
    private void showKeyboard()
    {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if(inputMethodManager!=null && getCurrentFocus() != null) inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

	
    @Override
    public void hideKeyboard()
    {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if(inputMethodManager!=null && getCurrentFocus() != null) inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }
	

    private void enterFullscreen() {
        if(mDocView==null)
            return;
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().hide();
        mActionBarMode = ActionBarMode.Hidden;
        invalidateOptionsMenu();
        mDocView.setScale(1.0f);
        mDocView.setLinksEnabled(false);
        mDocView.setPadding(0, 0, 0, 0);
    }
            
    private void exitFullScreen() {
        if(mDocView==null)
            return;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().show();
        if(mActionBarMode == ActionBarMode.Hidden)
            mActionBarMode = ActionBarMode.Main;
        invalidateOptionsMenu();
        mDocView.setScale(1.0f);
        mDocView.setLinksEnabled(true);
            //Make content appear below the toolbar if completely zoomed out
        TypedValue tv = new TypedValue();
        if(getSupportActionBar().isShowing() && getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
            int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
            mDocView.setPadding(0, actionBarHeight, 0, 0);
            mDocView.setClipToPadding(false);
        }
    }

    private void resetupDocViewAfterActionBarAnimation(final boolean linksEnabled) {
        final androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
        try {
                // Make the Animator accessible
            final Class<?> actionBarImpl = actionBar.getClass();
            final Field currentAnimField = actionBarImpl.getDeclaredField("mCurrentShowAnim");
            currentAnimField.setAccessible(true);
            
                // Monitor the animation
            final Animator currentAnim = (Animator) currentAnimField.get(actionBar);
            currentAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if(mDocView != null) {
                            mDocView.setScale(1.0f);
                            saveViewport(core.getUri()); //So that we show the right page when the mDocView is recreated
                        }
                        mDocView = null;
                        setupDocView();
                        mDocView.setLinksEnabled(linksEnabled);
                    }
                    
                });
        } catch (final Exception ignored) {
                // Nothing to do
        }
    }

    public static File getNotesDir(Context context) {
        File notesDir = new File(Environment.getExternalStorageDirectory(), NOTES_DIR_NAME);
        if (!notesDir.exists()) {
            notesDir.mkdirs();
        }
        migrateLegacyPrivateNotes(context, notesDir);
        migrateLegacyExternalNotes(notesDir);
        return notesDir;
    }

    private static void migrateLegacyPrivateNotes(Context context, File notesDir) {
        try {
            File oldNotesDir = context.getDir("notes", Context.MODE_WORLD_READABLE);
            File[] listOfFiles = oldNotesDir.listFiles();
            if (listOfFiles != null && listOfFiles.length > 0) {
                boolean migratedAny = false;
                for (File child : listOfFiles) {
                    File targetFile = new File(notesDir, child.getName());
                    if (child.isFile() && !targetFile.exists()) {
                        copyFile(child, targetFile);
                        child.delete();
                        migratedAny = true;
                    }
                }
                if (migratedAny) {
                    deleteDirIfEmpty(oldNotesDir);
                }
            }
        } catch (Exception ignored) {
                //Nothing we could do
        }
    }

    private static void migrateLegacyExternalNotes(File notesDir) {
        File legacyDir = new File(Environment.getExternalStorageDirectory(), LEGACY_NOTES_DIR_NAME);
        if (!legacyDir.exists() || NOTES_DIR_NAME.equals(LEGACY_NOTES_DIR_NAME)) {
            return;
        }
        File[] legacyFiles = legacyDir.listFiles();
        if (legacyFiles == null || legacyFiles.length == 0) {
            return;
        }
        for (File child : legacyFiles) {
            File target = new File(notesDir, child.getName());
            if (child.isFile() && !target.exists()) {
                if (!child.renameTo(target)) {
                    try {
                        copyFile(child, target);
                        child.delete();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        deleteDirIfEmpty(legacyDir);
    }

    private static void deleteDirIfEmpty(File directory) {
        if (directory == null) {
            return;
        }
        String[] remaining = directory.list();
        if (remaining == null || remaining.length == 0) {
            //noinspection ResultOfMethodCallIgnored
            directory.delete();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        FileInputStream in = new FileInputStream(source);
        FileOutputStream out = new FileOutputStream(target);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
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

    public int dpToPixel(float sizeInDp) {
        float scale = getResources().getDisplayMetrics().density;
        int dpAsPixels = (int) (sizeInDp*scale + 0.5f);
        return dpAsPixels;
    }

    private boolean memoryLow() {
        ActivityManager.MemoryInfo memoryInfo = getAvailableMemory();
        if (memoryInfo.lowMemory)
            return true;
        else
            return false;
    }

    private ActivityManager.MemoryInfo getAvailableMemory() {
        ActivityManager activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }
}
