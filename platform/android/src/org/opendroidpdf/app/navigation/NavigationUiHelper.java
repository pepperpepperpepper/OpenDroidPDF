package org.opendroidpdf.app.navigation;

import androidx.appcompat.app.ActionBar;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;

/**
 * Small UI helpers for navigation gestures and link-back restoration.
 */
public final class NavigationUiHelper {
    private NavigationUiHelper() {}

    public static boolean applyLinkBack(OpenDroidPDFActivity host,
                                        int page,
                                        float scale,
                                        float normX,
                                        float normY) {
        if (host == null) return false;
        if (page < 0) return false;
        if (host.getViewportController() != null) {
            host.getViewportController().setViewport(page, scale, normX, normY);
        }
        return true;
    }

    public static void tapTopLeft(OpenDroidPDFActivity host, MuPDFReaderView docView) {
        if (host == null || docView == null) return;
        ActionBar bar = host.getSupportActionBar();
        boolean showing = bar != null && bar.isShowing();
        if (showing) {
            docView.smartMoveBackwards();
        } else {
            docView.setDisplayedViewIndex(docView.getSelectedItemPosition() - 1);
            docView.setScale(1.0f);
            docView.setNormalizedScroll(0.0f, 0.0f);
        }
    }

    public static void tapBottomRight(OpenDroidPDFActivity host, MuPDFReaderView docView) {
        if (host == null || docView == null) return;
        ActionBar bar = host.getSupportActionBar();
        boolean showing = bar != null && bar.isShowing();
        if (showing) {
            docView.smartMoveForwards();
        } else {
            docView.setDisplayedViewIndex(docView.getSelectedItemPosition() + 1);
            docView.setScale(1.0f);
            docView.setNormalizedScroll(0.0f, 0.0f);
        }
    }
}
