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
    @Nullable private final Runnable invalidateOptionsMenu;
    @Nullable private final Runnable onShowDashboard;
    @Nullable private final Runnable onDocViewShown;

    public DashboardDelegate(@Nullable NavigationController navigationController,
                             @Nullable Provider<MuPDFReaderView> docViewProvider,
                             @Nullable Runnable invalidateOptionsMenu,
                             @Nullable Runnable onShowDashboard,
                             @Nullable Runnable onDocViewShown) {
        this.navigationController = navigationController;
        this.docViewProvider = docViewProvider;
        this.invalidateOptionsMenu = invalidateOptionsMenu;
        this.onShowDashboard = onShowDashboard;
        this.onDocViewShown = onDocViewShown;
    }

    public boolean dashboardIsShown() {
        return navigationController != null && navigationController.dashboardIsShown();
    }

    public void showDashboardIfAvailable() {
        if (navigationController != null) navigationController.showDashboard();
        if (onShowDashboard != null) onShowDashboard.run();
        if (invalidateOptionsMenu != null) invalidateOptionsMenu.run();
    }

    public void showDashboard() {
        if (navigationController != null) navigationController.showDashboard();
        if (onShowDashboard != null) onShowDashboard.run();
        if (invalidateOptionsMenu != null) invalidateOptionsMenu.run();
    }

    public void hideDashboard() {
        if (navigationController == null) return;
        if (!navigationController.dashboardIsShown()) return;
        MuPDFReaderView docView = docViewProvider != null ? docViewProvider.get() : null;
        if (docView == null) {
            navigationController.hideDashboard();
            if (invalidateOptionsMenu != null) invalidateOptionsMenu.run();
            return;
        }
        ViewGroup container = navigationController.ensureDocumentContainer();
        navigationController.attachDocViewToContainer(container, docView);
        if (onDocViewShown != null) onDocViewShown.run();
        if (invalidateOptionsMenu != null) invalidateOptionsMenu.run();
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
