package org.opendroidpdf.app.lifecycle;

import androidx.fragment.app.FragmentManager;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.annotation.PenSettingsController;
import org.opendroidpdf.app.dashboard.DashboardController;
import org.opendroidpdf.app.document.DocumentHostController;
import org.opendroidpdf.app.document.DocumentNavigationController;
import org.opendroidpdf.app.document.DocumentSetupController;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.helpers.ActivityResultRouter;
import org.opendroidpdf.app.helpers.IntentRouter;
import org.opendroidpdf.app.helpers.StoragePermissionController;
import org.opendroidpdf.app.hosts.ActivityResultHostAdapter;
import org.opendroidpdf.app.hosts.ExportHostAdapter;
import org.opendroidpdf.app.hosts.IntentHostAdapter;
import org.opendroidpdf.app.hosts.NavigationHostAdapter;
import org.opendroidpdf.app.hosts.ToolbarHostAdapter;
import org.opendroidpdf.app.hosts.ToolbarHostProvider;
import org.opendroidpdf.app.navigation.NavigationController;
import org.opendroidpdf.app.notes.NotesController;
import org.opendroidpdf.app.preferences.PenPreferences;
import org.opendroidpdf.app.toolbar.ToolbarStateCache;
import org.opendroidpdf.app.toolbar.ToolbarStateController;

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
        public DashboardController dashboardController;
        public DocumentHostController documentHostController;
        public StoragePermissionController storagePermissionController;
        public NavigationController navigationController;
        public ActivityResultRouter activityResultRouter;
        public DocumentNavigationController documentNavigationController;
        public DocumentSetupController documentSetupController;
    }

    public static Composition setup(OpenDroidPDFActivity activity) {
        Composition c = new Composition();
        c.appServices = AppServices.init(activity.getApplication());
        c.penPreferences = c.appServices.penPreferences();
        c.penSettingsController = new PenSettingsController(c.penPreferences, activity);

        c.exportController = new ExportController(new ExportHostAdapter(activity));
        c.notesController = new NotesController(new org.opendroidpdf.app.hosts.NotesHostAdapter(activity));
        c.intentRouter = new IntentRouter(new IntentHostAdapter(activity));

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
        c.documentSetupController = new DocumentSetupController(activity);

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

