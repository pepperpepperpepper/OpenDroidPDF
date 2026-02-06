package org.opendroidpdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.PointF;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

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

import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.preferences.EditorPreferences;
import org.opendroidpdf.app.preferences.EditorPrefsSnapshot;
import org.opendroidpdf.app.preferences.PenPrefsSnapshot;
import org.opendroidpdf.app.reader.ReaderComposition;
import org.opendroidpdf.app.services.Provider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Regression test: Fill & Sign signature placement commits ink via {@link MuPDFPageView#addInkAnnotationFromUi}
 * which must work for sidecar-backed documents (e.g., read-only PDFs). Previously, the method
 * returned false whenever a sidecar session existed, causing placed signatures to disappear.
 */
@RunWith(AndroidJUnit4.class)
public class FillSignSidecarInkCommitInstrumentedTest {

    private final FilePicker.FilePickerSupport noopPicker = picker -> { };

    private File pdfFile;
    private OpenDroidPDFCore core;
    private MuPDFPageView pageView;
    private FrameLayout root;
    private Context context;
    private MuPdfRepository repository;
    private MuPdfController controller;
    private ReaderComposition composition;

    private static final class TestPageView extends MuPDFPageView {
        TestPageView(Context context,
                     FilePicker.FilePickerSupport support,
                     MuPdfController controller,
                     ViewGroup parent,
                     ReaderComposition composition) {
            super(context, support, controller, parent, composition);
        }

        @Override
        public void addHq(boolean update) {
            // Skip HQ patch generation to avoid ReaderView dependency in instrumentation.
        }
    }

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        pdfFile = new File(context.getFilesDir(), "fillsign_sidecar_two_page.pdf");
        copyAsset("two_page_sample.pdf", pdfFile);
        core = new OpenDroidPDFCore(context, Uri.fromFile(pdfFile));
        repository = new MuPdfRepository(core);
        controller = new MuPdfController(repository);

        String docId = "fillsign_sidecar_test_" + SystemClock.uptimeMillis();
        composition = runOnUi(() -> new ReaderComposition(
                context,
                new EditorPreferences(
                        (Provider<PenPrefsSnapshot>) () -> new PenPrefsSnapshot(
                                /*thickness*/ 2.0f,
                                /*colorIndex*/ 0,
                                /*min*/ 0.5f,
                                /*max*/ 10.0f,
                                /*step*/ 0.25f,
                                /*def*/ 2.0f),
                        (Provider<EditorPrefsSnapshot>) () -> new EditorPrefsSnapshot(
                                /*eraserThickness*/ 24.0f,
                                /*smartTextSelectionEnabled*/ true,
                                /*highlightColorIndex*/ 0,
                                /*underlineColorIndex*/ 0,
                                /*strikeoutColorIndex*/ 0,
                                /*textAnnotIconColorIndex*/ 0)),
                controller,
                docId,
                docId,
                DocumentType.PDF,
                /*canSaveToCurrentUri*/ false));

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
            TestPageView view = new TestPageView(context, noopPicker, controller, root, composition);
            root.addView(view);
            return view;
        });
    }

    @After
    public void tearDown() {
        if (core != null) {
            core.onDestroy();
        }
        if (pdfFile != null && pdfFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pdfFile.delete();
        }
        repository = null;
        controller = null;
        context = null;
        pageView = null;
        root = null;
        composition = null;
    }

    @Test
    public void addInkAnnotationFromUiCommitsToSidecarSessionAndUndoWorks() throws Exception {
        SidecarAnnotationSession sidecar = composition.sidecarSession();
        assertNotNull("Sidecar session should exist when canSaveToCurrentUri=false", sidecar);

        int page = 0;
        PointF size = repository.getPageSize(page);
        runOnUi(() -> {
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

        int initialSidecarStrokes = sidecar.inkStrokesForPage(page).size();
        int initialEmbeddedAnnots = repository.loadAnnotations(page).length;

        PointF[][] arcsDoc = new PointF[][]{
                new PointF[]{
                        new PointF(size.x * 0.2f, size.y * 0.25f),
                        new PointF(size.x * 0.8f, size.y * 0.25f)
                }
        };

        boolean committed = runOnUi(() -> pageView.addInkAnnotationFromUi(arcsDoc));
        assertTrue("addInkAnnotationFromUi should commit in sidecar mode", committed);

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(200);

        assertTrue("Sidecar stroke count should increase after commit",
                sidecar.inkStrokesForPage(page).size() > initialSidecarStrokes);
        assertEquals("Embedded annotations should not change when committing to sidecar",
                initialEmbeddedAnnots,
                repository.loadAnnotations(page).length);

        boolean canUndo = runOnUi(() -> pageView.canUndo());
        assertTrue("Undo should be available after committing sidecar ink", canUndo);

        runOnUi(() -> {
            pageView.undoDraw();
            return null;
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(200);

        assertEquals("Sidecar strokes should revert after undo",
                initialSidecarStrokes,
                sidecar.inkStrokesForPage(page).size());
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

