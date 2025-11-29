package org.opendroidpdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.PointF;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.FrameLayout;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UndoWorkflowInstrumentedTest {

    private final FilePicker.FilePickerSupport noopPicker = picker -> { };

    private File pdfFile;
    private OpenDroidPDFCore core;
    private MuPDFPageView pageView;
    private FrameLayout root;
    private Context context;

    private static final class TestPageView extends MuPDFPageView {
        TestPageView(Context context, FilePicker.FilePickerSupport support, MuPDFCore core, ViewGroup parent) {
            super(context, support, core, parent);
        }

        @Override
        public void addHq(boolean update) {
            // Skip HQ patch generation to avoid ReaderView dependency in instrumentation.
        }
    }

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        pdfFile = new File(context.getFilesDir(), "undo_workflow_two_page.pdf");
        copyAsset("two_page_sample.pdf", pdfFile);
        core = new OpenDroidPDFCore(context, Uri.fromFile(pdfFile));
        root = runOnUi(() -> new FrameLayout(context));
        final int layoutWidth = 1600;
        final int layoutHeight = 2200;
        runOnUi(() -> {
            int widthSpec = View.MeasureSpec.makeMeasureSpec(layoutWidth, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(layoutHeight, View.MeasureSpec.EXACTLY);
            root.measure(widthSpec, heightSpec);
            root.layout(0, 0, layoutWidth, layoutHeight);
            return null;
        });
        pageView = runOnUi(() -> {
            TestPageView view = new TestPageView(context, noopPicker, core, root);
            root.addView(view);
            return view;
        });
    }

    @After
    public void tearDown() throws Exception {
        if (core != null) {
            core.onDestroy();
        }
        if (pdfFile != null && pdfFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pdfFile.delete();
        }
        context = null;
        pageView = null;
        root = null;
    }

    @Test
    public void committedInkUndoRemovesAnnotationAndExportStaysClean() throws Exception {
        int pageCount = core.countPages();
        assertTrue("Test document must have at least one page", pageCount >= 1);

        exerciseUndoOnPage(0);
        if (pageCount > 1) {
            exerciseUndoOnPage(pageCount - 1);
        }

        // After undo there should be no committed annotations on the tested pages.
        assertEquals("Annotations should be cleared on page 0 after undo", 0, annotationCount(0));
        if (pageCount > 1) {
            assertEquals("Annotations should be cleared on last page after undo", 0, annotationCount(pageCount - 1));
        }

        // Export the document and ensure no residual annotations are present.
        Uri exported = core.export(context);
        assertNotNull("Export should return a Uri", exported);

        OpenDroidPDFCore exportedCore = new OpenDroidPDFCore(context, exported);
        try {
            assertEquals("Exported page 0 should not contain ink annotations", 0, annotationCount(exportedCore, 0));
            if (pageCount > 1) {
                assertEquals("Exported last page should not contain ink annotations", 0, annotationCount(exportedCore, pageCount - 1));
            }
        } finally {
            exportedCore.onDestroy();
            try {
                context.getContentResolver().delete(exported, null, null);
            } catch (Exception ignore) {
                // Ignore clean-up failures; cache entries will be pruned later.
            }
        }
    }

    private void exerciseUndoOnPage(int page) throws Exception {
        PointF size = core.getPageSize(page);
        assertTrue("Page width must be positive", size.x > 0f);
        assertTrue("Page height must be positive", size.y > 0f);
        runOnUi(() -> {
            // setPage also clears the committed-ink stack which mimics user navigation.
            pageView.setPage(page, new PointF(size.x, size.y));
            return null;
        });
        runOnUi(() -> {
            int widthSpec = View.MeasureSpec.makeMeasureSpec(root.getMeasuredWidth(), View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(root.getMeasuredHeight(), View.MeasureSpec.EXACTLY);
            pageView.measure(widthSpec, heightSpec);
            pageView.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
            return null;
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(200);

        int initialCount = annotationCount(page);

        PointF[] strokePoints = buildRealisticStroke(page);
        boolean saved = runOnUi(() -> {
            pageView.startDraw(strokePoints[0].x, strokePoints[0].y);
            for (int i = 1; i < strokePoints.length; i++) {
                pageView.continueDraw(strokePoints[i].x, strokePoints[i].y);
            }
            pageView.finishDraw();
            return pageView.saveDraw();
        });
        assertTrue("saveDraw must succeed on page " + page, saved);

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(200);

        int afterAdd = annotationCount(page);
        assertTrue("Annotation count should increase after draw on page " + page, afterAdd > initialCount);

        boolean canUndo = runOnUi(() -> pageView.canUndo());
        assertTrue("Undo should be available after committing ink on page " + page, canUndo);

        runOnUi(() -> {
            pageView.undoDraw();
            return null;
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(200);

        int afterUndo = annotationCount(page);
        assertEquals("Undo should remove committed annotation on page " + page, initialCount, afterUndo);

        boolean canUndoAfter = runOnUi(() -> pageView.canUndo());
        assertFalse("Undo stack should be empty after undo on page " + page, canUndoAfter);
    }

    private PointF[] buildRealisticStroke(int page) throws Exception {
        float width = runOnUi(() -> (float) pageView.getWidth());
        float height = runOnUi(() -> (float) pageView.getHeight());
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("PageView not laid out before stroke generation");
        }
        final int pointCount = 120;
        PointF[] points = new PointF[pointCount];
        float left = width * 0.2f;
        float right = width * 0.8f;
        float top = height * 0.3f;
        float amplitude = height * 0.15f;
        for (int i = 0; i < pointCount; i++) {
            float t = (float) i / (pointCount - 1);
            float x = left + (right - left) * t;
            float y = top + (float) Math.sin((page + 1) * Math.PI * t * 3.5f) * amplitude;
            points[i] = new PointF(x, y + height * 0.1f * page);
        }
        return points;
    }

    private int annotationCount(int page) {
        return annotationCount(core, page);
    }

    private int annotationCount(MuPDFCore targetCore, int page) {
        Annotation[] annotations = targetCore.getAnnoations(page);
        return annotations == null ? 0 : annotations.length;
    }

    private void copyAsset(String assetName, File dest) throws IOException {
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

    private <T> T runOnUi(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
        try {
            return task.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
