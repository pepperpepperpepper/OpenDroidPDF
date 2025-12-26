package org.opendroidpdf.app.helpers;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.opendroidpdf.BuildConfig;

/**
 * Routes incoming intents into document/dashboard flows so the activity
 * keeps only the actual actions.
 */
public class IntentRouter {
    public static final String ACTION_DEBUG_IMPORT_SIDECAR_BUNDLE =
            "org.opendroidpdf.DEBUG_IMPORT_SIDECAR_BUNDLE";
    public static final String EXTRA_FORCE_IMPORT = "org.opendroidpdf.EXTRA_FORCE_IMPORT";

    public interface Host {
        boolean hasCore();
        void showDashboard();
        void openDocumentFromIntent(Intent intent);
        void resetDocumentStateForIntent();
        boolean ensureStoragePermission(Intent intent);
        void importSidecarAnnotationsBundleFromUri(Uri uri, boolean forceImport);
    }

    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingDashboardRunnable;
    private static final String TAG = "IntentRouter";

    public IntentRouter(Host host) {
        this.host = host;
    }

    public void cancelPendingActions() {
        if (pendingDashboardRunnable != null) {
            mainHandler.removeCallbacks(pendingDashboardRunnable);
            pendingDashboardRunnable = null;
        }
    }

    /**
     * Handle intent on resume. Returns true if an action was taken or the intent
     * was consumed; false means caller can proceed with normal flow.
     */
    public boolean handleOnResume(Intent intent) {
        if (intent == null) {
            return false;
        }
        cancelPendingActions();
        String action = intent.getAction();
        Uri data = intent.getData();
        boolean hasDocumentData = data != null;

        if (BuildConfig.DEBUG && ACTION_DEBUG_IMPORT_SIDECAR_BUNDLE.equals(action)) {
            Uri bundleUri = intent.getData();
            if (bundleUri != null) {
                host.importSidecarAnnotationsBundleFromUri(
                        bundleUri,
                        intent.getBooleanExtra(EXTRA_FORCE_IMPORT, false));
                return true;
            }
            return true;
        }

        if (Intent.ACTION_MAIN.equals(action) && !host.hasCore()) {
            // Show dashboard after a short delay so animations play.
            cancelPendingActions();
            pendingDashboardRunnable = new Runnable() {
                @Override public void run() {
                    host.showDashboard();
                    pendingDashboardRunnable = null;
                }
            };
            mainHandler.postDelayed(pendingDashboardRunnable, 100);
            return true;
        } else if ((Intent.ACTION_VIEW.equals(action) || (hasDocumentData && !host.hasCore())) && data != null) {
            if (!host.ensureStoragePermission(intent)) return true;
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
        cancelPendingActions();
        Log.i(TAG, "onNewIntent(): action=" + intent.getAction() + " data=" + intent.getData());
        if (BuildConfig.DEBUG && ACTION_DEBUG_IMPORT_SIDECAR_BUNDLE.equals(intent.getAction())) {
            Uri bundleUri = intent.getData();
            if (bundleUri != null) {
                host.importSidecarAnnotationsBundleFromUri(
                        bundleUri,
                        intent.getBooleanExtra(EXTRA_FORCE_IMPORT, false));
                return true;
            }
            return true;
        }
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
