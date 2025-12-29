package org.opendroidpdf.app.lifecycle;

import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.FilePickerCoordinator;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.annotation.PenSettingsController;
import org.opendroidpdf.app.annotation.TextAnnotationStyleController;
import org.opendroidpdf.app.dashboard.DashboardController;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.document.DocumentHostController;
import org.opendroidpdf.app.document.DocumentNavigationController;
import org.opendroidpdf.app.document.DocumentSetupController;
import org.opendroidpdf.app.document.DocumentToolbarController;
import org.opendroidpdf.app.document.DocumentViewDelegate;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.helpers.ActivityResultRouter;
import org.opendroidpdf.app.helpers.IntentRouter;
import org.opendroidpdf.app.helpers.RequestCodes;
import org.opendroidpdf.app.helpers.StoragePermissionController;
import org.opendroidpdf.app.hosts.ActivityResultHostAdapter;
import org.opendroidpdf.app.hosts.AnnotationToolbarHostAdapter;
import org.opendroidpdf.app.hosts.DrawingServiceAnnotationModeStore;
import org.opendroidpdf.app.hosts.DashboardHostAdapter;
import org.opendroidpdf.app.hosts.DocumentSetupHostAdapter;
import org.opendroidpdf.app.hosts.DocumentViewHostAdapter;
import org.opendroidpdf.app.hosts.DocumentViewDelegateHostAdapter;
import org.opendroidpdf.app.hosts.ExportHostAdapter;
import org.opendroidpdf.app.hosts.IntentHostAdapter;
import org.opendroidpdf.app.hosts.IntentResumeHostAdapter;
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
import org.opendroidpdf.app.hosts.BackPressHostAdapter;
import org.opendroidpdf.app.navigation.NavigationDelegate;
import org.opendroidpdf.app.notes.NotesController;
import org.opendroidpdf.app.notes.NotesDelegate;
import org.opendroidpdf.app.preferences.PenPreferencesServiceImpl;
import org.opendroidpdf.app.preferences.SharedPreferencesPenPrefsStore;
import org.opendroidpdf.app.preferences.PreferencesCoordinator;
import org.opendroidpdf.app.preferences.SharedPreferencesAppPrefsStore;
import org.opendroidpdf.app.preferences.SharedPreferencesEditorPrefsStore;
import org.opendroidpdf.app.preferences.SharedPreferencesViewerPrefsStore;
import org.opendroidpdf.app.reflow.ReflowPrefsStore;
import org.opendroidpdf.app.reflow.SharedPreferencesReflowPrefsStore;
import org.opendroidpdf.app.services.DrawingService;
import org.opendroidpdf.app.services.DrawingServiceImpl;
import org.opendroidpdf.app.services.PenPreferencesService;
import org.opendroidpdf.app.services.TextStylePreferencesService;
import org.opendroidpdf.app.services.SearchService;
import org.opendroidpdf.app.services.SearchServiceImpl;
import org.opendroidpdf.app.search.SearchToolbarController;
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
 * Centralizes controller/adapter wiring for the main activity onCreate.
 */
public final class ActivityComposition {
    private ActivityComposition() {}

    public static final class Composition {
        public AppServices appServices;
        public PenPreferencesService penPreferences;
        public TextStylePreferencesService textStylePreferences;
        public PreferencesCoordinator preferencesCoordinator;
        public ReflowPrefsStore reflowPrefsStore;
        public DrawingService drawingService;
        public PenSettingsController penSettingsController;
        public TextAnnotationStyleController textAnnotationStyleController;
        public SearchService searchService;
        public ExportController exportController;
        public NotesController notesController;
        public IntentRouter intentRouter;
        public ToolbarStateController toolbarStateController;
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
        public DocumentViewHostAdapter documentViewHostAdapter;
    }

    public static Composition setup(AppCompatActivity activity) {
        return org.opendroidpdf.app.hosts.ActivityCompositionHostAdapter.setup(activity);
    }
}
