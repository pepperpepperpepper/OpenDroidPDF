package org.opendroidpdf.app.lifecycle;

import androidx.fragment.app.FragmentManager;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.annotation.PenSettingsController;
import org.opendroidpdf.app.dashboard.DashboardController;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.document.DocumentHostController;
import org.opendroidpdf.app.document.DocumentNavigationController;
import org.opendroidpdf.app.document.DocumentSetupController;
import org.opendroidpdf.app.document.DocumentSetupHostAdapter;
import org.opendroidpdf.app.document.DocumentToolbarController;
import org.opendroidpdf.app.document.DocumentViewDelegate;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.helpers.ActivityResultRouter;
import org.opendroidpdf.app.helpers.IntentRouter;
import org.opendroidpdf.app.helpers.StoragePermissionController;
import org.opendroidpdf.app.hosts.ActivityResultHostAdapter;
import org.opendroidpdf.app.hosts.AnnotationToolbarHostAdapter;
import org.opendroidpdf.app.hosts.DashboardHostAdapter;
import org.opendroidpdf.app.hosts.ExportHostAdapter;
import org.opendroidpdf.app.hosts.IntentHostAdapter;
import org.opendroidpdf.app.hosts.NavigationHostAdapter;
import org.opendroidpdf.app.hosts.PasswordHostAdapter;
import org.opendroidpdf.app.hosts.SearchToolbarHostAdapter;
import org.opendroidpdf.app.hosts.TempUriPermissionHostAdapter;
import org.opendroidpdf.app.hosts.ToolbarHostAdapter;
import org.opendroidpdf.app.hosts.ToolbarHostProvider;
import org.opendroidpdf.app.hosts.FilePickerHostAdapter;
import org.opendroidpdf.app.hosts.AlertHostAdapter;
import org.opendroidpdf.app.navigation.DashboardDelegate;
import org.opendroidpdf.app.navigation.NavigationController;
import org.opendroidpdf.app.navigation.LinkBackDelegate;
import org.opendroidpdf.app.navigation.LinkBackHelper;
import org.opendroidpdf.app.navigation.BackPressController;
import org.opendroidpdf.app.navigation.BackPressHostAdapter;
import org.opendroidpdf.app.navigation.NavigationDelegate;
import org.opendroidpdf.app.notes.NotesController;
import org.opendroidpdf.app.notes.NotesDelegate;
import org.opendroidpdf.app.preferences.PenPreferences;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.search.SearchStateDelegate;
import org.opendroidpdf.app.toolbar.ToolbarStateCache;
import org.opendroidpdf.app.toolbar.ToolbarStateController;
import org.opendroidpdf.app.ui.KeyboardHostAdapter;
import org.opendroidpdf.app.ui.TitleHostAdapter;
import org.opendroidpdf.app.ui.UiStateDelegate;
import org.opendroidpdf.app.document.SaveUiDelegate;
import org.opendroidpdf.app.document.DocumentViewportController;
import org.opendroidpdf.app.hosts.ViewportHostAdapter;
import org.opendroidpdf.app.lifecycle.SaveFlagController;
import org.opendroidpdf.app.ui.OptionsMenuController;
import org.opendroidpdf.app.debug.DebugDelegate;
import org.opendroidpdf.app.document.InkCommitHostAdapter;
import org.opendroidpdf.app.helpers.IntentResumeDelegate;

/**
 * Centralizes controller/adapter wiring for OpenDroidPDFActivity.onCreate.
 */
public final class ActivityComposition {
    private ActivityComposition() {}

    public static final class Composition {
        public AppServices appServices;
        public PenPreferences penPreferences;
        public PenSettingsController penSettingsController;
        public ExportController exportController;
        public NotesController notesController;
        public IntentRouter intentRouter;
        public ToolbarStateController toolbarStateController;
        public SearchStateDelegate searchStateDelegate;
        public DashboardController dashboardController;
        public DocumentHostController documentHostController;
        public StoragePermissionController storagePermissionController;
        public NavigationController navigationController;
        public ActivityResultRouter activityResultRouter;
        public DocumentNavigationController documentNavigationController;
        public DocumentSetupController documentSetupController;
        public AnnotationToolbarController annotationToolbarController;
        public SearchToolbarController searchToolbarController;
        public DocumentToolbarController documentToolbarController;
        public SaveUiDelegate saveUiDelegate;
        public DocumentViewDelegate documentViewDelegate;
        public NotesDelegate notesDelegate;
        public UiStateDelegate uiStateDelegate;
        public KeyboardHostAdapter keyboardHostAdapter;
        public TitleHostAdapter titleHostAdapter;
        public DashboardDelegate dashboardDelegate;
        public LinkBackDelegate linkBackDelegate;
        public LinkBackHelper linkBackHelper;
        public DocumentViewportController viewportController;
        public SaveFlagController saveFlagController;
        public DashboardHostAdapter dashboardHostAdapter;
        public PasswordHostAdapter passwordHostAdapter;
        public TempUriPermissionHostAdapter tempUriPermissionHostAdapter;
        public OptionsMenuController optionsMenuController;
        public DebugDelegate debugDelegate;
        public InkCommitHostAdapter inkCommitHostAdapter;
        public BackPressController backPressController;
        public NavigationDelegate navigationDelegate;
        public IntentResumeDelegate intentResumeDelegate;
        public org.opendroidpdf.app.hosts.FilePickerHostAdapter filePickerHostAdapter;
    }

