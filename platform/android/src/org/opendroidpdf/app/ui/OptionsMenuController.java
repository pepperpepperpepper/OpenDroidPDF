package org.opendroidpdf.app.ui;

import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.opendroidpdf.R;
import org.opendroidpdf.app.DashboardFragment;
import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.debug.DebugActionsController;
import org.opendroidpdf.app.document.DocumentToolbarController;
import org.opendroidpdf.app.navigation.DashboardDelegate;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.toolbar.ToolbarMenuDelegate;
import org.opendroidpdf.app.toolbar.ToolbarStateController;

/**
 * Centralizes options-menu wiring to keep the activity slimmer.
 */
public final class OptionsMenuController {
    public interface BoolSupplier { boolean get(); }

    private final AppCompatActivity activity;
    @Nullable private final DebugActionsController.Host debugHost;
    private final DashboardDelegate dashboardDelegate;
    @Nullable private final DashboardFragment.DashboardHost dashboardHost;
    private final ToolbarStateController toolbarStateController;
    private final DocumentToolbarController documentToolbarController;
    private final AnnotationToolbarController annotationToolbarController;
    private final SearchToolbarController searchToolbarController;
    private final ActionBarModeDelegate actionBarModeDelegate;
    private boolean preparingOptionsMenu = false;
    private boolean pendingInvalidation = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public OptionsMenuController(@NonNull AppCompatActivity activity,
                                 @Nullable DebugActionsController.Host debugHost,
                                 DashboardDelegate dashboardDelegate,
                                 @Nullable DashboardFragment.DashboardHost dashboardHost,
                                 ToolbarStateController toolbarStateController,
                                 DocumentToolbarController documentToolbarController,
                                 AnnotationToolbarController annotationToolbarController,
                                 SearchToolbarController searchToolbarController,
                                 ActionBarModeDelegate actionBarModeDelegate) {
        this.activity = activity;
        this.debugHost = debugHost;
        this.dashboardDelegate = dashboardDelegate;
        this.dashboardHost = dashboardHost;
        this.toolbarStateController = toolbarStateController;
        this.documentToolbarController = documentToolbarController;
        this.annotationToolbarController = annotationToolbarController;
        this.searchToolbarController = searchToolbarController;
        this.actionBarModeDelegate = actionBarModeDelegate;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        ActionBarMode modeForMenu = actionBarModeDelegate.current();
        if (dashboardDelegate != null && dashboardDelegate.dashboardIsShown()) {
            // Dashboard is shown: show only library-level actions (Open/New/Settings).
            menu.clear();
            activity.getMenuInflater().inflate(R.menu.dashboard_menu, menu);
            return true;
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
        if (dashboardDelegate != null && dashboardDelegate.dashboardIsShown()) {
            if (dashboardHost != null) {
                int id = item.getItemId();
                if (id == R.id.menu_open_document) {
                    dashboardHost.onOpenDocumentRequested();
                    return true;
                }
                if (id == R.id.menu_new_document) {
                    dashboardHost.onCreateNewDocumentRequested();
                    return true;
                }
                if (id == R.id.menu_settings) {
                    dashboardHost.onOpenSettingsRequested();
                    return true;
                }
            }
        }
        return ToolbarMenuDelegate.onOptionsItemSelected(
                debugHost,
                item,
                documentToolbarController,
                annotationToolbarController,
                searchToolbarController);
    }

    public boolean onPrepareOptionsMenu(Menu menu, @Nullable BoolSupplier superCall) {
        preparingOptionsMenu = true;
        try {
            updateToolbarNavigation();
            ToolbarMenuDelegate.onPrepareOptionsMenu(toolbarStateController, menu);
            if (actionBarModeDelegate.current() == ActionBarMode.Selection) {
                Toolbar toolbar = activity.findViewById(R.id.toolbar);
                if (toolbar != null) {
                    toolbar.post(() -> {
                        try {
                            annotationToolbarController.bindSelectionToolbarLongPressHandlers(toolbar);
                        } catch (Throwable ignore) {
                        }
                    });
                }
            }
            return superCall != null ? superCall.get() : true;
        } finally {
            preparingOptionsMenu = false;
            if (pendingInvalidation) {
                pendingInvalidation = false;
                mainHandler.post(() -> {
                    try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
                });
            }
        }
    }

    private void updateToolbarNavigation() {
        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar == null) return;

        boolean dashboardShown;
        try {
            dashboardShown = dashboardDelegate != null && dashboardDelegate.dashboardIsShown();
        } catch (Throwable ignore) {
            dashboardShown = false;
        }

        if (dashboardShown) {
            toolbar.setNavigationIcon(null);
            toolbar.setNavigationOnClickListener(null);
            return;
        }

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        toolbar.setNavigationOnClickListener(v -> {
            try {
                activity.getOnBackPressedDispatcher().onBackPressed();
            } catch (Throwable ignore) {
            }
        });
    }

    public void invalidateOptionsMenuSafely() {
        if (preparingOptionsMenu) {
            pendingInvalidation = true;
            return;
        }
        try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
    }

    public boolean isPreparingOptionsMenu() { return preparingOptionsMenu; }
}
