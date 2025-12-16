package org.opendroidpdf.app.navigation;

import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFActivity;

/**
 * Slim host wrappers for dashboard/document container operations.
 */
public final class DashboardDelegate {
    private final NavigationController navigationController;
    private final OpenDroidPDFActivity activity;

    public DashboardDelegate(@Nullable NavigationController navigationController,
                             @Nullable OpenDroidPDFActivity activity) {
        this.navigationController = navigationController;
        this.activity = activity;
    }

    public boolean dashboardIsShown() {
        return navigationController != null && navigationController.dashboardIsShown();
    }

    public void showDashboardIfAvailable() {
        if (navigationController != null) navigationController.showDashboard();
    }

    public void showDashboard() {
        if (navigationController != null) navigationController.showDashboard();
    }

    public void hideDashboard() {
        if (navigationController != null) navigationController.hideDashboard();
    }

    public void attachDocViewToContainer(@Nullable ViewGroup container) {
        if (navigationController != null && activity != null) {
            navigationController.attachDocViewToContainer(container, activity.getDocView());
        }
    }

    public ViewGroup ensureDocumentContainer() {
        return navigationController != null ? navigationController.ensureDocumentContainer() : null;
    }
}
