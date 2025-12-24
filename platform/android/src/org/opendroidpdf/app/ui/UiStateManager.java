package org.opendroidpdf.app.ui;

import android.os.Parcelable;

import org.opendroidpdf.app.lifecycle.ActivityComposition;
import org.opendroidpdf.app.navigation.LinkBackDelegate;

/**
 * Applies/restores transient UI state so the activity can stay slimmer.
 */
public class UiStateManager {
    private final ActivityComposition.Composition comp;

    public UiStateManager(ActivityComposition.Composition comp) {
        this.comp = comp;
    }

    public void applySavedUiState(int pageBefore,
                                  float normScale,
                                  float normX,
                                  float normY,
                                  Parcelable docViewState,
                                  String latestSearch) {
        LinkBackDelegate linkBackDelegate = comp != null ? comp.linkBackDelegate : null;
        if (linkBackDelegate != null) linkBackDelegate.remember(pageBefore, normScale, normX, normY);
        if (comp != null && comp.documentViewDelegate != null) comp.documentViewDelegate.rememberDocViewState(docViewState);
        if (latestSearch != null && comp != null && comp.searchService != null) {
            comp.searchService.session().setLatestQuery(latestSearch);
        }
        if (comp != null && comp.optionsMenuController != null) {
            comp.optionsMenuController.invalidateOptionsMenuSafely();
        }
    }

    public boolean isPreparingOptionsMenu() {
        return comp != null && comp.optionsMenuController != null && comp.optionsMenuController.isPreparingOptionsMenu();
    }
}
