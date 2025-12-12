package org.opendroidpdf.app.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.view.MenuItem;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.app.reader.ReaderGeometry;

/**
 * Debug-only hooks and actions.
 */
public final class DebugActionsController {
    public static final String ACTION_SNAP_TO_FIT = "org.opendroidpdf.DEBUG_SNAP_TO_FIT";
    public static final String ACTION_EXPORT_TEST = "org.opendroidpdf.DEBUG_EXPORT_TEST";

    private DebugActionsController() {}

    public static boolean onOptionsItemSelected(@NonNull OpenDroidPDFActivity host,
                                                @NonNull MenuItem item) {
        if (!BuildConfig.DEBUG) return false;
        int id = item.getItemId();
        if (id == R.id.menu_debug_snap_fit) {
            performSnapToFit(host);
            return true;
        } else if (id == R.id.menu_debug_show_text_widget) {
            MuPDFReaderView docView = host.getDocView();
            if (docView != null) docView.debugShowTextWidgetDialog();
            return true;
        } else if (id == R.id.menu_debug_show_choice_widget) {
            MuPDFReaderView docView = host.getDocView();
            if (docView != null) docView.debugShowChoiceWidgetDialog();
            return true;
        } else if (id == R.id.menu_debug_export_test) {
            performExportTest(host);
            return true;
        }
        return false;
    }

    /** Visible for debug/instrumentation: invoke export test directly. */
    public static android.net.Uri runExportTest(@NonNull OpenDroidPDFActivity host) {
        if (!BuildConfig.DEBUG) return null;
        return performExportTest(host);
    }

    public static void registerDebugBroadcasts(@NonNull final OpenDroidPDFActivity host) {
        if (!BuildConfig.DEBUG) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent != null ? intent.getAction() : null;
                if (ACTION_SNAP_TO_FIT.equals(action)) {
                    performSnapToFit(host);
                } else if (ACTION_EXPORT_TEST.equals(action)) {
                    performExportTest(host);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SNAP_TO_FIT);
        filter.addAction(ACTION_EXPORT_TEST);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            host.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            host.registerReceiver(receiver, filter);
        }
    }

    private static void performSnapToFit(@NonNull OpenDroidPDFActivity host) {
        android.util.Log.d("OpenDroidPDF/Debug", "performSnapToFit invoked");
        final MuPDFReaderView docView = host.getDocView();
        if (docView == null) return;
        View cv = docView.getSelectedView();
        if (cv == null) return;

        final int cw = docView.getWidth();
        final int ch = docView.getHeight();
        final float fill = ReaderGeometry.fillScreenScale(
                cw, ch,
                docView.getPaddingLeft(), docView.getPaddingRight(),
                docView.getPaddingTop(), docView.getPaddingBottom(),
                cv.getMeasuredWidth(), cv.getMeasuredHeight());
        final float fitWidthScale = (float) cw / (cv.getMeasuredWidth() * fill);

        // Place scale slightly below target to satisfy the in-range snap check,
        // then invoke the ReaderView's debug snap.
        float seed = Math.max(1.0f, fitWidthScale * 0.93f);
        docView.setScale(seed);
        // Ensure fit‑width is enabled for this check.
        host.getSharedPreferences("org.opendroidpdf_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean("pref_fit_width", true).apply();
        docView.debugTriggerSnapToFitWidthIfEligible();
        android.util.Log.d("OpenDroidPDF/Debug", "performSnapToFit done (target="+fitWidthScale+", seed="+seed+")");
        try {
            Toast.makeText(host, "Snap-to-Fit: target=" + String.format(java.util.Locale.US, "%.3f", fitWidthScale), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {}
    }

    private static android.net.Uri performExportTest(@NonNull OpenDroidPDFActivity host) {
        android.util.Log.d("OpenDroidPDF/Debug", "performExportTest invoked");
        org.opendroidpdf.core.MuPdfRepository repo = host.getRepository();
        if (repo == null) {
            android.util.Log.w("OpenDroidPDF/Debug", "performExportTest skipped: no repository");
            return null;
        }
        host.commitPendingInkToCoreBlocking();
        try {
            Uri exported = repo.exportDocument(host);
            if (exported == null) {
                android.util.Log.e("OpenDroidPDF/Debug", "performExportTest failed: export returned null");
                Toast.makeText(host, "Export test failed", Toast.LENGTH_SHORT).show();
                return null;
            }
            android.util.Log.i("OpenDroidPDF/Debug", "performExportTest exported=" + exported);
            Toast.makeText(host, "Exported to: " + exported, Toast.LENGTH_SHORT).show();
            return exported;
        } catch (Exception e) {
            android.util.Log.e("OpenDroidPDF/Debug", "performExportTest error", e);
            Toast.makeText(host, "Export error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }
}
