package org.opendroidpdf.app.lifecycle;

import android.net.Uri;
import androidx.annotation.NonNull;

import java.util.concurrent.Callable;

/**
 * Extracts common lifecycle glue so the Activity stays slimmer.
 * Host supplies the concrete operations; this class just sequences them.
 */
public final class LifecycleHooks {

    public interface Host {
        // onPause
        void stopSearchTasks();
        boolean hasCore();
        Uri coreUri();
        void saveViewport(@NonNull Uri uri);
        void coreStopAlerts();
        void destroyAlertWaiter();

        // onStop
        boolean isChangingConfigurations();
        boolean hasUnsavedChanges();
        boolean getSaveOnStop();
        boolean getIgnoreSaveOnStopThisTime();
        void clearIgnoreSaveOnStopFlag();
        boolean canSaveToCurrentUri();
        void saveInBackground(Callable<?> ok, Callable<?> err);
        void showInfo(@NonNull String message);
        void cancelRenderThumbnailJob();

        // onDestroy core/save
        boolean getSaveOnDestroy();
        boolean getIgnoreSaveOnDestroyThisTime();
        void clearIgnoreSaveOnDestroyFlag();
        void destroyCoreNow();
    }

    private final Host host;

    public LifecycleHooks(@NonNull Host host) {
        this.host = host;
    }

    public void onPause() {
        host.stopSearchTasks();
        if (host.hasCore()) {
            Uri uri = host.coreUri();
            if (uri != null) host.saveViewport(uri);
            host.coreStopAlerts();
            host.destroyAlertWaiter();
        }
    }

    public void onStop() {
        if (!host.isChangingConfigurations() && host.hasUnsavedChanges()) {
            if (host.getSaveOnStop() && !host.getIgnoreSaveOnStopThisTime() && host.canSaveToCurrentUri()) {
                host.saveInBackground(null, new Callable<Void>() {
                    @Override public Void call() {
                        host.showInfo("Error saving");
                        return null;
                    }
                });
            }
        }
        host.clearIgnoreSaveOnStopFlag();
        host.cancelRenderThumbnailJob();
    }

    public void onDestroy() {
        if (host.hasCore() && host.hasUnsavedChanges() && !host.isChangingConfigurations()) {
            if (host.getSaveOnDestroy() && !host.getIgnoreSaveOnDestroyThisTime() && host.canSaveToCurrentUri()) {
                host.saveInBackground(
                        new Callable<Object>() {
                            @Override public Object call() {
                                host.destroyCoreNow();
                                return null;
                            }
                        },
                        new Callable<Void>() {
                            @Override public Void call() {
                                host.showInfo("Error saving");
                                host.destroyCoreNow();
                                return null;
                            }
                        }
                );
            }
        }
        host.clearIgnoreSaveOnDestroyFlag();
    }
}
