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
        if (latestSearch != null && comp != null && comp.searchStateDelegate != null) comp.searchStateDelegate.setLatestSearchQuery(latestSearch);
        activity.invalidateOptionsMenuSafely();
    }

    public void setSaveFlags(boolean saveOnStop, boolean saveOnDestroy, int numberRecentFiles) {
        if (comp != null && comp.saveFlagController != null) comp.saveFlagController.setSaveFlags(saveOnStop, saveOnDestroy, numberRecentFiles);
    }

    public boolean shouldSaveOnStop() { return comp != null && comp.saveFlagController != null && comp.saveFlagController.shouldSaveOnStop(); }
    public boolean shouldSaveOnDestroy() { return comp != null && comp.saveFlagController != null && comp.saveFlagController.shouldSaveOnDestroy(); }
    public boolean shouldIgnoreSaveOnStopOnce() { return comp != null && comp.saveFlagController != null && comp.saveFlagController.shouldIgnoreSaveOnStopOnce(); }
    public boolean shouldIgnoreSaveOnDestroyOnce() { return comp != null && comp.saveFlagController != null && comp.saveFlagController.shouldIgnoreSaveOnDestroyOnce(); }
    public void clearIgnoreSaveOnStopFlag() { if (comp != null && comp.saveFlagController != null) comp.saveFlagController.clearIgnoreSaveOnStopFlag(); }
    public void clearIgnoreSaveOnDestroyFlag() { if (comp != null && comp.saveFlagController != null) comp.saveFlagController.clearIgnoreSaveOnDestroyFlag(); }
    public void setIgnoreSaveFlagsForFinish() { if (comp != null && comp.saveFlagController != null) comp.saveFlagController.setIgnoreSaveFlagsForFinish(); }
    public int maxRecentFiles() { return comp != null && comp.saveFlagController != null ? comp.saveFlagController.maxRecentFiles() : 20; }
    public void markIgnoreSaveOnStop() { if (comp != null && comp.saveFlagController != null) comp.saveFlagController.markIgnoreSaveOnStop(); }

    public boolean isPreparingOptionsMenu() {
        return comp != null && comp.optionsMenuController != null && comp.optionsMenuController.isPreparingOptionsMenu();
    }
}
