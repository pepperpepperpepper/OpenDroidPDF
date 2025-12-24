package org.opendroidpdf.app.helpers;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Handles onResume/onNewIntent wiring so the activity surface stays slimmer.
 */
public final class IntentResumeDelegate {
    private static final String TAG = "OpenDroidPDF/Intent";

    public interface Host {
        void invalidateOptionsMenuSafely();
        void setIntent(Intent intent);
    }

    private final Host host;
    private final IntentRouter intentRouter;

    public IntentResumeDelegate(Host host, IntentRouter intentRouter) {
        this.host = host;
        this.intentRouter = intentRouter;
    }

    public void onResume(@Nullable Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri data = intent.getData();
        Log.i(TAG, "onResume(): action=" + action + " data=" + data);
        if (intentRouter != null && intentRouter.handleOnResume(intent)) {
            if (host != null) host.invalidateOptionsMenuSafely();
            return;
        }
        if (host != null) host.invalidateOptionsMenuSafely();
    }

    public void onNewIntent(Intent intent) {
        if (intent == null) return;
        if (host != null) host.setIntent(intent);
        Log.i(TAG, "onNewIntent(): action=" + intent.getAction() + " data=" + intent.getData());
        if (intentRouter != null) intentRouter.handleOnNewIntent(intent);
    }

    public void onPause() {
        if (intentRouter != null) intentRouter.cancelPendingActions();
    }
}
