package org.opendroidpdf.app.helpers;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFActivity;

/**
 * Handles onResume/onNewIntent wiring so the activity surface stays slimmer.
 */
public final class IntentResumeDelegate {
    private static final String TAG = "OpenDroidPDF/Intent";
    private final OpenDroidPDFActivity activity;
    private final IntentRouter intentRouter;

    public IntentResumeDelegate(OpenDroidPDFActivity activity, IntentRouter intentRouter) {
        this.activity = activity;
        this.intentRouter = intentRouter;
    }

    public void onResume(@Nullable Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri data = intent.getData();
        Log.i(TAG, "onResume(): action=" + action + " data=" + data);
        if (intentRouter != null && intentRouter.handleOnResume(intent)) {
            activity.invalidateOptionsMenuSafely();
            return;
        }
        activity.invalidateOptionsMenuSafely();
    }

    public void onNewIntent(Intent intent) {
        if (intent == null) return;
        activity.setIntent(intent);
        Log.i(TAG, "onNewIntent(): action=" + intent.getAction() + " data=" + intent.getData());
        if (intentRouter != null) intentRouter.handleOnNewIntent(intent);
    }

    public void onPause() {
        if (intentRouter != null) intentRouter.cancelPendingActions();
    }
}
