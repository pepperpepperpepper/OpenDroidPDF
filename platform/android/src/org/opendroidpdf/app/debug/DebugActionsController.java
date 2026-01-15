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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Nullable;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.reader.ReaderGeometry;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.core.PdfOps;

/**
 * Debug-only hooks and actions.
 */
public final class DebugActionsController {
    private static volatile Host lastHost;

    public static final String ACTION_SNAP_TO_FIT = "org.opendroidpdf.DEBUG_SNAP_TO_FIT";
    public static final String ACTION_EXPORT_TEST = "org.opendroidpdf.DEBUG_EXPORT_TEST";
    public static final String ACTION_ALERT_TEST = "org.opendroidpdf.DEBUG_ALERT_TEST";
    public static final String ACTION_RENDER_SELF_TEST = "org.opendroidpdf.DEBUG_RENDER_SELF_TEST";
    public static final String ACTION_TEXT_MULTI_ADD = "org.opendroidpdf.DEBUG_TEXT_MULTI_ADD";
    public static final String ACTION_TEXT_MULTI_ALIGN_LEFT = "org.opendroidpdf.DEBUG_TEXT_MULTI_ALIGN_LEFT";
    public static final String ACTION_TEXT_MULTI_DISTRIBUTE_H = "org.opendroidpdf.DEBUG_TEXT_MULTI_DISTRIBUTE_H";
    public static final String ACTION_TEXT_MULTI_TOGGLE_GROUP = "org.opendroidpdf.DEBUG_TEXT_MULTI_TOGGLE_GROUP";
    public static final String ACTION_TEXT_MULTI_NUDGE = "org.opendroidpdf.DEBUG_TEXT_MULTI_NUDGE";

    private DebugActionsController() {}

    public interface Host {
        @NonNull Context context();
        @Nullable MuPDFReaderView docViewOrNull();
        @Nullable org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController textMultiSelectOrNull();
        @Nullable MuPdfRepository repositoryOrNull();
        void commitPendingInkToCoreBlocking();
        @Nullable androidx.appcompat.app.AlertDialog.Builder alertBuilder();
        void setAlertBuilder(@NonNull androidx.appcompat.app.AlertDialog.Builder builder);
    }

    public static boolean onOptionsItemSelected(@NonNull Host host,
                                                @NonNull MenuItem item) {
        if (!BuildConfig.DEBUG) return false;
        int id = item.getItemId();
        if (id == R.id.menu_debug_snap_fit) {
            performSnapToFit(host);
            return true;
        } else if (id == R.id.menu_debug_show_text_widget) {
            MuPDFReaderView docView = host.docViewOrNull();
            if (docView != null) {
                android.view.View v = docView.getSelectedView();
                if (v instanceof org.opendroidpdf.MuPDFPageView) {
                    ((org.opendroidpdf.MuPDFPageView) v).debugShowTextWidgetDialog();
                }
            }
            return true;
        } else if (id == R.id.menu_debug_show_choice_widget) {
            MuPDFReaderView docView = host.docViewOrNull();
            if (docView != null) {
                android.view.View v = docView.getSelectedView();
                if (v instanceof org.opendroidpdf.MuPDFPageView) {
                    ((org.opendroidpdf.MuPDFPageView) v).debugShowChoiceWidgetDialog();
                }
            }
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
        } else if (id == R.id.menu_debug_qpdf_smoke) {
            performQpdfSmoke(host);
            return true;
        } else if (id == R.id.menu_debug_pdfbox_flatten) {
            performPdfBoxFlatten(host);
            return true;
        }
        return false;
    }

    /** Visible for debug/instrumentation: invoke export test directly. */
    public static android.net.Uri runExportTest(@NonNull Host host) {
        if (!BuildConfig.DEBUG) return null;
        return performExportTest(host);
    }

