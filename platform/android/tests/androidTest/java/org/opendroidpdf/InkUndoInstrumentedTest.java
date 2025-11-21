package org.opendroidpdf;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.PointF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class InkUndoInstrumentedTest {

    private File pdfFile;

    @Before
    public void setUp() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        pdfFile = new File(context.getFilesDir(), "ink_undo_test.pdf");
        copyAsset("rtl_sample.pdf", pdfFile);
    }

    @After
    public void tearDown() {
        if (pdfFile != null && pdfFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pdfFile.delete();
        }
    }

    @Test
    public void inkAnnotationReportedAsInkType() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        MuPDFCore core = new MuPDFCore(context, pdfFile.getAbsolutePath());
        try {
            PointF[][] stroke = new PointF[][]{
                    new PointF[]{
                            new PointF(10f, 10f),
                            new PointF(25f, 18f),
                            new PointF(40f, 12f)
                    }
            };

            core.setInkColor(0.1f, 0.2f, 0.7f);
            core.addInkAnnotation(0, stroke);

            Annotation[] annotations = core.getAnnoations(0);
            assertNotNull("Annotations should not be null after adding ink", annotations);
            boolean foundInk = false;
            int inkIndex = -1;
            StringBuilder typeSummary = new StringBuilder();
            for (int i = annotations.length - 1; i >= 0; i--) {
                Annotation annotation = annotations[i];
                if (annotation == null) {
                    continue;
                }
                typeSummary.append(annotation.type).append('#').append(annotation.objectNumber).append(' ');
                if (annotation.type == Annotation.Type.INK) {
                    foundInk = true;
                    inkIndex = i;
                    break;
                }
            }

            assertTrue("Newly added annotation should be reported as INK but saw: " + typeSummary, foundInk);

            int beforeDelete = annotations.length;
            core.deleteAnnotation(0, inkIndex);
            Annotation[] afterDelete = core.getAnnoations(0);
            assertNotNull("Annotations should still return array after deletion", afterDelete);
            String failureMsg = "Ink annotation was not removed; before="
                    + beforeDelete + " after=" + afterDelete.length;
            assertTrue(failureMsg, afterDelete.length < beforeDelete);
        } finally {
            core.onDestroy();
        }
    }

    private void copyAsset(String assetName, File dest) throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context instrumentationContext = InstrumentationRegistry.getInstrumentation().getContext();
        try (InputStream in = instrumentationContext.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }
}
