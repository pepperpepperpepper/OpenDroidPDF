package org.opendroidpdf.app.hosts;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.document.SaveUiDelegate;
import org.opendroidpdf.app.lifecycle.LifecycleHooks;
import org.opendroidpdf.app.lifecycle.SaveFlagController;

import java.util.concurrent.Callable;

/**
 * Maps {@link LifecycleHooks.Host} calls onto the activity, keeping the anonymous
 * host implementation out of the activity class.
 */
public final class ActivityLifecycleHostAdapter implements LifecycleHooks.Host {
    private final OpenDroidPDFActivity activity;
    private final SaveFlagController saveFlags;
    private final SaveUiDelegate saveUi;

    public ActivityLifecycleHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                        SaveFlagController saveFlags,
                                        SaveUiDelegate saveUi) {
        this.activity = activity;
        this.saveFlags = saveFlags;
        this.saveUi = saveUi;
    }

    @Override public void stopSearchTasks() { activity.stopSearchTasks(); }
    @Override public boolean hasCore() { return activity.getCore() != null; }
    @Override public Uri coreUri() {
        OpenDroidPDFCore core = activity.getCore();
        return core != null ? core.getUri() : null;
    }
    @Override public void saveViewport(@NonNull Uri uri) {
        if (activity.getViewportController() != null) {
            activity.getViewportController().saveViewport(uri);
        }
    }
    @Override public void coreStopAlerts() {
        OpenDroidPDFCore core = activity.getCore();
        if (core != null) core.stopAlerts();
    }
    @Override public void destroyAlertWaiter() { activity.destroyAlertWaiter(); }

    @Override public boolean isChangingConfigurations() { return activity.isChangingConfigurations(); }
    @Override public boolean hasUnsavedChanges() { return activity.hasUnsavedChanges(); }
    @Override public boolean getSaveOnStop() { return saveFlags != null && saveFlags.shouldSaveOnStop(); }
    @Override public boolean getIgnoreSaveOnStopThisTime() { return saveFlags != null && saveFlags.shouldIgnoreSaveOnStopOnce(); }
    @Override public void clearIgnoreSaveOnStopFlag() { if (saveFlags != null) saveFlags.clearIgnoreSaveOnStopFlag(); }
    @Override public boolean canSaveToCurrentUri() { return activity.canSaveToCurrentUri(); }
    @Override public void saveInBackground(Callable<?> ok, Callable<?> err) {
        if (saveUi != null) {
            saveUi.saveInBackground(ok, err);
        }
    }
    @Override public void showInfo(@NonNull String message) { activity.showInfo(message); }
    @Override public void cancelRenderThumbnailJob() { activity.cancelRenderThumbnailJob(); }

    @Override public boolean getSaveOnDestroy() { return saveFlags != null && saveFlags.shouldSaveOnDestroy(); }
    @Override public boolean getIgnoreSaveOnDestroyThisTime() { return saveFlags != null && saveFlags.shouldIgnoreSaveOnDestroyOnce(); }
    @Override public void clearIgnoreSaveOnDestroyFlag() { if (saveFlags != null) saveFlags.clearIgnoreSaveOnDestroyFlag(); }
    @Override public void destroyCoreNow() { activity.destroyCoreNow(); }
}
