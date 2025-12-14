package org.opendroidpdf.app.navigation;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.DocumentHostFragment;
import org.opendroidpdf.app.dashboard.DashboardController;
import org.opendroidpdf.app.document.DocumentHostController;

/**
 * Small facade over dashboard/doc-host navigation so the activity just delegates.
 */
public final class NavigationController {
    private final DashboardController dashboardController;
    private final DocumentHostController documentHostController;

    public NavigationController(@NonNull DashboardController dashboardController,
                                @NonNull DocumentHostController documentHostController) {
        this.dashboardController = dashboardController;
        this.documentHostController = documentHostController;
    }

    public boolean dashboardIsShown() {
        return dashboardController.isDashboardShown();
    }

    public void showDashboard() {
        dashboardController.showDashboard();
    }

    public void hideDashboard() {
        dashboardController.hideDashboard();
    }

    public ViewGroup ensureDocumentContainer() {
        DocumentHostFragment host = documentHostController.ensureFragment();
        return host != null ? host.getDocumentContainer() : null;
    }

    public void attachDocViewToContainer(ViewGroup container, View docView) {
        if (container == null || docView == null) return;
        try {
            if (docView.getParent() instanceof ViewGroup) {
                ((ViewGroup) docView.getParent()).removeView(docView);
            }
        } catch (Throwable ignore) {}
        container.removeAllViews();
        container.addView(docView,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
