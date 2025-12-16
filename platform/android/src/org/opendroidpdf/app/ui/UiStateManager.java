package org.opendroidpdf.app.ui;

import android.os.Parcelable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.lifecycle.ActivityComposition;
import org.opendroidpdf.app.navigation.LinkBackDelegate;

/**
 * Applies/restores transient UI state so the activity can stay slimmer.
 */
public class UiStateManager {
    private final OpenDroidPDFActivity activity;
    private final ActivityComposition.Composition comp;

    public UiStateManager(OpenDroidPDFActivity activity, ActivityComposition.Composition comp) {
        this.activity = activity;
        this.comp = comp;
    }

    public void applySavedUiState(ActionBarMode mode,
                                  int pageBefore,
                                  float normScale,
                                  float normX,
                                  float normY,
                                  Parcelable docViewState,
                                  String latestSearch,
                                  ActionBarModeDelegate actionBarModeDelegate) {
        actionBarModeDelegate.set((mode != null) ? mode : ActionBarMode.Main);
        LinkBackDelegate linkBackDelegate = comp != null ? comp.linkBackDelegate : null;
        if (linkBackDelegate != null) linkBackDelegate.remember(pageBefore, normScale, normX, normY);
        if (comp != null && comp.documentViewDelegate != null) comp.documentViewDelegate.rememberDocViewState(docViewState);
        if (latestSearch != null && comp != null && comp.searchService != null) {
            comp.searchService.session().setLatestQuery(latestSearch);
        }
        activity.invalidateOptionsMenuSafely();
    }

    public boolean isPreparingOptionsMenu() {
        return comp != null && comp.optionsMenuController != null && comp.optionsMenuController.isPreparingOptionsMenu();
    }
}
