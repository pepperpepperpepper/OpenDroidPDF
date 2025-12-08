package org.opendroidpdf.app.helpers;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import kotlinx.coroutines.Job;
import org.opendroidpdf.app.AppCoroutines;

/**
 * Routes incoming intents into document/dashboard flows so the activity
 * keeps only the actual actions.
 */
public class IntentRouter {

    public interface Host {
        boolean hasCore();
        void showDashboard();
        void openDocumentFromIntent(Intent intent);
        void resetDocumentStateForIntent();
        boolean ensureStoragePermission(Intent intent);
    }

    private final Host host;
    private Job pendingDashboardJob;
    private static final String TAG = "IntentRouter";

    public IntentRouter(Host host) {
        this.host = host;
    }

    /**
     * Handle intent on resume. Returns true if an action was taken or the intent
     * was consumed; false means caller can proceed with normal flow.
     */
    public boolean handleOnResume(Intent intent) {
        if (intent == null) {
            return false;
        }
        AppCoroutines.cancel(pendingDashboardJob);
        String action = intent.getAction();
        Uri data = intent.getData();
        boolean hasDocumentData = data != null;

        if (Intent.ACTION_MAIN.equals(action) && !host.hasCore()) {
            // Show dashboard after a short delay so animations play.
            AppCoroutines.cancel(pendingDashboardJob);
            pendingDashboardJob = AppCoroutines.launchMainDelayed(
                    AppCoroutines.mainScope(),
                    100,
                    new Runnable() {
                        @Override public void run() {
                            host.showDashboard();
                        }
                    });
            return true;
        } else if ((Intent.ACTION_VIEW.equals(action) || (hasDocumentData && !host.hasCore())) && data != null) {
            if (!host.ensureStoragePermission(intent))
                return true;
            host.openDocumentFromIntent(intent);
            return true;
        }
        return false;
    }

    /**
     * Handle onNewIntent, returning true if routed.
     */
    public boolean handleOnNewIntent(Intent intent) {
        if (intent == null) return false;
        AppCoroutines.cancel(pendingDashboardJob);
        Log.i(TAG, "onNewIntent(): action=" + intent.getAction() + " data=" + intent.getData());
        if (intent.getData() == null) {
            return false;
        }
        if (!host.ensureStoragePermission(intent)) {
            return true; // permission flow launched
        }
        host.resetDocumentStateForIntent();
        host.openDocumentFromIntent(intent);
        return true;
    }
}
