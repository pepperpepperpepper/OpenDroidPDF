package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.FilePickerCoordinator;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.annotation.PenSettingsController;
import org.opendroidpdf.app.annotation.TextAnnotationStyleController;
import org.opendroidpdf.app.dashboard.DashboardController;
import org.opendroidpdf.app.debug.DebugDelegate;
import org.opendroidpdf.app.document.DocumentHostController;
import org.opendroidpdf.app.document.DocumentNavigationController;
import org.opendroidpdf.app.document.DocumentSetupController;
import org.opendroidpdf.app.document.DocumentToolbarController;
import org.opendroidpdf.app.document.DocumentViewDelegate;
import org.opendroidpdf.app.document.DocumentViewportController;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.document.InkCommitHostAdapter;
import org.opendroidpdf.app.document.SaveUiDelegate;
import org.opendroidpdf.app.helpers.ActivityResultRouter;
import org.opendroidpdf.app.helpers.IntentResumeDelegate;
import org.opendroidpdf.app.helpers.IntentRouter;
import org.opendroidpdf.app.helpers.RequestCodes;
import org.opendroidpdf.app.helpers.StoragePermissionController;
import org.opendroidpdf.app.lifecycle.ActivityComposition;
import org.opendroidpdf.app.lifecycle.SaveFlagController;
import org.opendroidpdf.app.navigation.BackPressController;
import org.opendroidpdf.app.navigation.DashboardDelegate;
import org.opendroidpdf.app.navigation.LinkBackDelegate;
import org.opendroidpdf.app.navigation.LinkBackHelper;
import org.opendroidpdf.app.navigation.NavigationController;
import org.opendroidpdf.app.navigation.NavigationDelegate;
import org.opendroidpdf.app.notes.NotesController;
import org.opendroidpdf.app.notes.NotesDelegate;
import org.opendroidpdf.app.preferences.PreferencesCoordinator;
import org.opendroidpdf.app.preferences.SharedPreferencesAppPrefsStore;
import org.opendroidpdf.app.preferences.SharedPreferencesEditorPrefsStore;
import org.opendroidpdf.app.preferences.SharedPreferencesViewerPrefsStore;
import org.opendroidpdf.app.reflow.SharedPreferencesReflowPrefsStore;
import org.opendroidpdf.app.reflow.ReflowPrefsStore;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.services.DrawingService;
import org.opendroidpdf.app.services.DrawingServiceImpl;
import org.opendroidpdf.app.services.PenPreferencesService;
import org.opendroidpdf.app.services.SearchService;
import org.opendroidpdf.app.services.SearchServiceImpl;
import org.opendroidpdf.app.toolbar.ToolbarStateCache;
import org.opendroidpdf.app.toolbar.ToolbarStateController;
import org.opendroidpdf.app.ui.KeyboardHostAdapter;
import org.opendroidpdf.app.ui.OptionsMenuController;
import org.opendroidpdf.app.ui.TitleHostAdapter;
import org.opendroidpdf.app.ui.UiStateDelegate;

/**
 * Host-side builder for {@link ActivityComposition} that keeps concrete activity
 * dependencies out of the lifecycle package.
 */
public final class ActivityCompositionHostAdapter {
    private ActivityCompositionHostAdapter() {}