    private static void performPdfBoxFlatten(@NonNull Host host) {
        android.util.Log.d("OpenDroidPDF/Debug", "performPdfBoxFlatten invoked");
        final Context ctx = host.context();
        if (ctx == null) return;
        org.opendroidpdf.core.MuPdfRepository repo = host.repositoryOrNull();
        if (repo == null) {
            android.util.Log.w("OpenDroidPDF/Debug", "pdfbox flatten skipped: no repository");
            return;
        }
        if (!org.opendroidpdf.core.PdfBoxFacade.isAvailable()) {
            Toast.makeText(ctx, "pdfboxops not bundled; enable -Popendroidpdf.enablePdfBoxOps=true", Toast.LENGTH_LONG).show();
            return;
        }
        host.commitPendingInkToCoreBlocking();
        try {
            Uri input = repo.exportDocument(ctx);
            if (input == null) {
                Toast.makeText(ctx, "Flatten: export failed", Toast.LENGTH_SHORT).show();
                return;
            }
            File outFile = new File(ctx.getCacheDir(), "pdfbox_flattened.pdf");
            if (outFile.exists()) outFile.delete();
            Uri outUri = androidx.core.content.FileProvider.getUriForFile(ctx, "org.opendroidpdf.fileprovider", outFile);
            org.opendroidpdf.core.PdfBoxFacade.FlattenResult res =
                    org.opendroidpdf.core.PdfBoxFacade.flattenForm(ctx, input, outUri);
            Toast.makeText(ctx,
                    "Flattened pages=" + res.pageCount + " form=" + res.hadAcroForm,
                    Toast.LENGTH_LONG).show();
            android.util.Log.i("OpenDroidPDF/Debug", "pdfbox flatten success pages=" + res.pageCount + " hadForm=" + res.hadAcroForm + " out=" + outUri);
        } catch (Exception e) {
            android.util.Log.e("OpenDroidPDF/Debug", "pdfbox flatten failed", e);
            Toast.makeText(ctx, "Flatten error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void registerDebugBroadcasts(@NonNull final Host host) {
        if (!BuildConfig.DEBUG) return;
        lastHost = host;
        final Context ctx = host.context();
        if (ctx == null) return;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent != null ? intent.getAction() : null;
                dispatchAction(host, action);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SNAP_TO_FIT);
        filter.addAction(ACTION_EXPORT_TEST);
        filter.addAction(ACTION_ALERT_TEST);
        filter.addAction(ACTION_RENDER_SELF_TEST);
        filter.addAction(ACTION_TEXT_MULTI_ADD);
        filter.addAction(ACTION_TEXT_MULTI_ALIGN_LEFT);
        filter.addAction(ACTION_TEXT_MULTI_DISTRIBUTE_H);
        filter.addAction(ACTION_TEXT_MULTI_TOGGLE_GROUP);
        filter.addAction(ACTION_TEXT_MULTI_NUDGE);
        androidx.core.content.ContextCompat.registerReceiver(
                ctx,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private static void performSnapToFit(@NonNull Host host) {
        android.util.Log.d("OpenDroidPDF/Debug", "performSnapToFit invoked");
        final MuPDFReaderView docView = host.docViewOrNull();
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
        docView.debugTriggerSnapToFitWidthAssumingFitWidthEnabled();
        android.util.Log.d("OpenDroidPDF/Debug", "performSnapToFit done (target="+fitWidthScale+", seed="+seed+")");
        try {
            Toast.makeText(host.context(), "Snap-to-Fit: target=" + String.format(java.util.Locale.US, "%.3f", fitWidthScale), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {}
    }

    private static android.net.Uri performExportTest(@NonNull Host host) {
        android.util.Log.d("OpenDroidPDF/Debug", "performExportTest invoked");
        final Context ctx = host.context();
        org.opendroidpdf.core.MuPdfRepository repo = host.repositoryOrNull();
        if (repo == null) {
            android.util.Log.w("OpenDroidPDF/Debug", "performExportTest skipped: no repository");
            return null;
        }
        host.commitPendingInkToCoreBlocking();
        try {
            Uri exported = repo.exportDocument(ctx);
            if (exported == null) {
                android.util.Log.e("OpenDroidPDF/Debug", "performExportTest failed: export returned null");
                Toast.makeText(ctx, "Export test failed", Toast.LENGTH_SHORT).show();
                return null;
            }
            android.util.Log.i("OpenDroidPDF/Debug", "performExportTest exported=" + exported);
            Toast.makeText(ctx, "Exported to: " + exported, Toast.LENGTH_SHORT).show();
            return exported;
        } catch (Exception e) {
            android.util.Log.e("OpenDroidPDF/Debug", "performExportTest error", e);
            Toast.makeText(ctx, "Export error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private static void performAlertTest(@NonNull Host host) {
        android.util.Log.d("OpenDroidPDF/Debug", "performAlertTest invoked");
        final Context ctx = host.context();
        if (ctx == null) return;
        if (host.alertBuilder() == null) {
            host.setAlertBuilder(new androidx.appcompat.app.AlertDialog.Builder(ctx));
        }
        androidx.appcompat.app.AlertDialog.Builder b = host.alertBuilder();
        if (b == null) return;
        b.setTitle("Debug Alert")
                .setMessage("This is a debug-only alert plumbing test.")
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void performRenderSelfTest(@NonNull Host host) {
        android.util.Log.d("OpenDroidPDF/Debug", "performRenderSelfTest invoked");
        final Context ctx = host.context();
        if (ctx == null) return;
        org.opendroidpdf.core.MuPdfRepository repo = host.repositoryOrNull();
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
            java.io.File outDir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (outDir == null) outDir = ctx.getExternalFilesDir(null);
            if (outDir == null) outDir = ctx.getFilesDir();
            java.io.File out = new java.io.File(outDir, "odp_render_test.png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            bm.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            android.util.Log.i("OpenDroidPDF/Debug", "Render self-test saved " + out.getAbsolutePath()
                    + " uniform=" + uniform + " size=" + targetW + "x" + targetH);
            try {
                Toast.makeText(ctx, "Render self-test saved: " + out.getName()
                        + (uniform ? " (uniform!)" : ""), Toast.LENGTH_LONG).show();
            } catch (Throwable ignore) {}
        } catch (Exception e) {
            android.util.Log.e("OpenDroidPDF/Debug", "Render self-test failed", e);
            try { Toast.makeText(ctx, "Render self-test failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); } catch (Throwable ignore) {}
        }
    }

    private static void performQpdfSmoke(@NonNull Host host) {
        if (!BuildConfig.DEBUG) return;
        final Context ctx = host.context();
        final MuPdfRepository repo = host.repositoryOrNull();
        if (ctx == null || repo == null) {
            android.util.Log.w("OpenDroidPDF/Debug", "qpdf smoke skipped: missing ctx/repo");
            return;
        }
        if (!org.opendroidpdf.BuildConfig.ENABLE_QPDF_OPS) {
            Toast.makeText(ctx, "qpdf ops disabled by flag", Toast.LENGTH_SHORT).show();
            return;
        }
        host.commitPendingInkToCoreBlocking();
        File cache = new File(ctx.getCacheDir(), "qpdf_smoke");
        cache.mkdirs();
        try {
            Uri exported = repo.exportDocument(ctx);
            if (exported == null) {
                Toast.makeText(ctx, "qpdf smoke: export failed", Toast.LENGTH_SHORT).show();
                return;
            }
            File src = new File(cache, "src.pdf");
            File srcB = new File(cache, "src_b.pdf");
            copyUriToFile(ctx, exported, src);
            copyUriToFile(ctx, exported, srcB);

            File merged = new File(cache, "merged.pdf");
            File linearized = new File(cache, "linearized.pdf");
            File encrypted = new File(cache, "encrypted.pdf");
            File decrypted = new File(cache, "decrypted.pdf");

            boolean mergeOk = PdfOps.INSTANCE.mergePdfs(src, srcB, merged);
            boolean linearOk = mergeOk && PdfOps.INSTANCE.linearizePdf(merged, linearized);
            boolean encryptOk = linearOk && PdfOps.INSTANCE.encryptPdf(
                    linearized,
                    "userpw",
                    "ownerpw",
                    encrypted,
                    "256"
            );
            boolean decryptOk = encryptOk && PdfOps.INSTANCE.decryptPdf(
                    encrypted,
                    "userpw",
                    decrypted
            );

            String summary = "qpdf smoke "
                    + (mergeOk ? "merge-ok " : "merge-fail ")
                    + (linearOk ? "lin-ok " : "lin-fail ")
                    + (encryptOk ? "enc-ok " : "enc-fail ")
                    + (decryptOk ? "dec-ok" : "dec-fail");
            android.util.Log.i("OpenDroidPDF/Debug", summary + " cache=" + cache);
            Toast.makeText(ctx, summary, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("OpenDroidPDF/Debug", "qpdf smoke failed", e);
            Toast.makeText(ctx, "qpdf smoke error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void dispatchAction(@Nullable Host host, @Nullable String action) {
        if (!BuildConfig.DEBUG) return;
        if (host == null || action == null) {
            android.util.Log.w("OpenDroidPDF/Debug",
                    "dispatchAction skipped host=" + (host != null) + " action=" + action);
            return;
        }
        if (ACTION_SNAP_TO_FIT.equals(action)) {
            performSnapToFit(host);
        } else if (ACTION_EXPORT_TEST.equals(action)) {
            performExportTest(host);
        } else if (ACTION_ALERT_TEST.equals(action)) {
            performAlertTest(host);
        } else if (ACTION_RENDER_SELF_TEST.equals(action)) {
            performRenderSelfTest(host);
        } else if (ACTION_TEXT_MULTI_ADD.equals(action)) {
            performTextMultiAdd(host);
        } else if (ACTION_TEXT_MULTI_ALIGN_LEFT.equals(action)) {
            performTextMultiApply(host, org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController.Action.ALIGN_LEFT);
        } else if (ACTION_TEXT_MULTI_DISTRIBUTE_H.equals(action)) {
            performTextMultiApply(host, org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController.Action.DISTRIBUTE_HORIZONTAL);
        } else if (ACTION_TEXT_MULTI_TOGGLE_GROUP.equals(action)) {
            performTextMultiToggleGroup(host);
        } else if (ACTION_TEXT_MULTI_NUDGE.equals(action)) {
            performTextMultiNudge(host);
        }
    }

    @Nullable
    public static Host lastHost() {
        return lastHost;
    }

    private static void performTextMultiAdd(@NonNull Host host) {
        if (!BuildConfig.DEBUG) return;
        org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController ms = host.textMultiSelectOrNull();
        if (ms == null) return;
        boolean ok = ms.addCurrentSelection();
        android.util.Log.d("OpenDroidPDF/Debug", "text-multi-add result=" + ok + " size=" + ms.size());
    }

    private static void performTextMultiApply(@NonNull Host host,
                                              @NonNull org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController.Action action) {
        if (!BuildConfig.DEBUG) return;
        org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController ms = host.textMultiSelectOrNull();
        if (ms == null) return;
        boolean ok = ms.apply(action);
        android.util.Log.d("OpenDroidPDF/Debug", "text-multi-apply " + action + " result=" + ok + " size=" + ms.size());
    }

    private static void performTextMultiToggleGroup(@NonNull Host host) {
        if (!BuildConfig.DEBUG) return;
        org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController ms = host.textMultiSelectOrNull();
        if (ms == null) return;
        boolean ok = ms.toggleGroupForCurrentSelection();
        android.util.Log.d("OpenDroidPDF/Debug", "text-multi-toggle-group result=" + ok + " grouped=" + ms.isGrouped());
    }

    private static void performTextMultiNudge(@NonNull Host host) {
        if (!BuildConfig.DEBUG) return;
        org.opendroidpdf.app.annotation.TextAnnotationMultiSelectController ms = host.textMultiSelectOrNull();
        if (ms == null) return;
        boolean ok = ms.debugTranslateAll(120f, 80f);
        android.util.Log.d("OpenDroidPDF/Debug", "text-multi-nudge result=" + ok + " size=" + ms.size());
    }

    private static void copyUriToFile(@NonNull Context ctx, @NonNull Uri src, @NonNull File dest) throws Exception {
        try (InputStream in = ctx.getContentResolver().openInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IllegalStateException("input stream null for " + src);
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
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
