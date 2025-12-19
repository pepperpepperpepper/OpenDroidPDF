package org.opendroidpdf.app.navigation;

import org.opendroidpdf.app.ui.ActionBarMode;

/**
 * Centralizes Activity back-press behavior so OpenDroidPDFActivity shrinks.
 */
public final class BackPressController {

    public interface Host {
        boolean isActionBarHidden();
        void exitFullScreen();
        boolean dashboardIsShown();
        void hideDashboard();
        ActionBarMode getMode();
        void hideKeyboard();
        void clearSearchQuery();
        void clearSearchResults();
        void resetupChildren();
        void setViewingMode();
        void deselectTextOnCurrentPage();
        boolean hasUnsavedChanges();
        void promptToSaveForExit();
        void finish();
    }

    private final Host host;

    public BackPressController(Host host) {
        this.host = host;
    }

    public boolean onBackPressed() {
        if (host.isActionBarHidden()) { host.exitFullScreen(); return true; }
        if (host.dashboardIsShown()) { host.hideDashboard(); return true; }

        switch (host.getMode()) {
            case Annot:
                return true;
            case Search:
                host.hideKeyboard();
                host.clearSearchQuery();
                host.setViewingMode();
                host.clearSearchResults();
                host.resetupChildren();
                return true;
            case Selection:
                host.setViewingMode();
                host.deselectTextOnCurrentPage();
                return true;
        }

        if (!host.hasUnsavedChanges()) {
            return false; // allow system default
        }
        host.promptToSaveForExit();
        return true;
    }
}