    @NonNull
    public static ActivityComposition.Composition setup(@NonNull AppCompatActivity appCompatActivity) {
        if (!(appCompatActivity instanceof OpenDroidPDFActivity)) {
            throw new IllegalArgumentException("Expected OpenDroidPDFActivity, got " + appCompatActivity);
        }
        final OpenDroidPDFActivity activity = (OpenDroidPDFActivity) appCompatActivity;

        ActivityComposition.Composition c = new ActivityComposition.Composition();

        // These are used by multiple controllers/host adapters; initialize first.
        c.saveFlagController = new SaveFlagController();
        org.opendroidpdf.app.hosts.DocumentAccessHostAdapter documentAccessHostAdapter =
                new org.opendroidpdf.app.hosts.DocumentAccessHostAdapter(activity);
        c.documentViewHostAdapter = new DocumentViewHostAdapter(activity::getDocView, activity::getCore);
        c.saveUiDelegate = new SaveUiDelegate(
                new org.opendroidpdf.app.hosts.SaveUiHostAdapter(
                        activity,
                        c.documentViewHostAdapter,
                        documentAccessHostAdapter));
        c.linkBackDelegate = new LinkBackDelegate();
        c.linkBackHelper = new LinkBackHelper(c.linkBackDelegate);

        c.appServices = AppServices.init(activity.getApplication());
        c.penPreferences = c.appServices.penPreferences();
        c.textStylePreferences = c.appServices.textStylePreferences();
        c.preferencesCoordinator = new PreferencesCoordinator(
                new PreferencesCoordinator.Host() {
                    @Override public android.app.Activity activity() { return activity; }
                    @Override public void setSaveFlags(boolean saveOnStop, boolean saveOnDestroy, int numberRecentFiles) {
                        activity.setSaveFlags(saveOnStop, saveOnDestroy, numberRecentFiles);
                    }
                    @Override public org.opendroidpdf.MuPDFReaderView docViewOrNull() { return activity.getDocView(); }
                    @Override public org.opendroidpdf.OpenDroidPDFCore coreOrNull() { return activity.getCore(); }
                },
                new SharedPreferencesAppPrefsStore(activity),
                new SharedPreferencesViewerPrefsStore(activity),
                new SharedPreferencesEditorPrefsStore(activity),
                c.penPreferences);
        c.reflowPrefsStore = new SharedPreferencesReflowPrefsStore(activity);
        c.searchService = new SearchServiceImpl(activity);
        c.drawingService = new DrawingServiceImpl(activity::getDocView);
        c.penSettingsController = new PenSettingsController(c.penPreferences, c.drawingService, activity);
        c.textAnnotationStyleController = new TextAnnotationStyleController(
                c.textStylePreferences,
                new TextAnnotationStyleHostAdapter(activity, c.documentViewHostAdapter));

        c.exportController = new ExportController(new ExportHostAdapter(
                activity,
                c.documentViewHostAdapter,
                c.saveFlagController,
                c.saveUiDelegate));
        c.notesController = new NotesController(new org.opendroidpdf.app.hosts.NotesHostAdapter(activity));
        c.intentRouter = new IntentRouter(new IntentHostAdapter(activity, c.exportController));
        FilePickerCoordinator filePickerCoordinator = new FilePickerCoordinator();
        FilePickerHostAdapter filePickerHost = new FilePickerHostAdapter(activity, filePickerCoordinator);
        c.filePickerHostAdapter = filePickerHost;

        ToolbarHostAdapter toolbarHost = new ToolbarHostAdapter(new ToolbarHostProvider(activity, c.documentViewHostAdapter, c.drawingService));
        c.toolbarStateController = new ToolbarStateController(toolbarHost);
        ToolbarStateCache.get().setListener(() -> c.toolbarStateController.notifyStateChanged());

        androidx.fragment.app.FragmentManager fm = activity.getSupportFragmentManager();
        c.dashboardController = new DashboardController(fm, R.id.content_fragment_container);
        c.documentHostController = new DocumentHostController(fm, R.id.content_fragment_container);
        c.storagePermissionController = new StoragePermissionController();
        c.navigationController = new NavigationController(c.dashboardController, c.documentHostController);

        NavigationHostAdapter navigationHostAdapter = new NavigationHostAdapter(activity);
        c.documentNavigationController = new DocumentNavigationController(
                navigationHostAdapter,
                RequestCodes.EDIT,
                RequestCodes.SAVE_AS);
        c.documentSetupController = new DocumentSetupController(
                new DocumentSetupHostAdapter(activity, c.documentViewHostAdapter, filePickerHost, documentAccessHostAdapter),
                c.searchService,
                c.preferencesCoordinator,
                c.reflowPrefsStore);
        c.navigationDelegate = new NavigationDelegate(c.documentNavigationController, c.saveFlagController);
        c.intentResumeDelegate = new IntentResumeDelegate(new IntentResumeHostAdapter(activity), c.intentRouter);

        c.viewportController = new DocumentViewportController(new ViewportHostAdapter(activity, c.documentViewHostAdapter));
        c.documentViewDelegate = new DocumentViewDelegate(new DocumentViewDelegateHostAdapter(activity), c.documentViewHostAdapter, c.viewportController, c.preferencesCoordinator);
        c.notesDelegate = new NotesDelegate(new org.opendroidpdf.app.hosts.NotesDelegateHostAdapter(activity));
        c.uiStateDelegate = new UiStateDelegate(activity, activity::currentDocumentState, activity::getDocView);
        c.keyboardHostAdapter = new KeyboardHostAdapter(activity);
        c.titleHostAdapter = new TitleHostAdapter(c.uiStateDelegate);
        c.dashboardDelegate = new DashboardDelegate(c.navigationController, activity::getDocView);
        c.dashboardHostAdapter = new DashboardHostAdapter(activity, c.documentNavigationController);
        c.passwordHostAdapter = new PasswordHostAdapter(activity);
        c.tempUriPermissionHostAdapter = new TempUriPermissionHostAdapter(new org.opendroidpdf.app.util.TempUriPermissionDelegate());

        c.backPressController = new BackPressController(new BackPressHostAdapter(activity, c.keyboardHostAdapter));

        DrawingServiceAnnotationModeStore annotationModeStore = new DrawingServiceAnnotationModeStore(c.drawingService);
        activity.setAnnotationModeStore(annotationModeStore);
        activity.getActionBarModeDelegate().attachAnnotationModeStore(annotationModeStore);
        c.annotationToolbarController = new AnnotationToolbarController(
                new AnnotationToolbarHostAdapter(activity, c.documentViewHostAdapter, c.drawingService, c.exportController, c.textAnnotationStyleController),
                annotationModeStore);
        SearchToolbarHostAdapter searchHost = new SearchToolbarHostAdapter(
                activity,
                activity.getComponentName(),
                c.documentViewDelegate,
                c.keyboardHostAdapter,
                null, // options menu controller set after construction
                c.searchService);
        c.searchToolbarController = new SearchToolbarController(searchHost);
        c.documentToolbarController = new DocumentToolbarController(
                new org.opendroidpdf.app.hosts.DocumentToolbarHostAdapter(
                        activity,
                        c.documentViewHostAdapter,
                        c.exportController,
                        c.linkBackHelper));
        c.optionsMenuController = new OptionsMenuController(
                activity,
                new org.opendroidpdf.app.hosts.DebugActionsHostAdapter(activity),
                c.dashboardDelegate,
                c.toolbarStateController,
                c.documentToolbarController,
                c.annotationToolbarController,
                c.searchToolbarController,
                activity.getActionBarModeDelegate());
        searchHost.setOptionsMenuController(c.optionsMenuController);
        c.debugDelegate = new DebugDelegate();
        c.inkCommitHostAdapter = new InkCommitHostAdapter(activity, c.drawingService);

        c.activityResultRouter = new ActivityResultRouter(
                new ActivityResultHostAdapter(
                        activity,
                        c.documentNavigationController,
                        filePickerCoordinator,
                        c.exportController));

        return c;
    }
}
