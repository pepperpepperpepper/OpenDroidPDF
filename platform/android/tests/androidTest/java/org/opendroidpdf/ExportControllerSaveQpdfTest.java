package org.opendroidpdf;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.document.ExportController.Host;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.core.MuPdfRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Callable;

/**
 * End-to-end save tests for qpdf-backed linearize/encrypt paths.
 * Uses the test override hook to bypass repository export.
 */
@RunWith(AndroidJUnit4.class)
public class ExportControllerSaveQpdfTest {

    private Context ctx;
    private File cacheDir;
    private ExportController controller;

    @Before
    public void setup() {
        Assume.assumeTrue("qpdf disabled", BuildConfig.ENABLE_QPDF_OPS);
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        cacheDir = ctx.getCacheDir();
        controller = new ExportController(new FakeHost());
    }

    @Test
    public void saveLinearizedCopiesAsset() throws Exception {
        File src = copyAsset("two_page_sample.pdf", new File(cacheDir, "src_linearize.pdf"));
        controller.setExportUriOverrideForTest(uriSupplier(src));

        File dest = new File(cacheDir, "out_linearized.pdf");
        Uri destUri = FileProvider.getUriForFile(ctx, "org.opendroidpdf.fileprovider", dest);
        Intent intent = new Intent().setData(destUri);

        controller.onActivityResultSaveLinearized(Activity.RESULT_OK, intent);

        assertTrue(dest.exists());
        assertTrue(dest.length() > 0);
    }

    @Test
    public void saveEncryptedCopiesAsset() throws Exception {
        File src = copyAsset("two_page_sample.pdf", new File(cacheDir, "src_encrypt.pdf"));
        controller.setExportUriOverrideForTest(uriSupplier(src));
        controller.setPendingEncryptionForTest("userpw", "ownerpw");

        File dest = new File(cacheDir, "out_encrypted.pdf");
        Uri destUri = FileProvider.getUriForFile(ctx, "org.opendroidpdf.fileprovider", dest);
        Intent intent = new Intent().setData(destUri);

        controller.onActivityResultSaveEncrypted(Activity.RESULT_OK, intent);

        assertTrue(dest.exists());
        assertTrue(dest.length() > 0);
    }

    private Callable<Uri> uriSupplier(File file) {
        return () -> FileProvider.getUriForFile(ctx, "org.opendroidpdf.fileprovider", file);
    }

    private File copyAsset(String name, File dest) throws Exception {
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        }
        return dest;
    }

    private class FakeHost implements Host {
        @Override public MuPdfRepository getRepository() { return null; }
        @Override public void commitPendingInkToCoreBlocking() {}
        @Override public void showInfo(String message) {}
        @Override public String currentDocumentName() { return "test.pdf"; }
        @Override public void markIgnoreSaveOnStop() {}
        @Override public Context getContext() { return ctx; }
        @Override public android.content.ContentResolver getContentResolver() { return ctx.getContentResolver(); }
        @Override public void startActivityForResult(Intent intent, int requestCode) {}
        @Override public void invalidateDocumentView() {}
        @Override public void callInBackgroundAndShowDialog(String message, Callable<Exception> background, Callable<Void> success, Callable<Void> failure) {
            try {
                Exception err = background.call();
                if (err == null) {
                    if (success != null) success.call();
                } else if (failure != null) {
                    failure.call();
                }
            } catch (Exception ignored) {}
        }
        @Override public void promptSaveAs() {}
        @Override public SidecarAnnotationProvider sidecarAnnotationProviderOrNull() { return null; }
    }
}
