package org.opendroidpdf.app.navigation;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.document.DocumentViewportController;

/**
 * Small UI helpers for navigation gestures and link-back restoration.
 */
public final class NavigationUiHelper {
    private NavigationUiHelper() {}

    public static boolean applyLinkBack(@Nullable DocumentViewportController viewportController,
                                        int page,
                                        float scale,
                                        float normX,
                                        float normY) {
        if (viewportController == null) return false;
        if (page < 0) return false;
        viewportController.setViewport(page, scale, normX, normY);
        return true;
    }

    public static void tapTopLeft(AppCompatActivity host, MuPDFReaderView docView) {
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

    public static void tapBottomRight(AppCompatActivity host, MuPDFReaderView docView) {
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
