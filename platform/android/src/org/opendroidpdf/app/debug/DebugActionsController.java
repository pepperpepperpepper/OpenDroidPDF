package org.opendroidpdf.app.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
        }
        return false;
    }

    public static void registerDebugBroadcasts(@NonNull final OpenDroidPDFActivity host) {
        if (!BuildConfig.DEBUG) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (ACTION_SNAP_TO_FIT.equals(intent.getAction())) {
                    performSnapToFit(host);
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_SNAP_TO_FIT);
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
}
