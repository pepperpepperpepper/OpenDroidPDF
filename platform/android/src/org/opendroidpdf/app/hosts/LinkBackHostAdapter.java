package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.navigation.LinkBackHelper;
import org.opendroidpdf.app.navigation.NavigationUiHelper;

/**
 * Handles link-back navigation using the saved link target in the Activity,
 * so the Activity body stays slim.
 */
public final class LinkBackHostAdapter {
    private final OpenDroidPDFActivity activity;
    private final LinkBackHelper helper;

    public LinkBackHostAdapter(@NonNull OpenDroidPDFActivity activity, @NonNull LinkBackHelper helper) {
        this.activity = activity;
        this.helper = helper;
    }

    public void requestLinkBackNavigation() {
        org.opendroidpdf.app.navigation.LinkBackState state = helper.state();
        if (state == null) return;
        boolean applied = NavigationUiHelper.applyLinkBack(
                activity.getViewportController(),
                state.page(),
                state.scale(),
                state.normX(),
                state.normY());
        if (applied) {
            helper.clear();
        }
        activity.invalidateOptionsMenuSafely();
    }
}
