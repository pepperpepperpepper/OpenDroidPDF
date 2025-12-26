package org.opendroidpdf;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendroidpdf.app.debug.DebugActionsController;
import org.opendroidpdf.app.hosts.DebugActionsHostAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Instrumentation coverage for the debug broadcast hooks so we can exercise
 * export/snap logic without multi-touch input.
 */
@RunWith(AndroidJUnit4.class)
public class DebugBroadcastInstrumentationTest {

    @Test
    public void exportBroadcastCreatesFileInCache() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();

        // Prepare a source PDF under cache/tmpfiles so FileProvider can serve it.
        File tmpRoot = new File(ctx.getCacheDir(), "tmpfiles");
        deleteRecursively(tmpRoot);
        tmpRoot.mkdirs();
        File srcDir = new File(tmpRoot, "itest_src");
        srcDir.mkdirs();
        File srcPdf = new File(srcDir, "broadcast.pdf");
        copyAsset(ctx, "two_page_sample.pdf", srcPdf);

        Uri srcUri = FileProvider.getUriForFile(
                ctx, "org.opendroidpdf.fileprovider", srcPdf);

        Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(srcUri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        // Launch the activity with a real document to ensure repository/core exist.
        try (ActivityScenario<OpenDroidPDFActivity> scenario = ActivityScenario.launch(view)) {
            waitForCoreReady(scenario);

            final android.net.Uri[] exportedUri = {null};
            scenario.onActivity(a -> exportedUri[0] = DebugActionsController.runExportTest(new DebugActionsHostAdapter(a)));

            // Allow export to run on main thread + IO.
            Thread.sleep(2000);

            assertNotNull("Export URI should not be null", exportedUri[0]);
        }
    }

    private static void waitForCoreReady(ActivityScenario<OpenDroidPDFActivity> scenario) throws Exception {
        final long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            final boolean[] ready = {false};
            scenario.onActivity(a -> ready[0] = a.hasCore() && a.getDocView() != null);
            if (ready[0]) return;
            Thread.sleep(200);
        }
        throw new AssertionError("Core/DocView not ready after timeout");
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) deleteRecursively(k);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static void copyAsset(Context ctx, String assetName, File dest) throws Exception {
        dest.getParentFile().mkdirs();
        Context instrumentationCtx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext();
        try (InputStream in = instrumentationCtx.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
