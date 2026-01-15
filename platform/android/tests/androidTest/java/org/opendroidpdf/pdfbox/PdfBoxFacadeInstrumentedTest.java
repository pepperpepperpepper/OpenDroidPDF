package org.opendroidpdf.pdfbox;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.core.PdfBoxFacade;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class PdfBoxFacadeInstrumentedTest {

    @Test
    public void flatten_twoPageSample_succeeds_whenModulePresent() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        Context testCtx = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue("pdfbox module not bundled", BuildConfig.ENABLE_PDFBOX_OPS && PdfBoxFacade.isAvailable());

        File inFile = new File(ctx.getCacheDir(), "pdfbox_in.pdf");
        File outFile = new File(ctx.getCacheDir(), "pdfbox_out.pdf");
        try (InputStream in = testCtx.getAssets().open("two_page_sample.pdf");
             FileOutputStream out = new FileOutputStream(inFile)) {
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        }
        if (outFile.exists()) outFile.delete();
        Uri inUri = Uri.fromFile(inFile);
        Uri outUri = Uri.fromFile(outFile);

        PdfBoxFacade.FlattenResult res = PdfBoxFacade.flattenForm(ctx, inUri, outUri);
        assertTrue("flatten produced output", outFile.exists() && outFile.length() > 0);
        assertTrue("page count preserved", res.pageCount == 2);
    }
}
