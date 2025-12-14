package org.opendroidpdf.app.navigation;

import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;

/**
 * Slim host wrappers for dashboard/document container operations.
 */
public final class DashboardDelegate {
    private final NavigationController navigationController;
    private final MuPDFReaderView docView;

    public DashboardDelegate(@Nullable NavigationController navigationController,
                             @Nullable MuPDFReaderView docView) {
        this.navigationController = navigationController;
        this.docView = docView;
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
        if (navigationController != null) navigationController.attachDocViewToContainer(container, docView);
    }

    public ViewGroup ensureDocumentContainer() {
        return navigationController != null ? navigationController.ensureDocumentContainer() : null;
    }
}
