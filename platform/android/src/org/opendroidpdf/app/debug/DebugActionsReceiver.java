package org.opendroidpdf.app.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import org.opendroidpdf.BuildConfig;

/** Manifest-registered bridge to run debug actions via adb broadcasts. */
public final class DebugActionsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BuildConfig.DEBUG) return;
        String action = intent != null ? intent.getAction() : null;
        @Nullable DebugActionsController.Host host = DebugActionsController.lastHost();
        android.util.Log.d("OpenDroidPDF/Debug",
                "DebugActionsReceiver onReceive action=" + action + " host=" + (host != null));
        DebugActionsController.dispatchAction(host, action);
    }
}
