package org.opendroidpdf;
import java.util.concurrent.Callable;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PropertyValuesHolder;
import android.animation.ObjectAnimator;
import android.animation.LayoutTransition;
import android.app.ActivityManager;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
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
import android.graphics.drawable.TransitionDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
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
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.helpers.IntentRouter;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.annotation.PenSettingsController;
import org.opendroidpdf.app.preferences.PenPreferences;
import org.opendroidpdf.app.toolbar.ToolbarStateController;
import org.opendroidpdf.core.AlertController;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.core.SaveCallback;
import org.opendroidpdf.core.SaveController;
import org.opendroidpdf.core.SearchController;

public class OpenDroidPDFActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, androidx.appcompat.widget.SearchView.OnQueryTextListener, androidx.appcompat.widget.SearchView.OnCloseListener, FilePicker.FilePickerSupport, TemporaryUriPermission.TemporaryUriPermissionProvider, AnnotationToolbarController.Host, SearchToolbarController.Host, DocumentToolbarController.Host, PenSettingsController.Host
{       
    enum ActionBarMode {Main, Annot, Edit, Search, Selection, Hidden, AddingTextAnnot, Empty};
    private static final String TAG = "OpenDroidPDFActivity";
    private static final String NOTES_DIR_NAME = "OpenDroidPDFNotes";
    private static final String LEGACY_NOTES_DIR_NAME = "PenAndPDFNotes";
    private static final String TAG_FRAGMENT_DASHBOARD = "org.opendroidpdf.app.DashboardFragment";
    private static final String TAG_FRAGMENT_DOCUMENT_HOST = "org.opendroidpdf.app.DocumentHostFragment";
    
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

    private boolean awaitingManageStoragePermission = false;
    private boolean showingStoragePermissionDialog = false;

    public void setAwaitingManageStoragePermission(boolean awaiting) {
        this.awaitingManageStoragePermission = awaiting;
    }

    public boolean isAwaitingManageStoragePermission() {
        return awaitingManageStoragePermission;
    }

    public boolean isShowingStoragePermissionDialog() {
        return showingStoragePermissionDialog;
    }

    public void setShowingStoragePermissionDialog(boolean showing) {
        this.showingStoragePermissionDialog = showing;
    }

    public boolean ensureStoragePermission(Intent intent) {
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
    private PenPreferences penPreferences;
    private PenSettingsController penSettingsController;
    private ExportController exportController;
    private IntentRouter intentRouter;
    private ToolbarStateController toolbarStateController;
    Parcelable mDocViewParcelable;
    private EditText     mPasswordView;
    private ActionBarMode  mActionBarMode = ActionBarMode.Empty;
    private boolean selectedAnnotationIsEditable = false;
    private SearchTaskManager   mSearchTaskManager;
    private AlertDialog.Builder mAlertBuilder;
    private boolean    mLinkHighlight = false;
    private boolean mAlertsActive= false;
    private boolean mReflow = false;
    private AlertController alertController;
    private CancellableAsyncTask<RecentFile, RecentFile> mRenderThumbnailTask = null;
    private AlertDialog mAlertDialog;
    private FilePicker mFilePicker;
    private final SaveController saveController = new SaveController();
    private SaveController.SaveJob activeSaveJob;
    
    private ArrayList<TemporaryUriPermission> temporaryUriPermissions = new ArrayList<TemporaryUriPermission>();

    private boolean mDashboardIsShown = false;

    private void setCoreInstance(OpenDroidPDFCore newCore) {
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
        } else {
            muPdfRepository = null;
            muPdfController = null;
            searchController = null;
            alertController = null;
        }
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
public static String getActualPath(final Context context, final Uri uri) {

    final boolean isKitKat = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT;

    // DocumentProvider
    if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
        // ExternalStorageProvider
        if (isExternalStorageDocument(uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];

            if ("primary".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            }

            // TODO handle non-primary volumes
        }
        // DownloadsProvider
        else if (isDownloadsDocument(uri)) {
            final String id = DocumentsContract.getDocumentId(uri);
            try
            {
                final Long idl = Long.valueOf(id);
                final Uri contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), idl);
                
                return getDataColumn(context, contentUri, null, null);
            }
            catch(NumberFormatException ex) {
                    //Nothing we can do, just keep trying the other options
            }
        }
        // MediaProvider
        else if (isMediaDocument(uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];

            Uri contentUri = null;
            if ("image".equals(type)) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(type)) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(type)) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }

            final String selection = "_id=?";
            final String[] selectionArgs = new String[] {
                    split[1]
            };

            return getDataColumn(context, contentUri, selection, selectionArgs);
        }
    }
    // MediaStore (and general)
    else if ("content".equalsIgnoreCase(uri.getScheme())) {
        return getDataColumn(context, uri, null, null);
    }
    // File
    else if ("file".equalsIgnoreCase(uri.getScheme())) {
        return uri.getPath();
    }
    return uri.getPath();
}

/**
 * Get the value of the data column for this Uri. This is useful for
 * MediaStore Uris, and other file-based ContentProviders.
 *
 * @param context The context.
 * @param uri The Uri to query.
 * @param selection (Optional) Filter used in the query.
 * @param selectionArgs (Optional) Selection arguments used in the query.
 * @return The value of the _data column, which is typically a file path.
 */
public static String getDataColumn(Context context, Uri uri, String selection,
        String[] selectionArgs) {

    Cursor cursor = null;
    final String column = "_data";
    final String[] projection = {
            column
    };

    try {
        cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                null);
        if (cursor != null && cursor.moveToFirst()) {
            final int column_index = cursor.getColumnIndexOrThrow(column);
            return cursor.getString(column_index);
        }
    } finally {
        if (cursor != null)
            cursor.close();
    }
    return null;
}


/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is ExternalStorageProvider.
 */
