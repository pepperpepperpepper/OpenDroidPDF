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
        boolean applied = NavigationUiHelper.applyLinkBack(
                activity,
                activity.getLinkBackPage(),
                activity.getLinkBackScale(),
                activity.getLinkBackX(),
                activity.getLinkBackY());
        if (applied) {
            activity.clearLinkBackTarget();
        }
        activity.invalidateOptionsMenuSafely();
    }
}

