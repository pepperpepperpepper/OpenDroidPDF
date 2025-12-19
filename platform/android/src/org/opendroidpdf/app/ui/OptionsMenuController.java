package org.opendroidpdf.app.ui;

import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.document.DocumentToolbarController;
import org.opendroidpdf.app.navigation.DashboardDelegate;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.toolbar.ToolbarMenuDelegate;
import org.opendroidpdf.app.toolbar.ToolbarStateController;

/**
 * Centralizes options-menu wiring to keep OpenDroidPDFActivity slimmer.
 */
public final class OptionsMenuController {
    public interface BoolSupplier { boolean get(); }

    private final OpenDroidPDFActivity activity;
    private final DashboardDelegate dashboardDelegate;
    private final ToolbarStateController toolbarStateController;
    private final DocumentToolbarController documentToolbarController;
    private final AnnotationToolbarController annotationToolbarController;
    private final SearchToolbarController searchToolbarController;
    private final ActionBarModeDelegate actionBarModeDelegate;
    private boolean preparingOptionsMenu = false;

    public OptionsMenuController(OpenDroidPDFActivity activity,
                                 DashboardDelegate dashboardDelegate,
                                 ToolbarStateController toolbarStateController,
                                 DocumentToolbarController documentToolbarController,
                                 AnnotationToolbarController annotationToolbarController,
                                 SearchToolbarController searchToolbarController,
                                 ActionBarModeDelegate actionBarModeDelegate) {
        this.activity = activity;
        this.dashboardDelegate = dashboardDelegate;
        this.toolbarStateController = toolbarStateController;
        this.documentToolbarController = documentToolbarController;
        this.annotationToolbarController = annotationToolbarController;
        this.searchToolbarController = searchToolbarController;
        this.actionBarModeDelegate = actionBarModeDelegate;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        ActionBarMode modeForMenu = actionBarModeDelegate.current();
        if (dashboardDelegate != null && dashboardDelegate.dashboardIsShown()) {
            // Dashboard is shown: use an empty menu, but do not mutate the canonical UI mode.
            modeForMenu = ActionBarMode.Empty;
        }
        return ToolbarMenuDelegate.onCreateOptionsMenu(
                activity,
                modeForMenu,
                toolbarStateController,
                documentToolbarController,
                annotationToolbarController,
                searchToolbarController,
                menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        return ToolbarMenuDelegate.onOptionsItemSelected(
                activity,
                item,
                documentToolbarController,
                annotationToolbarController,
                searchToolbarController);
    }

    public boolean onPrepareOptionsMenu(Menu menu, @Nullable BoolSupplier superCall) {
        preparingOptionsMenu = true;
        try {
            ToolbarMenuDelegate.onPrepareOptionsMenu(toolbarStateController, menu);
            return superCall != null ? superCall.get() : true;
        } finally {
            preparingOptionsMenu = false;
        }
    }

    public void invalidateOptionsMenuSafely() {
        if (preparingOptionsMenu) return;
        try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
    }

    public boolean isPreparingOptionsMenu() { return preparingOptionsMenu; }
}
