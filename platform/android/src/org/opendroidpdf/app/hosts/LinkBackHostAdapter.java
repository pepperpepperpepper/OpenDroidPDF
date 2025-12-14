package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.navigation.NavigationUiHelper;

/**
 * Handles link-back navigation using the saved link target in the Activity,
 * so the Activity body stays slim.
 */
public final class LinkBackHostAdapter {
    private final OpenDroidPDFActivity activity;

    public LinkBackHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    public void requestLinkBackNavigation() {
        org.opendroidpdf.app.navigation.LinkBackState state = activity.getLinkBackState();
        boolean applied = NavigationUiHelper.applyLinkBack(
                activity,
                state.page(),
                state.scale(),
                state.normX(),
                state.normY());
        if (applied) {
            activity.clearLinkBackTarget();
        }
        activity.invalidateOptionsMenuSafely();
    }
}
