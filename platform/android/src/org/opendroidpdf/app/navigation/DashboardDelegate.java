package org.opendroidpdf.app.navigation;

import android.view.ViewGroup;

import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.services.Provider;

/**
 * Slim host wrappers for dashboard/document container operations.
 */
public final class DashboardDelegate {
    private final NavigationController navigationController;
    private final Provider<MuPDFReaderView> docViewProvider;

    public DashboardDelegate(@Nullable NavigationController navigationController,
                             @Nullable Provider<MuPDFReaderView> docViewProvider) {
        this.navigationController = navigationController;
        this.docViewProvider = docViewProvider;
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
        if (navigationController != null) {
            MuPDFReaderView docView = docViewProvider != null ? docViewProvider.get() : null;
            navigationController.attachDocViewToContainer(container, docView);
        }
    }

    public ViewGroup ensureDocumentContainer() {
        return navigationController != null ? navigationController.ensureDocumentContainer() : null;
    }
}