    public static Composition setup(OpenDroidPDFActivity activity) {
        Composition c = new Composition();
        c.appServices = AppServices.init(activity.getApplication());
        c.penPreferences = c.appServices.penPreferences();
        c.penSettingsController = new PenSettingsController(c.penPreferences, activity);
        c.searchStateDelegate = new SearchStateDelegate();

        c.exportController = new ExportController(new ExportHostAdapter(
                activity,
                c.saveFlagController,
                c.saveUiDelegate));
        c.notesController = new NotesController(new org.opendroidpdf.app.hosts.NotesHostAdapter(activity));
        c.intentRouter = new IntentRouter(new IntentHostAdapter(activity));
        FilePickerHostAdapter filePickerHost = new FilePickerHostAdapter(activity);
        c.filePickerHostAdapter = filePickerHost;

        ToolbarHostAdapter toolbarHost = new ToolbarHostAdapter(new ToolbarHostProvider(activity));
        c.toolbarStateController = new ToolbarStateController(toolbarHost);
        ToolbarStateCache.get().setListener(() -> c.toolbarStateController.notifyStateChanged());

        FragmentManager fm = activity.getSupportFragmentManager();
        c.dashboardController = new DashboardController(fm, R.id.content_fragment_container);
        c.documentHostController = new DocumentHostController(fm, R.id.content_fragment_container);
        c.storagePermissionController = new StoragePermissionController();
        c.navigationController = new NavigationController(c.dashboardController, c.documentHostController);

        c.documentNavigationController = new DocumentNavigationController(
                activity,
                new NavigationHostAdapter(activity),
                activity.getEditRequestCode(),
                activity.getSaveAsRequestCode());
        c.documentSetupController = new DocumentSetupController(new DocumentSetupHostAdapter(activity, filePickerHost));
        c.navigationDelegate = new NavigationDelegate(activity, c.documentNavigationController, c.saveFlagController);
        c.intentResumeDelegate = new IntentResumeDelegate(activity, c.intentRouter);

        c.viewportController = new DocumentViewportController(new ViewportHostAdapter(activity));
        c.documentViewDelegate = new DocumentViewDelegate(activity, c.viewportController);
        c.notesDelegate = new NotesDelegate(activity);
        c.uiStateDelegate = new UiStateDelegate(activity);
        c.keyboardHostAdapter = new KeyboardHostAdapter(activity);
        c.titleHostAdapter = new TitleHostAdapter(activity);
        c.dashboardDelegate = new DashboardDelegate(c.navigationController, activity.getDocView());
        c.linkBackDelegate = new LinkBackDelegate();
        c.linkBackHelper = new LinkBackHelper(c.linkBackDelegate);
        c.saveFlagController = new SaveFlagController();
        c.dashboardHostAdapter = new DashboardHostAdapter(activity, c.documentNavigationController);
        c.passwordHostAdapter = new PasswordHostAdapter(activity);
        c.tempUriPermissionHostAdapter = new TempUriPermissionHostAdapter(new org.opendroidpdf.app.util.TempUriPermissionDelegate());

        c.backPressController = new BackPressController(new BackPressHostAdapter(activity, c.keyboardHostAdapter));

        c.annotationToolbarController = new AnnotationToolbarController(new AnnotationToolbarHostAdapter(activity));
        SearchToolbarHostAdapter searchHost = new SearchToolbarHostAdapter(
                activity,
                activity.getComponentName(),
                c.documentViewDelegate,
                c.searchStateDelegate,
                c.keyboardHostAdapter,
                null, // options menu controller set after construction
                activity.getActionBarModeDelegate(),
                c.documentSetupController);
        c.searchToolbarController = new SearchToolbarController(searchHost);
        c.documentToolbarController = new DocumentToolbarController(new org.opendroidpdf.app.hosts.DocumentToolbarHostAdapter(activity));
        c.optionsMenuController = new OptionsMenuController(
                activity,
                c.dashboardDelegate,
                c.toolbarStateController,
                c.documentToolbarController,
                c.annotationToolbarController,
                c.searchToolbarController,
                activity.getActionBarModeDelegate());
        searchHost.setOptionsMenuController(c.optionsMenuController);
        c.debugDelegate = new DebugDelegate();
        c.saveUiDelegate = new SaveUiDelegate(activity);
        c.inkCommitHostAdapter = new InkCommitHostAdapter(activity);
        c.backPressController = new BackPressController(new BackPressHostAdapter(activity, c.keyboardHostAdapter));

        c.activityResultRouter = new ActivityResultRouter(
                new ActivityResultHostAdapter(
                        activity,
                        c.documentNavigationController,
                        activity.getEditRequestCode(),
                        activity.getOutlineRequestCode(),
                        activity.getPrintRequestCode(),
                        activity.getSaveAsRequestCode(),
                        activity.getManageStorageRequestCode()));

        return c;
    }
}
