package org.opendroidpdf.app.lifecycle;

import androidx.annotation.NonNull;
import android.net.Uri;

import java.util.concurrent.Callable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;

/**
 * Maps {@link LifecycleHooks.Host} calls onto the activity, keeping the anonymous
 * host implementation out of {@link OpenDroidPDFActivity}.
 */
public final class ActivityLifecycleHostAdapter implements LifecycleHooks.Host {
    private final OpenDroidPDFActivity activity;

    public ActivityLifecycleHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
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
    @Override public boolean getSaveOnStop() { return activity.shouldSaveOnStop(); }
    @Override public boolean getIgnoreSaveOnStopThisTime() { return activity.shouldIgnoreSaveOnStopOnce(); }
    @Override public void clearIgnoreSaveOnStopFlag() { activity.clearIgnoreSaveOnStopFlag(); }
    @Override public boolean canSaveToCurrentUri() { return activity.canSaveToCurrentUri(); }
    @Override public void saveInBackground(Callable<?> ok, Callable<?> err) {
        if (activity.getSaveUiDelegate() != null) {
            activity.getSaveUiDelegate().saveInBackground(ok, err);
        }
    }
    @Override public void showInfo(@NonNull String message) { activity.showInfo(message); }
    @Override public void cancelRenderThumbnailJob() { activity.cancelRenderThumbnailJob(); }

    @Override public boolean getSaveOnDestroy() { return activity.shouldSaveOnDestroy(); }
    @Override public boolean getIgnoreSaveOnDestroyThisTime() { return activity.shouldIgnoreSaveOnDestroyOnce(); }
    @Override public void clearIgnoreSaveOnDestroyFlag() { activity.clearIgnoreSaveOnDestroyFlag(); }
    @Override public void destroyCoreNow() { activity.destroyCoreNow(); }
}
