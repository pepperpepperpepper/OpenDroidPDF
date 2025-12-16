package org.opendroidpdf.app.debug;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.Environment;
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
    public static final String ACTION_ALERT_TEST = "org.opendroidpdf.DEBUG_ALERT_TEST";
    public static final String ACTION_RENDER_SELF_TEST = "org.opendroidpdf.DEBUG_RENDER_SELF_TEST";

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
        } else if (id == R.id.menu_debug_alert_test) {
            performAlertTest(host);
            return true;
        } else if (id == R.id.menu_debug_render_self_test) {
            performRenderSelfTest(host);
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
                } else if (ACTION_ALERT_TEST.equals(action)) {
                    performAlertTest(host);
                } else if (ACTION_RENDER_SELF_TEST.equals(action)) {
                    performRenderSelfTest(host);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SNAP_TO_FIT);
        filter.addAction(ACTION_EXPORT_TEST);
        filter.addAction(ACTION_ALERT_TEST);
        filter.addAction(ACTION_RENDER_SELF_TEST);
        androidx.core.content.ContextCompat.registerReceiver(
                host,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
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

    private static void performAlertTest(@NonNull OpenDroidPDFActivity host) {
        android.util.Log.d("OpenDroidPDF/Debug", "performAlertTest invoked");
        if (host.alertBuilder() == null) {
            host.setAlertBuilder(new androidx.appcompat.app.AlertDialog.Builder(host));
        }
        androidx.appcompat.app.AlertDialog.Builder b = host.alertBuilder();
        if (b == null) return;
        b.setTitle("Debug Alert")
                .setMessage("This is a debug-only alert plumbing test.")
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void performRenderSelfTest(@NonNull OpenDroidPDFActivity host) {
        android.util.Log.d("OpenDroidPDF/Debug", "performRenderSelfTest invoked");
        org.opendroidpdf.core.MuPdfRepository repo = host.getRepository();
        if (repo == null) {
            android.util.Log.w("OpenDroidPDF/Debug", "Render self-test skipped: no repository");
            return;
        }
        try {
            PointF size = repo.getPageSize(0);
            float scale = 2.0f;
            int targetW = 1200;
            int targetH = 1600;
            if (size != null && size.x > 0 && size.y > 0) {
                float ratio = size.y / size.x;
                targetW = Math.min(2000, Math.max(400, (int) (size.x * scale)));
                targetH = Math.min(2600, Math.max(400, (int) (targetW * ratio)));
            }
            Bitmap bm = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
            org.opendroidpdf.MuPDFCore.Cookie cookie = repo.newRenderCookie();
            try {
                repo.drawPage(bm, /*page*/0, targetW, targetH, 0, 0, targetW, targetH, cookie);
            } finally {
                cookie.destroy();
            }
            boolean uniform = looksUniform(bm);
            java.io.File outDir = host.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (outDir == null) outDir = host.getExternalFilesDir(null);
            if (outDir == null) outDir = host.getFilesDir();
            java.io.File out = new java.io.File(outDir, "odp_render_test.png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            bm.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            android.util.Log.i("OpenDroidPDF/Debug", "Render self-test saved " + out.getAbsolutePath()
                    + " uniform=" + uniform + " size=" + targetW + "x" + targetH);
            try {
                Toast.makeText(host, "Render self-test saved: " + out.getName()
                        + (uniform ? " (uniform!)" : ""), Toast.LENGTH_LONG).show();
            } catch (Throwable ignore) {}
        } catch (Exception e) {
            android.util.Log.e("OpenDroidPDF/Debug", "Render self-test failed", e);
            try { Toast.makeText(host, "Render self-test failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); } catch (Throwable ignore) {}
        }
    }

    private static boolean looksUniform(Bitmap bm) {
        if (bm == null) return false;
        int w = bm.getWidth();
        int h = bm.getHeight();
        if (w == 0 || h == 0) return false;
        int[] samples = new int[25];
        int idx = 0;
        for (int yi = 0; yi < 5; yi++) {
            for (int xi = 0; xi < 5; xi++) {
                int x = (int)((xi + 0.5f) * w / 5f);
                int y = (int)((yi + 0.5f) * h / 5f);
                samples[idx++] = bm.getPixel(Math.min(x, w - 1), Math.min(y, h - 1));
            }
        }
        int base = samples[0];
        final int tol = 3;
        for (int i = 1; i < samples.length; i++) {
            int c = samples[i];
            int dr = Math.abs(((c >> 16) & 0xFF) - ((base >> 16) & 0xFF));
            int dg = Math.abs(((c >> 8) & 0xFF) - ((base >> 8) & 0xFF));
            int db = Math.abs((c & 0xFF) - (base & 0xFF));
            if (dr > tol || dg > tol || db > tol) return false;
        }
        return true;
    }
}