public static boolean isExternalStorageDocument(Uri uri) {
    return "com.android.externalstorage.documents".equals(uri.getAuthority());
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is DownloadsProvider.
 */
public static boolean isDownloadsDocument(Uri uri) {
    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is MediaProvider.
 */
public static boolean isMediaDocument(Uri uri) {
    return "com.android.providers.media.documents".equals(uri.getAuthority());
}
	
    public void createAlertWaiter() {
        destroyAlertWaiter();
        if (alertController == null) {
            return;
        }
        mAlertsActive = true;
        alertController.start(new AlertController.AlertListener() {
            @Override
            public void onAlert(MuPDFAlert result) {
                showAlertDialog(result);
            }
        });
    }

    private void showAlertDialog(final MuPDFAlert result) {
        if (result == null || !mAlertsActive || isFinishing()) {
            return;
        }
        final MuPDFAlert.ButtonPressed[] pressed = new MuPDFAlert.ButtonPressed[3];
        for (int i = 0; i < pressed.length; i++) {
            pressed[i] = MuPDFAlert.ButtonPressed.None;
        }
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mAlertDialog = null;
                if (!mAlertsActive || alertController == null) {
                    return;
                }
                int index = 0;
                switch (which) {
                    case AlertDialog.BUTTON1:
                        index = 0;
                        break;
                    case AlertDialog.BUTTON2:
                        index = 1;
                        break;
                    case AlertDialog.BUTTON3:
                        index = 2;
                        break;
                }
                result.buttonPressed = pressed[index];
                alertController.reply(result);
            }
        };
        mAlertDialog = mAlertBuilder.create();
        mAlertDialog.setTitle(result.title);
        mAlertDialog.setMessage(result.message);
        switch (result.iconType)
        {
            case Error:
                break;
            case Warning:
                break;
            case Question:
                break;
            case Status:
                break;
        }
        switch (result.buttonGroupType)
        {
            case OkCancel:
                mAlertDialog.setButton(AlertDialog.BUTTON2, getString(R.string.cancel), listener);
                pressed[1] = MuPDFAlert.ButtonPressed.Cancel;
            case Ok:
                mAlertDialog.setButton(AlertDialog.BUTTON1, getString(R.string.okay), listener);
                pressed[0] = MuPDFAlert.ButtonPressed.Ok;
                break;
            case YesNoCancel:
                mAlertDialog.setButton(AlertDialog.BUTTON3, getString(R.string.cancel), listener);
                pressed[2] = MuPDFAlert.ButtonPressed.Cancel;
            case YesNo:
                mAlertDialog.setButton(AlertDialog.BUTTON1, getString(R.string.yes), listener);
                pressed[0] = MuPDFAlert.ButtonPressed.Yes;
                mAlertDialog.setButton(AlertDialog.BUTTON2, getString(R.string.no), listener);
                pressed[1] = MuPDFAlert.ButtonPressed.No;
                break;
        }
        mAlertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                mAlertDialog = null;
                if (mAlertsActive && alertController != null) {
                    result.buttonPressed = MuPDFAlert.ButtonPressed.None;
                    alertController.reply(result);
                }
            }
        });

        mAlertDialog.show();
    }

    public void destroyAlertWaiter() {
        mAlertsActive = false;
        if (mAlertDialog != null) {
            mAlertDialog.cancel();
            mAlertDialog = null;
        }
        if (alertController != null) {
            alertController.stop();
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
            appServices = AppServices.init(getApplication());
            penPreferences = appServices.penPreferences();
            penSettingsController = new PenSettingsController(penPreferences, this);
            exportController = new ExportController(new ExportHost());
            intentRouter = new IntentRouter(new IntentHost());
            toolbarStateController = new ToolbarStateController(new ToolbarHost());
			
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
        if (uri == null)
            return false;
        if (!"file".equalsIgnoreCase(uri.getScheme()))
            return false;
        String path = uri.getPath();
        if (path == null)
            return false;
        return isPathWithinDir(path, getFilesDir())
            || isPathWithinDir(path, getCacheDir())
            || isPathWithinDir(path, getNoBackupFilesDir())
            || isPathWithinDir(path, getExternalFilesDir(null))
            || isPathWithinDir(path, getExternalCacheDir());
    }

    private boolean isPathWithinDir(String path, File dir) {
        if (path == null || dir == null)
            return false;
        try {
            File target = new File(path).getCanonicalFile();
            File root = dir.getCanonicalFile();
            String rootPath = root.getPath();
            return target.getPath().equals(rootPath) || target.getPath().startsWith(rootPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }

    private void showStoragePermissionExplanation(@StringRes int messageResId, final Runnable onContinue)
    {
        if (showingStoragePermissionDialog)
            return;

        final View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_rationale, null, false);
        final TextView messageView = dialogView.findViewById(R.id.dialog_permission_message);
        messageView.setText(messageResId);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_title)
            .setView(dialogView)
            .setPositiveButton(R.string.storage_permission_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        showingStoragePermissionDialog = false;
                        dialogInterface.dismiss();
                        if (onContinue != null)
                            onContinue.run();
                    }
                })
            .setNegativeButton(R.string.storage_permission_not_now, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        showingStoragePermissionDialog = false;
                        dialogInterface.dismiss();
                    }
                })
            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        showingStoragePermissionDialog = false;
                    }
                })
            .create();

        showingStoragePermissionDialog = true;
        dialog.show();
    }

    private void openDocumentFromIntent(Intent intent)
    {
        Log.i(TAG, "openDocumentFromIntent(): data=" + intent.getData() + " type=" + intent.getType());
        //If the core was not restored during onCreate() set it up now
        setupCore();

        if (core != null) //OK, so apparently we have a valid pdf open
        {
            // Try to take permissions
            tryToTakePersistablePermissions(intent);
            rememberTemporaryUriPermission(intent);

            //Setup the mDocView
            Log.i(TAG, "openDocumentFromIntent(): core valid, entering setupDocView()");
            setupDocView();

            //Set the action bar title
            setTitle();

            //Setup the mSearchTaskManager
            setupSearchTaskManager();

            //Update the recent files list
            SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            SharedPreferences.Editor edit = prefs.edit();
            saveRecentFiles(prefs, edit, core.getUri());
            edit.apply();

            // DEBUG autotest: draw a stroke, accept, export, and stash to internal files
            if (BuildConfig.DEBUG && intent.getBooleanExtra("autotest", false) && !mAutoTestRan) {
                mAutoTestRan = true;
                if (mDocView != null) {
                    mDocView.postDelayed(new Runnable() {
                        @Override public void run() {
                            try {
                                // If requested, force ink color to red for visibility in automated tests
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
                                    // Commit the pending ink synchronously into core so export/print will include it
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

                                // Ensure in‑progress ink is committed to core, then export
                                commitPendingInkToCoreBlocking();
                                // Log hasChanges for debugging
                                android.util.Log.i(getString(R.string.app_name), "AUTOTEST_HAS_CHANGES="+hasUnsavedChanges());
                                Uri exported = muPdfRepository != null
                                    ? muPdfRepository.exportDocument(getApplicationContext())
                                    : null;
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
                    }, 1000);
                }
            }
        }
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

        if(mRenderThumbnailTask!=null) 
        {
            mRenderThumbnailTask.cancel();
            mRenderThumbnailTask = null;
        }
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
            
            MenuInflater inflater = getMenuInflater();
            switch (mActionBarMode)
            {
                case Main:
                    if (documentToolbarController != null) {
                        documentToolbarController.inflateMainMenu(menu, inflater);
                    } else {
                        inflater.inflate(R.menu.main_menu, menu);
                    }
                    if (annotationToolbarController != null) {
                        annotationToolbarController.prepareMainMenuShortcuts(menu);
                    }
                    break;
                case Selection:
                    if (annotationToolbarController != null) {
                        annotationToolbarController.inflateSelectionMenu(menu, inflater);
                    } else {
                        inflater.inflate(R.menu.selection_menu, menu);
                    }
                    break;
                case Annot:
                    if (annotationToolbarController != null) {
                        annotationToolbarController.inflateAnnotationMenu(menu, inflater);
                    } else {
                        inflater.inflate(R.menu.annot_menu, menu);
                    }
                    break;
                case Edit:
                    if (annotationToolbarController != null) {
                        annotationToolbarController.inflateEditMenu(menu, inflater);
                    } else {
                        inflater.inflate(R.menu.edit_menu, menu);
                    }
                    break;
                case Search:
                    if (searchToolbarController != null) {
                        searchToolbarController.inflateSearchMenu(menu, inflater);
                    } else {
                        inflater.inflate(R.menu.search_menu, menu);
                    }
                    textOfLastSearch = "";
                case Hidden:
                    inflater.inflate(R.menu.empty_menu, menu);
                    break;
                case AddingTextAnnot:
                    if (annotationToolbarController != null) {
                        annotationToolbarController.inflateAddTextAnnotationMenu(menu, inflater);
                    } else {
                        inflater.inflate(R.menu.add_text_annot_menu, menu);
                    }
                    break;
				case Empty:
					inflater.inflate(R.menu.empty_menu, menu);
					break;
                default:
            }
            return true;
        }

    @Override
    public boolean onClose() {
        hideKeyboard();
        textOfLastSearch = "";
        if (searchToolbarController != null) {
            searchToolbarController.clearQuery();
        }
        mDocView.clearSearchResults();
        mDocView.resetupChildren();
		mDocView.setMode(MuPDFReaderView.Mode.Viewing);
        mActionBarMode = ActionBarMode.Main;
        invalidateOptionsMenu();
        return false;
    }
    
    @Override
    public boolean onQueryTextChange(String query) {//Called when string in search box has changed
            //This is a hacky way to determine when the user has reset the text field with the X button 
        if (query.length() == 0 && latestTextInSearchBox.length() > 1) {
            if (mSearchTaskManager != null) mSearchTaskManager.stop();
            textOfLastSearch = "";
            if(mDocView.hasSearchResults())
            {
                mDocView.clearSearchResults();
                mDocView.resetupChildren();
            }
        }
        latestTextInSearchBox = query;
        return false;
    }

    @Override 
    public boolean onQueryTextSubmit(String query) {//For search
        mDocView.requestFocus();
        hideKeyboard();
        if(!query.equals(textOfLastSearch)) //only perform a search if the query has changed    
            search(1);
        return true; //We handle this here and don't want onNewIntent() to be called
    }

    @Override
    public void showInkColorDialog() {
        final SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS);
        final String key = SettingsActivity.PREF_INK_COLOR;
        final CharSequence[] names = getResources().getTextArray(R.array.pen_color_names);
        final CharSequence[] values = ColorPalette.getColorNumbers();
        int currentIndex = 0;
        try {
            currentIndex = Integer.parseInt(prefs.getString(key, "0"));
        } catch (NumberFormatException ignore) {}

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(getString(R.string.ink_color));
        b.setSingleChoiceItems(names, currentIndex, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                try {
                    finalizePendingInkBeforePenSettingChange();
                    String sel = values[which].toString();
                    int colorIndex = Integer.parseInt(sel);
                    if (penPreferences != null) {
                        penPreferences.setColorIndex(colorIndex);
                    } else {
                        prefs.edit().putString(SettingsActivity.PREF_INK_COLOR, Integer.toString(colorIndex)).apply();
                    }
                    onPenPreferenceChanged(SettingsActivity.PREF_INK_COLOR);
                } catch (NumberFormatException ignore) {
                }
                dialog.dismiss();
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
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
        if (mPageBeforeInternalLinkHit >= 0) {
            setViewport(
                mPageBeforeInternalLinkHit,
                mNormalizedScaleBeforeInternalLinkHit,
                mNormalizedXScrollBeforeInternalLinkHit,
                mNormalizedYScrollBeforeInternalLinkHit);
        }
        mPageBeforeInternalLinkHit = -1;
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { //Handel clicks in the options menu
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

	private void tryToTakePersistablePermissions(Intent intent) {
        Uri uri = intent.getData();
		if (android.os.Build.VERSION.SDK_INT >= 19)
		{
			try
			{
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
			}
			catch(Exception e)
			{
					//Nothing we can do if we don't get the permission
			}
            finally
            {
                try
                {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
                catch(Exception e)
                {
                        //Nothing we can do if we don't get the permission
                    Log.i(getString(R.string.app_name), "Failed to take persistable write uri permissions for "+uri+" Exception: "+e);
                }
            }
		}
	}
	

    public void setupCore() {//Called during onResume()		
        if (core == null) {            
            mDocViewNeedsNewAdapter = true;
            Intent intent = getIntent();
		
            Uri uri = intent.getData();
            Log.i(TAG, "setupCore(): intent=" + intent + " uri=" + uri);
            
            OpenDroidPDFCore newCore = null;
            try 
            {
                newCore = new OpenDroidPDFCore(this, uri);
                if(newCore == null) throw new Exception(getResources().getString(R.string.unable_to_interpret_uri)+" "+uri);
            }
            catch (Exception e)
            {
                Log.e(getString(R.string.app_name), "Failed to open document uri=" + uri, e);
                AlertDialog alert = mAlertBuilder.create();
                alert.setTitle(R.string.cannot_open_document);
                alert.setMessage(getResources().getString(R.string.reason)+": "+e.toString());
                alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                });
                alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                alert.show();
                newCore = null;
            }

            setCoreInstance(newCore);
		
            if (core != null && core.needsPassword()) {
                requestPassword();
            }
            if (core != null && core.countPages() == 0) {
                setCoreInstance(null);
            }
            if (core != null) {
                Log.i(TAG, "setupCore(): core ready, pages=" + core.countPages());
                    /*There seems to be some bug in this that sometimes make the native code lock up. As it is not so important I am disabeling this for now*/
                    //Start receiving alerts
                // createAlertWaiter();
                // core.startAlerts();
                
                    //Make the core read the current preferences
                SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
                core.onSharedPreferenceChanged(prefs,"");
            }    
        }
    }
    
        
    public void setupSearchTaskManager() { //Is called during onResume()
        if (searchController == null) {
            mSearchTaskManager = null;
            return;
        }
            //Create a new search task (the core might have changed)
		mSearchTaskManager = new SearchTaskManager(this, searchController) {
                    @Override
                    protected void onTextFound(SearchResult result) {
                        mDocView.addSearchResult(result);
                    }
                    
                    @Override
                    protected void goToResult(SearchResult result) {
                            //Make the docview show the hits
                        mDocView.resetupChildren();
                            // Ask the ReaderView to move to the resulting page
                        if(mDocView.getSelectedItemPosition() != result.getPageNumber())
                            mDocView.setDisplayedViewIndex(result.getPageNumber());
                            // ... and the region on the page
                        RectF resultRect = result.getFocusedSearchBox();
                        if(resultRect!=null)
                        {
                            mDocView.doNextScrollWithCenter();
                            mDocView.setDocRelXScroll(resultRect.left);
                            mDocView.setDocRelYScroll(resultRect.top);
                        }
                    }
                };
    }
    
    public void setupDocView() { //Is called during onResume()
            //If we don't even have a core there is nothing to do
        if(core == null) {
            Log.i(TAG, "setupDocView(): core is null, aborting setup");
            return;
        }

        Log.i(TAG, "setupDocView(): core available, docView=" + mDocView);

        hideDashboard();
        final DocumentHostFragment documentHostFragment = showDocumentHostFragment();
        final ViewGroup documentContainer = documentHostFragment != null ? documentHostFragment.getDocumentContainer() : null;
        Log.i(TAG, "setupDocView(): hostFragment=" + documentHostFragment + " container=" + documentContainer);

            //If the doc view is not present create it
        if(mDocView == null)
        {
            mDocView = new MuPDFReaderView(this) {
                    
                    @Override
                    public void setMode(Mode m) {
                        super.setMode(m);

                        switch(m)
                        {
                            case Viewing:
                                mActionBarMode = ActionBarMode.Main;
                                break;
                            case Drawing:
                            case Erasing:
                                mActionBarMode = ActionBarMode.Annot;
                                break;
                            case Selecting:
                                mActionBarMode = ActionBarMode.Selection;
                                break;
                            case AddingTextAnnot:
                                mActionBarMode = ActionBarMode.AddingTextAnnot;
                                break;
                        }
                        invalidateOptionsMenu();
                    }
                    
                    @Override
                    protected void onMoveToChild(int pageNumber) {
                        setTitle();
                            //We deselect annotations on page changes so let the action bar act accordingly
                        if(mActionBarMode == ActionBarMode.Edit)
                        {
                            mActionBarMode = ActionBarMode.Main;
                            invalidateOptionsMenu();
                        }
                    }

                    @Override
                    protected void onTapMainDocArea() {
                        if (mActionBarMode == ActionBarMode.Edit || mActionBarMode == ActionBarMode.AddingTextAnnot) 
                        {
                            mActionBarMode = ActionBarMode.Main;
                            invalidateOptionsMenu();
                        }
                    }
                
                    @Override
                    protected void onTapTopLeftMargin() {
                        if (getSupportActionBar().isShowing())
                            smartMoveBackwards();
                        else {
                            mDocView.setDisplayedViewIndex(getSelectedItemPosition()-1);
                            mDocView.setScale(1.0f);
                            mDocView.setNormalizedScroll(0.0f,0.0f);
                        }
                    };

                    @Override
                    protected void onBottomRightMargin() {
                        if (getSupportActionBar().isShowing())
                            smartMoveForwards();
                        else {
                            mDocView.setDisplayedViewIndex(getSelectedItemPosition()+1);
                            mDocView.setScale(1.0f);
                            mDocView.setNormalizedScroll(0.0f,0.0f);
                        }
                    };
                
                    @Override
                    protected void onDocMotion() {

                    }

                    @Override
                    protected void addTextAnnotFromUserInput(final Annotation annot) {

						mAlertDialog = mAlertBuilder.create();
                        final View dialogView = LayoutInflater.from(OpenDroidPDFActivity.this).inflate(R.layout.dialog_text_input, null, false);
                        final EditText input = dialogView.findViewById(R.id.dialog_text_input);
                        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_NORMAL|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                        input.setHint(getString(R.string.add_text_placeholder));
                        input.setGravity(Gravity.TOP | Gravity.START);
                        input.setHorizontallyScrolling(false);
                        input.setBackgroundDrawable(null);
                        if(annot != null && annot.text != null) input.setText(annot.text);
                        mAlertDialog.setView(dialogView);
                        mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.save), new DialogInterface.OnClickListener() 
                                {
                                    public void onClick(DialogInterface dialog, int whichButton) 
                                        {
                                            ((MuPDFPageView)getSelectedView()).deleteSelectedAnnotation();
                                            annot.text = input.getText().toString();
                                            addTextAnnotion(annot);
                                            mAlertDialog.setOnCancelListener(null);
                                        }
                            });
                        mAlertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancel), new DialogInterface.OnClickListener() 
                                {public void onClick(DialogInterface dialog, int whichButton)
                                        {
                                            ((MuPDFPageView)getSelectedView()).deselectAnnotation();
                                            mAlertDialog.setOnCancelListener(null);
                                        }
                            });
                        if(annot != null && annot.text != null)
                            mAlertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.delete), new DialogInterface.OnClickListener() 
                                {public void onClick(DialogInterface dialog, int whichButton)
                                        {
                                            ((MuPDFPageView)getSelectedView()).deleteSelectedAnnotation();
                                            mAlertDialog.setOnCancelListener(null);
                                        }
                                });
                        mAlertDialog.setCanceledOnTouchOutside(true);
                        mAlertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                public void onCancel(DialogInterface dialog) {
                                    ((MuPDFPageView)getSelectedView()).deselectAnnotation();
                                }
                            });
                        mAlertDialog.show();
                        input.requestFocus();
                    }

                    @Override
                    protected void onHit(Hit item) {
                        switch(item){
                            case Annotation:
                            case InkAnnotation:
                                mActionBarMode = ActionBarMode.Edit;
                                invalidateOptionsMenu();
                                selectedAnnotationIsEditable = ((MuPDFPageView)getSelectedView()).selectedAnnotationIsEditable();
                                break;
                            case TextAnnotation:
                                break;
                            case Nothing:
                                if(mActionBarMode != ActionBarMode.Search && mActionBarMode != ActionBarMode.Hidden)
                                {
                                    mActionBarMode = ActionBarMode.Main;
                                    invalidateOptionsMenu();
                                }
                                break;
                            case LinkInternal:
                                if(mDocView.linksEnabled()) {
                                    mPageBeforeInternalLinkHit = getSelectedItemPosition();
                                    mNormalizedScaleBeforeInternalLinkHit = getNormalizedScale();
                                    mNormalizedXScrollBeforeInternalLinkHit = getNormalizedXScroll();
                                    mNormalizedYScrollBeforeInternalLinkHit = getNormalizedYScroll();
                                }
                                mActionBarMode = ActionBarMode.Main;
                                invalidateOptionsMenu();
                                break;
                        }
                    }

                    @Override
                    protected void onNumberOfStrokesChanged(int numberOfStrokes) {
                        invalidateOptionsMenu();
                    }
                
                };
            Log.i(TAG, "setupDocView(): created new MuPDFReaderView");
            mDocViewNeedsNewAdapter = true;

				//Make content appear below the toolbar if completely zoomed out
            TypedValue tv = new TypedValue();
            if(getSupportActionBar().isShowing() && getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
                int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
                mDocView.setPadding(0, actionBarHeight, 0, 0);
                mDocView.setClipToPadding(false);
            }
            
        }

        attachDocViewToContainer(documentContainer);
        Log.i(TAG, "setupDocView(): attachDocViewToContainer invoked with container=" + documentContainer);
        if(mDocView!=null)
        {
				//Synchronize modes of DocView and ActionBar 
            mDocView.setMode(mDocView.getMode());
			
                //Clear the search results 
            mDocView.clearSearchResults();  
            
                //Ascociate the mDocView with a new adapter if necessary
            if(mDocViewNeedsNewAdapter) {
                if (muPdfController == null && muPdfRepository != null) {
                    muPdfController = new MuPdfController(muPdfRepository);
                }
                mDocView.setAdapter(new MuPDFPageAdapter(this, this, muPdfController));
                mDocViewNeedsNewAdapter = false;
            }
			
                //Reinstate last viewport if it was recorded
            restoreViewport();

                //Restore the state of mDocView from its saved state in case there is one
            if(mDocViewParcelable != null) mDocView.onRestoreInstanceState(mDocViewParcelable);
            mDocViewParcelable=null;
            
                //Make the mDocView read the prefernces 
            SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);   
            mDocView.onSharedPreferenceChanged(prefs,"");
        }
    }

    private void attachDocViewToContainer(ViewGroup container) {
        if (container == null || mDocView == null) {
            Log.w(TAG, "attachDocViewToContainer: missing container/docView container=" + container + " docView=" + mDocView);
            return;
        }

        if (mDocView.getParent() == container) {
            Log.i(TAG, "attachDocViewToContainer: doc view already attached to container");
            return;
        }

        if (mDocView.getParent() instanceof ViewGroup) {
            ((ViewGroup) mDocView.getParent()).removeView(mDocView);
        }

        final FrameLayout.LayoutParams layoutParams =
            new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        container.addView(mDocView, 0, layoutParams);
        Log.i(TAG, "attachDocViewToContainer: attached doc view to " + container);
    }

	private void hideDashboard() {
        final DashboardFragment fragment = getDashboardFragment();
        final ScrollView entryScreenScrollView = fragment != null ? fragment.getScrollView() : null;
        final LinearLayout entryScreenLayout = fragment != null ? fragment.getEntryLayout() : null;

        if (entryScreenScrollView == null || entryScreenLayout == null) {
            mDashboardIsShown = false;
            return;
        }

        if(entryScreenLayout.getChildCount() > 0)
            entryScreenLayout.removeViews(0,entryScreenLayout.getChildCount());
        mActionBarMode = ActionBarMode.Main;
        mDashboardIsShown = false;
        invalidateOptionsMenu();

        final Drawable background = entryScreenScrollView.getBackground();
        final int animationTime = (int)entryScreenLayout.getLayoutTransition().getDuration(LayoutTransition.DISAPPEARING);
        if (background instanceof TransitionDrawable) {
            TransitionDrawable transition = (TransitionDrawable) background;
            transition.reverseTransition(animationTime);
            org.opendroidpdf.app.AppCoroutines.launchMainDelayed(
                    org.opendroidpdf.app.AppCoroutines.mainScope(),
                    animationTime,
                    new Runnable() {
                        @Override
                        public void run() {
                            entryScreenScrollView.setVisibility(View.INVISIBLE);
                        }
                    });
        } else {
            entryScreenScrollView.setVisibility(View.INVISIBLE);
        }
    }

    
    private boolean dashboardIsShown() {
        return mDashboardIsShown;
    }

    
	private void showDashboard() {
        if(dashboardIsShown())
            return;

        final DashboardFragment fragment = showDashboardFragment();
        final ScrollView entryScreenScrollView = fragment.getScrollView();
        final LinearLayout entryScreenLayout = fragment.getEntryLayout();

        if (entryScreenScrollView == null || entryScreenLayout == null) {
            Log.w(getString(R.string.app_name), "Dashboard views unavailable; cannot render entry screen");
            return;
        }

        mDashboardIsShown = true;
        
        mActionBarMode = ActionBarMode.Empty;
        invalidateOptionsMenu();
        entryScreenLayout.removeAllViews();
        entryScreenScrollView.scrollTo(0, 0);
        
        Animator scrollUp = ObjectAnimator.ofPropertyValuesHolder((Object)null, PropertyValuesHolder.ofFloat("translationY", entryScreenScrollView.getHeight(), 0));
        scrollUp.setInterpolator(new AccelerateDecelerateInterpolator());
        Animator scrollDown = ObjectAnimator.ofPropertyValuesHolder((Object)null, PropertyValuesHolder.ofFloat("translationY", 0, entryScreenScrollView.getHeight()));
        scrollDown.setInterpolator(new AccelerateDecelerateInterpolator());

        LayoutTransition layoutTransition;
        layoutTransition = new LayoutTransition();
        layoutTransition.setAnimator(LayoutTransition.APPEARING, scrollUp);
        layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, scrollDown);
        entryScreenLayout.setLayoutTransition(layoutTransition);

        entryScreenScrollView.setVisibility(View.VISIBLE);
        
        final Drawable background = entryScreenScrollView.getBackground();
        int animationTime = (int)entryScreenLayout.getLayoutTransition().getDuration(LayoutTransition.DISAPPEARING);

        if (background instanceof TransitionDrawable) {
            TransitionDrawable transition = (TransitionDrawable) background;
            transition.startTransition(animationTime);
        }
        
        
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        int elevation = 5;
        int elevationInc = 5;
        CardView fixedcard;
        ImageView icon;
        TextView title;
        TextView subtitle;

        fixedcard = (CardView)getLayoutInflater().inflate(R.layout.dashboard_card, entryScreenLayout, false);
        icon = (ImageView)fixedcard.findViewById(R.id.image);
        title = (TextView)fixedcard.findViewById(R.id.title);
        subtitle = (TextView)fixedcard.findViewById(R.id.subtitle);
        icon.setImageResource(R.drawable.ic_open);
        title.setText(R.string.entry_screen_open_document);
        subtitle.setText(R.string.entry_screen_open_document_summ);
        fixedcard.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					openDocument();
				}
			});
        fixedcard.setCardElevation(elevation);
        entryScreenLayout.addView(fixedcard);
        
        fixedcard = (CardView)getLayoutInflater().inflate(R.layout.dashboard_card, entryScreenLayout, false);
        icon = (ImageView)fixedcard.findViewById(R.id.image);
        title = (TextView)fixedcard.findViewById(R.id.title);
        subtitle = (TextView)fixedcard.findViewById(R.id.subtitle);
        icon.setImageResource(R.drawable.ic_new);
        title.setText(R.string.entry_screen_new_document);
        subtitle.setText(R.string.entry_screen_new_document_summ);
        fixedcard.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
                    showOpenNewDocumentDialoge();
				}
			});
        fixedcard.setCardElevation(elevation);
        entryScreenLayout.addView(fixedcard);
        
        fixedcard = (CardView)getLayoutInflater().inflate(R.layout.dashboard_card, entryScreenLayout, false);
        icon = (ImageView)fixedcard.findViewById(R.id.image);
        title = (TextView)fixedcard.findViewById(R.id.title);
        subtitle = (TextView)fixedcard.findViewById(R.id.subtitle);
        icon.setImageResource(R.drawable.ic_settings);
        title.setText(R.string.entry_screen_settings);
        subtitle.setText(R.string.entry_screen_settings_summ);
        fixedcard.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					Intent intent = new Intent(view.getContext(), SettingsActivity.class);
					view.getContext().startActivity(intent);
					overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
				}
			});
        fixedcard.setCardElevation(elevation);
        entryScreenLayout.addView(fixedcard);

        boolean beforeFirstCard = true;
        int cardNumber = 0;
        RecentFilesList recentFilesList = new RecentFilesList(getApplicationContext(), prefs);
        for(final RecentFile recentFile: recentFilesList) {
            cardNumber++;
            if(cardNumber > numberRecentFilesInMenu) break;
            
            if (!OpenDroidPDFCore.canReadFromUri(this, recentFile.getUri()))
                continue;

            if(beforeFirstCard)
            {
                final CardView recentFilesListHeading = (CardView)getLayoutInflater().inflate(R.layout.dashboard_recent_files_list_heading, entryScreenLayout, false);
                entryScreenLayout.addView(recentFilesListHeading);
                beforeFirstCard = false;
            }
            
            final CardView card = (CardView)getLayoutInflater().inflate(R.layout.dashboard_card_recent_file, entryScreenLayout, false);

            elevation += elevationInc;
            card.setCardElevation(elevation);
            TextView tv = (TextView)card.findViewById(R.id.title);
            tv.setText(recentFile.getDisplayName());
            
            card.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        checkSaveThenCall(new Callable<Void>(){
                                public Void call() {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, recentFile.getUri(), card.getContext(), OpenDroidPDFActivity.class);
                                    intent.putExtra(Intent.EXTRA_TITLE, recentFile.getDisplayName());
                                    startActivity(intent);
                                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                                    hideDashboard();
                                    finish();
                                    return null;
                                }});
                    }
                });

            final RecentFile capturedRecentFile = recentFile;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (memoryLow()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                entryScreenLayout.addView(card);
                            }
                        });
                        return;
                    }
                    PdfThumbnailManager pdfThumbnailManager = new PdfThumbnailManager(card.getContext());
                    final BitmapDrawable drawable = pdfThumbnailManager.getDrawable(getResources(), capturedRecentFile.getThumbnailString());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (drawable != null) {
                                ImageView imageView = (ImageView) card.findViewById(R.id.image);
                                imageView.setImageDrawable(drawable);
                                final Matrix matrix = imageView.getImageMatrix();
                                final float imageWidth = drawable.getIntrinsicWidth();
                                final int screenWidth = entryScreenLayout.getWidth();
                                final float scaleRatio = screenWidth / imageWidth;
                                matrix.postScale(scaleRatio, scaleRatio);
                                imageView.setImageMatrix(matrix);
                            }
                            entryScreenLayout.addView(card);
                        }
                    });
                }
            }).start();
        }
	}
    
    

    private DashboardFragment getDashboardFragment() {
        FragmentManager fm = getSupportFragmentManager();
        androidx.fragment.app.Fragment fragment = fm.findFragmentById(R.id.content_fragment_container);
        if (fragment instanceof DashboardFragment) {
            return (DashboardFragment) fragment;
        }
        fragment = fm.findFragmentByTag(TAG_FRAGMENT_DASHBOARD);
        if (fragment instanceof DashboardFragment) {
            return (DashboardFragment) fragment;
        }
        return null;
    }

    private DocumentHostFragment getDocumentHostFragment() {
        FragmentManager fm = getSupportFragmentManager();
        androidx.fragment.app.Fragment fragment = fm.findFragmentById(R.id.content_fragment_container);
        if (fragment instanceof DocumentHostFragment) {
            return (DocumentHostFragment) fragment;
        }
        fragment = fm.findFragmentByTag(TAG_FRAGMENT_DOCUMENT_HOST);
        if (fragment instanceof DocumentHostFragment) {
            return (DocumentHostFragment) fragment;
        }
        return null;
    }

    private DashboardFragment showDashboardFragment() {
        DashboardFragment fragment = getDashboardFragment();
        if (fragment != null && fragment.isAdded()) {
            return fragment;
        }
        fragment = new DashboardFragment();
        FragmentTransaction transaction = getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.content_fragment_container, fragment, TAG_FRAGMENT_DASHBOARD);
        commitFragmentTransaction(transaction);
        return fragment;
    }

    private DocumentHostFragment showDocumentHostFragment() {
        DocumentHostFragment fragment = getDocumentHostFragment();
        if (fragment != null && fragment.isAdded()) {
            Log.i(TAG, "showDocumentHostFragment(): reusing existing fragment " + fragment);
            return fragment;
        }
        fragment = new DocumentHostFragment();
        FragmentTransaction transaction = getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.content_fragment_container, fragment, TAG_FRAGMENT_DOCUMENT_HOST);
        commitFragmentTransaction(transaction);
        Log.i(TAG, "showDocumentHostFragment(): committed new fragment " + fragment);
        return fragment;
    }

    private void commitFragmentTransaction(FragmentTransaction transaction) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.isStateSaved()) {
            transaction.commitAllowingStateLoss();
            fm.executePendingTransactions();
        } else {
            transaction.commitNow();
        }
    }

    public void showOpenDocumentDialog() {
		Intent intent = null;
		if (android.os.Build.VERSION.SDK_INT < 19)
        {
			intent = new Intent(this, OpenDroidPDFFileChooser.class);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			intent.setAction(Intent.ACTION_EDIT);
		}
		else
		{
            Intent openDocumentIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            openDocumentIntent.addCategory(Intent.CATEGORY_OPENABLE);
            openDocumentIntent.setType("application/pdf");
            openDocumentIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
			intent = openDocumentIntent;
		}
		
		startActivityForResult(intent, EDIT_REQUEST);
		overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
	}

    
    public void openNewDocument(final String filename) throws java.io.IOException {		
        File dir = getNotesDir(this);
		File file = new File(dir, filename);
		final Uri uri = Uri.fromFile(file);
		
		OpenDroidPDFCore.createEmptyDocument(this, uri);

        checkSaveThenCall(new Callable<Void>(){
                public Void call() {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri, getApplicationContext(), OpenDroidPDFActivity.class);
                    intent.putExtra(Intent.EXTRA_TITLE, filename);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivity(intent);
                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                    hideDashboard();
                    finish();
                    return null;
                }});
	}

    
    public void openDocument() {
        checkSaveThenCall(new Callable<Void>(){
                public Void call() {
                    showOpenDocumentDialog();
                    return null;
                }});
    }
    
    public void checkSaveThenCall(final Callable callable) {
		if (core!=null && hasUnsavedChanges()) {
            final OpenDroidPDFActivity activity = this;
            
			DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (which == AlertDialog.BUTTON_POSITIVE) {
							if(canSaveToCurrentUri(activity))
							{
                                saveInBackground(callable,
                                                 new Callable<Void>() {
                                                     @Override
                                                     public Void call() {
                                                         showInfo(getString(R.string.error_saveing));
                                                         return null;
                                                     }
                                                 }
                                                 );
							}
							else
							{
								showSaveAsActivity();
							}
						}
						if (which == AlertDialog.BUTTON_NEGATIVE) {
                            try{callable.call();}catch(Exception e){}
						}
						if (which == AlertDialog.BUTTON_NEUTRAL) {
						}
					}
                    };
			AlertDialog alert = mAlertBuilder.create();
			alert.setTitle(getString(R.string.save_question));
			alert.setMessage(getString(R.string.document_has_changes_save_them));
			if (canSaveToCurrentUri(this))
				alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.yes), listener);
			else
				alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.saveas), listener);
			alert.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancel), listener);
			alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), listener);
			alert.show();
		}
		else
		{
            try{callable.call();}catch(Exception e){}
		}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {        
        if (requestCode == MANAGE_STORAGE_REQUEST)
        {
            awaitingManageStoragePermission = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && Environment.isExternalStorageManager())
            {
                if (Intent.ACTION_VIEW.equals(getIntent().getAction()))
                    openDocumentFromIntent(getIntent());
            }
            else
            {
                Toast.makeText(this, R.string.cannot_open_document, Toast.LENGTH_LONG).show();
            }
            return;
        }
        switch (requestCode) {
            case EDIT_REQUEST:
                overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
                if(resultCode == AppCompatActivity.RESULT_OK)
                {
                    if (intent != null) {
                        getIntent().setAction(Intent.ACTION_VIEW);
                        getIntent().setData(intent.getData());
                        getIntent().setFlags((getIntent().getFlags() & ~Intent.FLAG_GRANT_WRITE_URI_PERMISSION & ~Intent.FLAG_GRANT_READ_URI_PERMISSION) | (intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) | (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION));//Set the read and writ flags to what they are in the received intent
                        
                        if (core != null) {
                            core.onDestroy();
                            setCoreInstance(null);
                        }
//                        tryToTakePersistablePermissions(intent);//No need to do this, is done during onResume()
//                        rememberTemporaryUriPermission(intent);//No need to do this, is done during onResume()
//                        onResume();//New core and new docview are setup during onResume(), which is automatically called after onActivityResult()
                        hideDashboard();
                    }
                }
                break;
            case OUTLINE_REQUEST:
                if (resultCode >= 0 && mDocView!=null)
                    mDocView.setDisplayedViewIndex(resultCode);
                break;
            case PRINT_REQUEST:
                // if (resultCode == RESULT_CANCELED)
                //     showInfo(getString(R.string.print_failed));
                break;
            case SAVEAS_REQUEST:
                overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
                if (resultCode == RESULT_OK) {
                    final Uri uri = intent.getData();
                    File file = null;
                    if (uri!=null)
                        file = new File(getActualPath(this, uri));
					if(file != null && file.isFile() && file.length() > 0) //Warn if file already exists
                    {
                        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    if (which == AlertDialog.BUTTON_POSITIVE) {
                                        saveAsInBackground(uri,
                                                           new Callable<Void>() {
                                                               @Override
                                                               public Void call() {
                                                                   setTitle();
                                                                   return null;
                                                               }
                                                           },
                                                           new Callable<Void>() {
                                                               @Override
                                                               public Void call() {
                                                                   showInfo(getString(R.string.error_saveing));
                                                                   return null;
                                                               }
                                                           }
                                                           );
                                    }
                                    if (which == AlertDialog.BUTTON_NEGATIVE) {
                                    }
                                }
                            };
                        AlertDialog alert = mAlertBuilder.create();
                        alert.setTitle(R.string.overwrite_question);
                        alert.setMessage(getString(R.string.overwrite)+" "+uri.getPath()+" ?");
                        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.yes), listener);
                        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), listener);
                        alert.show();
                    }
                    else
                    {
                        if(uri == null)
                            showInfo(getString(R.string.error_saveing));
                        else
                        {
                            saveAsInBackground(uri,
                                               new Callable<Void>() {
                                                   @Override
                                                   public Void call() {
                                                       setTitle();
                                                       return null;
                                                   }
                                               },
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
                }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void showSaveAsActivity() {
        if(core == null)
            return;
        if (android.os.Build.VERSION.SDK_INT < 19)
        {
            Intent intent = new Intent(getApplicationContext(),OpenDroidPDFFileChooser.class);
            if (core.getUri() != null) intent.setData(core.getUri());
            intent.putExtra(Intent.EXTRA_TITLE, core.getFileName());
            intent.setAction(Intent.ACTION_PICK);
            mIgnoreSaveOnStopThisTime = true;
            startActivityForResult(intent, SAVEAS_REQUEST);
        }
        else
        {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                    
                // Filter to only show results that can be "opened", such as
                // a file (as opposed to a list of contacts or timezones).
            intent.addCategory(Intent.CATEGORY_OPENABLE);
                                    
                // Create a file with the requested MIME type.
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_TITLE, core.getFileName());
            mIgnoreSaveOnStopThisTime = true;
            startActivityForResult(intent, SAVEAS_REQUEST);
            overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
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
                    if (successCallable != null) {
                        try {
                            successCallable.call();
                        } catch (Exception e) {
                            showInfo(getString(R.string.error_saveing)+": "+e);
                        }
                    }
                } else {
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
        if (core != null && mDocView != null) {
            SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            setViewport(prefs, core.getUri());
        }
    }


    private void setViewport(SharedPreferences prefs, Uri uri) {
        setViewport(prefs.getInt("page"+uri.toString(), 0),prefs.getFloat("normalizedscale"+uri.toString(), 0.0f),prefs.getFloat("normalizedxscroll"+uri.toString(), 0.0f), prefs.getFloat("normalizedyscroll"+uri.toString(), 0.0f));
    }

    
    private void setViewport(int page, float normalizedscale, float normalizedxscroll, float normalizedyscroll) {
        mDocView.setDisplayedViewIndex(page);
        mDocView.setNormalizedScale(normalizedscale);
        mDocView.setNormalizedScroll(normalizedxscroll, normalizedyscroll);
    }


    private void saveRecentFiles(SharedPreferences prefs, final SharedPreferences.Editor edit, Uri uri) {
            //Read the recent files list from preferences
        final RecentFilesList recentFilesList = new RecentFilesList(getApplicationContext(), prefs);                    
            //Add the current file
        RecentFile recentFile = new RecentFile(uri.toString(), core.getFileName());
        recentFilesList.push(recentFile);
        
            //Write the recent files list
        recentFilesList.write(edit);
        edit.apply();

            //Generate and add a thubnail in the background
        if(mRenderThumbnailTask!=null)
            mRenderThumbnailTask.cancel();

        if (muPdfRepository == null || muPdfController == null) {
            return;
        }
        final PdfThumbnailManager thumbnailManager = new PdfThumbnailManager(this, muPdfController);
        mRenderThumbnailTask = new CancellableAsyncTask<RecentFile, RecentFile>(new MuPDFCancellableTaskDefinition<RecentFile,RecentFile>(muPdfRepository) 
            {
                @Override
                public RecentFile doInBackground(MuPDFCore.Cookie cookie, RecentFile... recentFile0) {
                    RecentFile recentFile = recentFile0[0];
                    int bmWidth;
                    int bmHeight;
                    Display display = getWindowManager().getDefaultDisplay();
                    if (android.os.Build.VERSION.SDK_INT < 13) {
                        bmWidth = Math.min(display.getWidth(), display.getHeight());
                    } else {
                        Point size = new Point();
                        display.getSize(size);
                        bmWidth = Math.min(size.x,size.y);
                    }
                    bmHeight = (int)((float)bmWidth*0.5);

                    String thunbnailString = thumbnailManager.generate(bmWidth, bmHeight, cookie);
                    if(thunbnailString != null && !cookie.aborted())
                    {
                        recentFile.setThumbnailString(thunbnailString);
                        recentFilesList.write(edit);
                        edit.apply();
                    }
                    
                    return recentFile;
                }
            })
                               {
                                       // @Override
                                       // protected void onPostExecute(final RecentFile recentFile) {                       
                                       //     recentFilesList.push(recentFile);//this replaces the previously pushed version
                                       //     recentFilesList.write(edit);
                                       //     edit.apply();
                                       // }
            };
        mRenderThumbnailTask.execute(recentFile);
    }
    
    
    private void saveViewportAndRecentFiles(Uri uri) {
        if(uri != null)
        {
            SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            SharedPreferences.Editor edit = prefs.edit();
            saveRecentFiles(prefs, edit, uri);
            saveViewport(edit, uri.toString());
            edit.apply();
        }
    }
    

    private void saveViewport(Uri uri) {
        if(uri != null)
        {
            SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            SharedPreferences.Editor edit = prefs.edit();
            saveViewport(edit, uri.toString());
            edit.apply();
        }
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
        public void commitPendingInkToCoreBlocking() {
            OpenDroidPDFActivity.this.commitPendingInkToCoreBlocking();
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

    private void showInfo(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }    

    
    public void requestPassword() {
        final View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null, false);
        mPasswordView = dialogView.findViewById(R.id.dialog_text_input);
        mPasswordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        mPasswordView.setTransformationMethod(new PasswordTransformationMethod());
        mPasswordView.setHint(R.string.enter_password);
        mPasswordView.setSingleLine(true);
        mPasswordView.setMinLines(1);

        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.enter_password);
        alert.setView(dialogView);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (core==null || !core.authenticatePassword(mPasswordView.getText().toString()))
                                    requestPassword();
                                
                            }
                        });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        });
        alert.show();
    }


    private void showGoToPageDialoge() {

		mAlertDialog = mAlertBuilder.create();

		final View editTextLayout = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null, false);
		final EditText input = editTextLayout.findViewById(R.id.dialog_text_input);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine();
		input.setBackgroundDrawable(null);
		input.setHint(getString(R.string.dialog_gotopage_hint));
        input.setFocusable(true);
		mAlertDialog.setTitle(R.string.dialog_gotopage_title);
		DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which) {
					if (which == AlertDialog.BUTTON_POSITIVE) {
							// User clicked OK button
                        int pageNumber;
                        try{
                            pageNumber = Integer.parseInt(input.getText().toString());
                        }catch(NumberFormatException e){
                            pageNumber = 0;
                        }
                        if(mDocView!=null)
                        {
                            mDocView.setDisplayedViewIndex(pageNumber == 0 ? 0 : pageNumber -1 );
                            mDocView.setScale(1.0f);
                            mDocView.setNormalizedScroll(0.0f,0.0f);
                        }
					} else if (which == AlertDialog.BUTTON_NEGATIVE) {
					}
				}
			};
		mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_gotopage_ok), listener);
		mAlertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_gotopage_cancel), listener);
		mAlertDialog.setView(editTextLayout);
		mAlertDialog.show();
		input.requestFocus();
    }

    private void showSaveDialog() {
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    if (hasRepository()) {
                        saveInBackground(
                            new Callable<Void>() {
                                @Override
                                public Void call() {
                                    setTitle();
                                    return null;
                                }
                            },
                            new Callable<Void>() {
                                @Override
                                public Void call() {
                                    showInfo(getString(R.string.error_saveing));
                                    return null;
                                }
                            });
                    }
                }
                if (which == AlertDialog.BUTTON_NEGATIVE) {
                    showSaveAsActivity();
                }
            }
        };
        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(getString(R.string.save));
        alert.setMessage(getString(R.string.how_do_you_want_to_save));
        if (canSaveToCurrentUri(this)) {
            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.save), listener);
        }
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.saveas), listener);
        alert.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancel), listener);
        alert.show();
    }


    private void showOpenNewDocumentDialoge() {

		mAlertDialog = mAlertBuilder.create();

		final View editTextLayout = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null, false);
        final EditText input = editTextLayout.findViewById(R.id.dialog_text_input);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine();
		input.setBackgroundDrawable(null);
        TextDrawable textDrawable = new TextDrawable(".pdf", input.getTextSize(), input.getCurrentTextColor());
        input.setCompoundDrawablesWithIntrinsicBounds(null , null, textDrawable, null);
        input.setFocusable(true);
        input.setGravity(Gravity.END);
		mAlertDialog.setTitle(R.string.dialog_newdoc_title);
		DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which) {
					if (which == AlertDialog.BUTTON_POSITIVE) {
							// User clicked OK button
                        String filename = input.getText().toString();
                        try
                        {
                            if(filename != "")
                            {
                                filename+=".pdf";
                                File dir = getNotesDir(mAlertDialog.getContext());
                                File file = new File(dir, filename);

                                if(file != null && file.isFile() && file.length() > 0)
                                    showInfo(String.format(getString(R.string.file_alrady_exists), filename));
                                else
                                    openNewDocument(filename);
                            }
                        }
                        catch(java.io.IOException e){
                            AlertDialog alert = mAlertBuilder.create();
                            alert.setTitle(R.string.cannot_open_document);
                            alert.setMessage(getResources().getString(R.string.reason)+": "+e.toString());
                            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    finish();
                                                }
                                            });
                            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                    public void onDismiss(DialogInterface dialog) {
                                        finish();
                                    }
                                });
                            alert.show();	
                        }   
					}
					else if (which == AlertDialog.BUTTON_NEGATIVE) {
					}
				}
			};
		mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_newdoc_ok), listener);
		mAlertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_newdoc_cancel), listener);
		mAlertDialog.setView(editTextLayout);
		mAlertDialog.show();
		input.requestFocus();
        input.setSelection(0);
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
        if (getSupportActionBar() != null && !getSupportActionBar().isShowing()) {
            exitFullScreen();
            return;
        };
        if(dashboardIsShown() && mDocView != null) {
            hideDashboard();
            return;
        }
        switch (mActionBarMode) {
            case Annot:
                return;
            case Search:
                hideKeyboard();
                textOfLastSearch = "";
                if (searchToolbarController != null) {
                    searchToolbarController.clearQuery();
                }
				mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                mDocView.clearSearchResults();
                mDocView.resetupChildren();
                mActionBarMode = ActionBarMode.Main;
                invalidateOptionsMenu();
                return;
            case Selection:
                mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                MuPDFView pageView = (MuPDFView) mDocView.getSelectedView();
                if (pageView != null) pageView.deselectText();
                return;
        }
        
        if (core != null && hasUnsavedChanges()) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == AlertDialog.BUTTON_POSITIVE) {
                            if(canSaveToCurrentUri(OpenDroidPDFActivity.this))
                            {
                                saveInBackground(
                                    new Callable<Void>() {
                                        @Override
                                        public Void call() {
                                            mIgnoreSaveOnStopThisTime = true;//No need to save twice
                                            mIgnoreSaveOnDestroyThisTime = true;//No need to save twice
                                            finish();
                                            return null;
                                        }
                                    },
                                    new Callable<Void>() {
                                        @Override
                                        public Void call() {
                                            showInfo(getString(R.string.error_saveing));
                                            return null;
                                        }
                                    }
                                                 );                                
                            }
                            else
                                showSaveAsActivity();
                        }
                        if (which == AlertDialog.BUTTON_NEGATIVE) {
                            mIgnoreSaveOnStopThisTime = true;
                            mIgnoreSaveOnDestroyThisTime = true;
                            finish();
                        }
                        if (which == AlertDialog.BUTTON_NEUTRAL) {
                        }
                    }
                };
            AlertDialog alert = mAlertBuilder.create();
            alert.setTitle(getString(R.string.save_question));
            alert.setMessage(getString(R.string.document_has_changes_save_them));
            if(canSaveToCurrentUri(this))
                alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.save), listener);
            else
                alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.saveas), listener);      
            alert.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancel), listener);
            alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), listener);
            alert.show();
        } else {
            super.onBackPressed();
        }
    }

    
    @Override
    public void performPickFor(FilePicker picker) {
        mFilePicker = picker;
        Intent intent = new Intent(this, OpenDroidPDFFileChooser.class);
        startActivityForResult(intent, FILEPICK_REQUEST);
    }

    private void setTitle() {
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

	
	private void hideKeyboard()
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
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0;
            if (granted) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        granted = false;
                        break;
                    }
                }
            }

            if (granted) {
                openDocumentFromIntent(getIntent());
            } else {
                Toast.makeText(this, R.string.cannot_open_document, Toast.LENGTH_LONG).show();
            }
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
