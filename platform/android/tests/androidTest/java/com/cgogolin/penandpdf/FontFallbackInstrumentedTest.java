package com.cgogolin.penandpdf;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class FontFallbackInstrumentedTest {

    private static final String TAG = "FontFallbackTest";
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() {
        context = null;
    }

    @Test
    public void cjkPdfRendersWithSystemFonts() throws Exception {
        File pdf = copyAssetToCache("cjk_sample.pdf");
        assertPdfRendersWithInk(pdf);
    }

    @Test
    public void rtlPdfRendersWithSystemFonts() throws Exception {
        File pdf = copyAssetToCache("rtl_sample.pdf");
        assertPdfRendersWithInk(pdf);
    }

    private void assertPdfRendersWithInk(File file) throws Exception {
        MuPDFCore core = null;
        MuPDFCore.Cookie cookie = null;
        Bitmap bitmap = null;
        try {
            core = new MuPDFCore(context, file.getAbsolutePath());
            assertTrue("Expected at least one page in " + file, core.countPages() > 0);

            PointF size = core.getPageSize(0);
            int width = Math.max(1, Math.round(size.x));
            int height = Math.max(1, Math.round(size.y));

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            cookie = core.new Cookie();
            core.drawPage(bitmap, 0, width, height, 0, 0, width, height, cookie);

            assertBitmapHasGlyphs(bitmap);
        } finally {
            if (cookie != null) {
                cookie.destroy();
            }
            if (bitmap != null) {
                bitmap.recycle();
            }
            if (core != null) {
                core.onDestroy();
            }
        }
    }

    private void assertBitmapHasGlyphs(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int dominantColor = mostCommonColor(pixels);
        int opaqueInkPixels = 0;
        for (int color : pixels) {
            int alpha = (color >>> 24) & 0xFF;
            if (alpha == 0) {
                continue;
            }
            if (color != dominantColor) {
                opaqueInkPixels++;
            }
        }

        float ratio = opaqueInkPixels / (float) pixels.length;
        Log.i(TAG, "ink ratio=" + ratio);
        assertTrue(
                "Rendered page is blank (ink ratio=" + ratio + ")",
                ratio > 0.0001f
        );
    }

    private static int mostCommonColor(int[] pixels) {
        Map<Integer, Integer> counts = new HashMap<>();
        int maxColor = 0;
        int maxCount = 0;
        for (int color : pixels) {
            int alpha = (color >>> 24) & 0xFF;
            if (alpha == 0) {
                continue;
            }
            int count = counts.merge(color, 1, Integer::sum);
            if (count > maxCount) {
                maxCount = count;
                maxColor = color;
            }
        }
        return maxColor;
    }

    private File copyAssetToCache(String assetName) throws IOException {
        File outFile = new File(context.getCacheDir(), assetName);
        Context instrumentationContext = InstrumentationRegistry.getInstrumentation().getContext();
        try (InputStream input = instrumentationContext.getAssets().open(assetName);
             FileOutputStream output = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return outFile;
    }
}
