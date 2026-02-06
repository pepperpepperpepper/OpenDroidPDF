package org.opendroidpdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.overlay.ItemSelectionHandles;
import org.opendroidpdf.app.preferences.EditorPreferences;
import org.opendroidpdf.app.preferences.EditorPrefsSnapshot;
import org.opendroidpdf.app.preferences.PenPrefsSnapshot;
import org.opendroidpdf.app.reader.ReaderComposition;
import org.opendroidpdf.app.reader.gesture.InkAnnotationManipulationGestureHandler;
import org.opendroidpdf.app.services.Provider;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class InkSignatureManipulationInstrumentedTest {

    private final FilePicker.FilePickerSupport noopPicker = picker -> { };

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

    private static final class Setup {
        File pdfFile;
        OpenDroidPDFCore core;
        MuPdfRepository repository;
        MuPdfController controller;
        ReaderComposition composition;
        FrameLayout root;
        TestPageView pageView;
        Context context;
    }

    @Test
    public void sidecarInk_canSelectResizeAndDelete() throws Exception {
        Setup s = createSetup(/*canSaveToCurrentUri*/ false);
        try {
            SidecarAnnotationSession sidecar = s.composition.sidecarSession();
            assertNotNull("Sidecar session should exist when canSaveToCurrentUri=false", sidecar);

            final int page = 0;
            preparePageView(s, page);

            PointF size = s.repository.getPageSize(page);
            PointF[][] arcsDoc = new PointF[][]{
                    new PointF[]{
                            new PointF(size.x * 0.25f, size.y * 0.25f),
                            new PointF(size.x * 0.75f, size.y * 0.25f)
                    },
                    new PointF[]{
                            new PointF(size.x * 0.25f, size.y * 0.35f),
                            new PointF(size.x * 0.75f, size.y * 0.35f)
                    }
            };

            int initialStrokes = sidecar.inkStrokesForPage(page).size();
            boolean committed = runOnUi(() -> s.pageView.addInkAnnotationFromUi(arcsDoc));
            assertTrue("Expected sidecar ink commit to succeed", committed);

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(150);

            assertTrue("Sidecar stroke count should increase after commit",
                    sidecar.inkStrokesForPage(page).size() > initialStrokes);

            // Tap inside the expected bounds to select the ink group.
            float scale = runOnUi(() -> s.pageView.getScale());
            float tapDocX = size.x * 0.5f;
            float tapDocY = size.y * 0.30f;
            float tapX = (tapDocX * scale);
            float tapY = (tapDocY * scale);
            long t0 = SystemClock.uptimeMillis();
            MotionEvent tap = MotionEvent.obtain(t0, t0 + 40, MotionEvent.ACTION_UP, tapX, tapY, 0);
            runOnUi(() -> {
                s.pageView.passClickEvent(tap);
                return null;
            });
            tap.recycle();

            SidecarSelectionController.Selection sel = runOnUi(() -> s.pageView.selectedSidecarSelectionOrNull());
            assertNotNull("Expected tap to select sidecar ink", sel);
            assertEquals("Expected sidecar selection kind INK", SidecarSelectionController.Kind.INK, sel.kind);

            RectF startBox = runOnUi(() -> s.pageView.getItemSelectBox());
            assertNotNull("Expected selection box after selecting ink", startBox);

            InkAnnotationManipulationGestureHandler handler =
                    new InkAnnotationManipulationGestureHandler(s.context.getResources(), () -> s.pageView);

            // Resize via the top-left handle: start outside the box but inside the handle rect.
            float cornerHalfDoc = ItemSelectionHandles.cornerHandleHalfPx(s.context.getResources()) / scale;
            float startDocX = startBox.left - (cornerHalfDoc * 0.5f);
            float startDocY = startBox.top - (cornerHalfDoc * 0.5f);
            float endDocX = startDocX - 25f;
            float endDocY = startDocY - 25f;

            MotionEvent down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, startDocX * scale, startDocY * scale, 0);
            MotionEvent move = MotionEvent.obtain(t0, t0 + 16, MotionEvent.ACTION_MOVE, endDocX * scale, endDocY * scale, 0);
            MotionEvent up = MotionEvent.obtain(t0, t0 + 32, MotionEvent.ACTION_UP, endDocX * scale, endDocY * scale, 0);

            RectF expectedAfterDrag = runOnUi(() -> {
                handler.onTouchEvent(down);
                handler.onScroll(down, move);
                RectF next = s.pageView.getItemSelectBox();
                handler.onTouchEvent(up);
                return next;
            });
            down.recycle();
            move.recycle();
            up.recycle();

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(150);

            RectF boundsAfterCommit = boundsForSidecarInkCreatedAt(sidecar, page, sel.createdAtEpochMs);
            assertNotNull("Expected sidecar ink bounds after resize", boundsAfterCommit);
            assertRectApproxEquals("Expected sidecar ink group bounds to match resized selection box",
                    expectedAfterDrag,
                    boundsAfterCommit,
                    0.75f);

            int strokesBeforeDelete = sidecar.inkStrokesForPage(page).size();
            runOnUi(() -> {
                s.pageView.deleteSelectedAnnotation();
                return null;
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(150);
            assertTrue("Expected deleteSelectedAnnotation to remove sidecar ink strokes",
                    sidecar.inkStrokesForPage(page).size() < strokesBeforeDelete);
        } finally {
            destroySetup(s);
        }
    }

    @Test
    public void embeddedInk_canUndoResizeAndDelete() throws Exception {
        Setup s = createSetup(/*canSaveToCurrentUri*/ true);
        try {
            assertNull("Sidecar session should be null when canSaveToCurrentUri=true", s.composition.sidecarSession());

            final int page = 0;
            preparePageView(s, page);

            int initialAnnots = s.repository.loadAnnotations(page).length;

            PointF size = s.repository.getPageSize(page);
            PointF[][] arcsDoc = new PointF[][]{
                    new PointF[]{
                            new PointF(size.x * 0.25f, size.y * 0.25f),
                            new PointF(size.x * 0.75f, size.y * 0.25f)
                    },
                    new PointF[]{
                            new PointF(size.x * 0.25f, size.y * 0.35f),
                            new PointF(size.x * 0.75f, size.y * 0.35f)
                    }
            };

            boolean committed = runOnUi(() -> s.pageView.addInkAnnotationFromUi(arcsDoc));
            assertTrue("Expected embedded ink commit to succeed", committed);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(150);
            assertTrue("Annotation count should increase after committing ink",
                    s.repository.loadAnnotations(page).length > initialAnnots);

            boolean canUndo = runOnUi(() -> s.pageView.canUndo());
            assertTrue("Undo should be available after committing embedded ink", canUndo);

            runOnUi(() -> {
                s.pageView.undoDraw();
                return null;
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(150);
            assertEquals("Undo should remove the committed ink annotation",
                    initialAnnots,
                    s.repository.loadAnnotations(page).length);

            // Re-add for resize/delete verification.
            runOnUi(() -> s.pageView.addInkAnnotationFromUi(arcsDoc));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(150);

            // Select the embedded ink annotation by object id.
            Annotation targetInk = null;
            int targetIndex = -1;
            Annotation[] annots = s.controller.annotations(page);
            for (int i = annots.length - 1; i >= 0; i--) {
                Annotation a = annots[i];
                if (a != null && a.type == Annotation.Type.INK && a.objectNumber > 0L) {
                    targetInk = a;
                    targetIndex = i;
                    break;
                }
            }
            assertNotNull("Expected to find embedded INK annotation after commit", targetInk);
            assertTrue("Expected valid ink annotation index", targetIndex >= 0);

            final Annotation ink = targetInk;
            final int inkIndex = targetIndex;
            runOnUi(() -> {
                s.composition.selectionManager().select(
                        inkIndex,
                        ink.objectNumber,
                        new RectF(ink),
                        rect -> s.pageView.setSelectionBox(rect));
                return null;
            });

            RectF startBox = runOnUi(() -> s.pageView.getItemSelectBox());
            assertNotNull("Expected selection box after selecting embedded ink", startBox);

            InkAnnotationManipulationGestureHandler handler =
                    new InkAnnotationManipulationGestureHandler(s.context.getResources(), () -> s.pageView);

            float scale = runOnUi(() -> s.pageView.getScale());
            float cornerHalfDoc = ItemSelectionHandles.cornerHandleHalfPx(s.context.getResources()) / scale;
            float startDocX = startBox.left - (cornerHalfDoc * 0.5f);
            float startDocY = startBox.top - (cornerHalfDoc * 0.5f);
            float endDocX = startDocX - 25f;
            float endDocY = startDocY - 25f;

            long t0 = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, startDocX * scale, startDocY * scale, 0);
            MotionEvent move = MotionEvent.obtain(t0, t0 + 16, MotionEvent.ACTION_MOVE, endDocX * scale, endDocY * scale, 0);
            MotionEvent up = MotionEvent.obtain(t0, t0 + 32, MotionEvent.ACTION_UP, endDocX * scale, endDocY * scale, 0);

            RectF expectedAfterDrag = runOnUi(() -> {
                handler.onTouchEvent(down);
                handler.onScroll(down, move);
                RectF next = s.pageView.getItemSelectBox();
                handler.onTouchEvent(up);
                return next;
            });
            down.recycle();
            move.recycle();
            up.recycle();

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(250);

            RectF embeddedInkBounds = boundsForNewestEmbeddedInk(s.controller, page);
            assertNotNull("Expected ink bounds after replacement commit", embeddedInkBounds);
            assertRectApproxEquals("Expected embedded ink bounds to roughly match resized selection box",
                    expectedAfterDrag,
                    embeddedInkBounds,
                    5.0f);

            // Delete the newly created ink annotation.
            Annotation[] afterResize = s.controller.annotations(page);
            Annotation newest = null;
            int newestIndex = -1;
            for (int i = afterResize.length - 1; i >= 0; i--) {
                Annotation a = afterResize[i];
                if (a != null && a.type == Annotation.Type.INK && a.objectNumber > 0L) {
                    newest = a;
                    newestIndex = i;
                    break;
                }
            }
            assertNotNull("Expected an INK annotation after resize", newest);
            final Annotation newestInk = newest;
            final int newestInkIndex = newestIndex;

            runOnUi(() -> {
                s.composition.selectionManager().select(
                        newestInkIndex,
                        newestInk.objectNumber,
                        new RectF(newestInk),
                        rect -> s.pageView.setSelectionBox(rect));
                s.pageView.deleteSelectedAnnotation();
                return null;
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(250);

            assertEquals("Expected deleteSelectedAnnotation to remove embedded ink annotation",
                    initialAnnots,
                    s.repository.loadAnnotations(page).length);
        } finally {
            destroySetup(s);
        }
    }

    private Setup createSetup(boolean canSaveToCurrentUri) throws Exception {
        Setup s = new Setup();
        s.context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        s.pdfFile = new File(s.context.getFilesDir(), "ink_sig_" + SystemClock.uptimeMillis() + ".pdf");
        copyAsset("two_page_sample.pdf", s.pdfFile);
        s.core = new OpenDroidPDFCore(s.context, Uri.fromFile(s.pdfFile));
        s.repository = new MuPdfRepository(s.core);
        s.controller = new MuPdfController(s.repository);

        String docId = "ink_sig_test_" + SystemClock.uptimeMillis();
        s.composition = runOnUi(() -> new ReaderComposition(
                s.context,
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
                s.controller,
                docId,
                docId,
                DocumentType.PDF,
                canSaveToCurrentUri));

        s.root = runOnUi(() -> new FrameLayout(s.context));
        final int layoutWidth = 1600;
        final int layoutHeight = 2200;
        runOnUi(() -> {
            int widthSpec = View.MeasureSpec.makeMeasureSpec(layoutWidth, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(layoutHeight, View.MeasureSpec.EXACTLY);
            s.root.measure(widthSpec, heightSpec);
            s.root.layout(0, 0, layoutWidth, layoutHeight);
            return null;
        });

        s.pageView = runOnUi(() -> {
            TestPageView view = new TestPageView(s.context, noopPicker, s.controller, s.root, s.composition);
            s.root.addView(view);
            return view;
        });

        return s;
    }

    private void preparePageView(@NonNull Setup s, int page) throws Exception {
        PointF size = s.repository.getPageSize(page);
        runOnUi(() -> {
            s.pageView.setPage(page, new PointF(size.x, size.y));
            return null;
        });
        runOnUi(() -> {
            int widthSpec = View.MeasureSpec.makeMeasureSpec(s.root.getMeasuredWidth(), View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(s.root.getMeasuredHeight(), View.MeasureSpec.EXACTLY);
            s.pageView.measure(widthSpec, heightSpec);
            s.pageView.layout(0, 0, s.root.getMeasuredWidth(), s.root.getMeasuredHeight());
            return null;
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(150);
    }

    private void destroySetup(@NonNull Setup s) {
        try {
            if (s.core != null) s.core.onDestroy();
        } catch (Throwable ignore) {
        }
        try {
            if (s.pdfFile != null && s.pdfFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                s.pdfFile.delete();
            }
        } catch (Throwable ignore) {
        }
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

    private static void assertRectApproxEquals(@NonNull String message, @NonNull RectF expected, @NonNull RectF actual, float eps) {
        boolean ok = Math.abs(expected.left - actual.left) <= eps
                && Math.abs(expected.top - actual.top) <= eps
                && Math.abs(expected.right - actual.right) <= eps
                && Math.abs(expected.bottom - actual.bottom) <= eps;
        assertTrue(message + " expected=" + expected + " actual=" + actual + " eps=" + eps, ok);
    }

    @Nullable
    private static RectF boundsForNewestEmbeddedInk(@NonNull MuPdfController controller, int page) {
        try {
            Annotation[] annots = controller.annotations(page);
            if (annots == null || annots.length == 0) return null;
            for (int i = annots.length - 1; i >= 0; i--) {
                Annotation a = annots[i];
                if (a != null && a.type == Annotation.Type.INK) return new RectF(a);
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    @Nullable
    private static RectF boundsForSidecarInkCreatedAt(@NonNull SidecarAnnotationSession sidecar, int page, long createdAtEpochMs) {
        try {
            java.util.List<org.opendroidpdf.app.sidecar.model.SidecarInkStroke> strokes = sidecar.inkStrokesForPage(page);
            if (strokes == null || strokes.isEmpty()) return null;
            RectF union = null;
            for (org.opendroidpdf.app.sidecar.model.SidecarInkStroke s : strokes) {
                if (s == null || s.points == null || s.points.length < 2) continue;
                if (s.createdAtEpochMs != createdAtEpochMs) continue;
                RectF b = boundsForPointsOrNull(s.points);
                if (b == null) continue;
                if (union == null) union = new RectF(b);
                else union.union(b);
            }
            return union;
        } catch (Throwable ignore) {
            return null;
        }
    }

    @Nullable
    private static RectF boundsForPointsOrNull(@NonNull PointF[] points) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (PointF p : points) {
            if (p == null) continue;
            if (!Float.isFinite(p.x) || !Float.isFinite(p.y)) continue;
            if (p.x < minX) minX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) return null;
        if (maxX <= minX) maxX = minX + 0.001f;
        if (maxY <= minY) maxY = minY + 0.001f;
        return new RectF(minX, minY, maxX, maxY);
    }
}
