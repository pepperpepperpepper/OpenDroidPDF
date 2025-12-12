package org.opendroidpdf;

import android.graphics.PointF;

import androidx.annotation.NonNull;

import org.opendroidpdf.core.MuPdfRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Flushes any in-progress ink from the overlay into MuPDF and refreshes appearances.
 */
public final class InkCommitHelper {
    private InkCommitHelper() {}

    public interface Host {
        @NonNull MuPdfRepository getRepository();
        @NonNull MuPDFReaderView getDocView();
        void runOnUiThread(@NonNull Runnable r);
        void invalidateOptionsMenu(); // host should guard against reentrancy
    }

    public static void commitPendingInkToCoreBlocking(@NonNull final Host host) {
        final MuPdfRepository repo = host.getRepository();
        final MuPDFReaderView doc = host.getDocView();
        if (repo == null || doc == null) return;

        final AtomicReference<PointF[][]> arcsRef = new AtomicReference<>(null);
        final AtomicInteger pageIndexRef = new AtomicInteger(-1);
        final AtomicReference<MuPDFPageView> pvRef = new AtomicReference<>(null);
        final CountDownLatch latch = new CountDownLatch(1);

        host.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    MuPDFView v = (MuPDFView) doc.getSelectedView();
                    if (v instanceof MuPDFPageView) {
                        MuPDFPageView pv = (MuPDFPageView) v;
                        pvRef.set(pv);
                        PointF[][] arcs = pv.getDraw();
                        if (arcs != null && arcs.length > 0) {
                            arcsRef.set(arcs);
                            pageIndexRef.set(doc.getSelectedItemPosition());
                            pv.cancelDraw();
                        }
                    }
                } catch (Throwable ignore) {
                } finally {
                    latch.countDown();
                }
            }
        });

        try { latch.await(500, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {}

        PointF[][] arcs = arcsRef.get();
        final int pageIndex = pageIndexRef.get();
        if (pageIndex >= 0) {
            if (arcs != null) {
                try {
                    repo.addInkAnnotation(pageIndex, arcs);
                    repo.markDocumentDirty();
                    final PointF[][] arcsForUndo = arcs;
                    host.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            MuPDFPageView pv = pvRef.get();
                            if (pv != null) {
                                pv.recordCommittedInkForUndo(arcsForUndo);
                                pv.loadAnnotations();
                            }
                            host.invalidateOptionsMenu();
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
            try { repo.refreshAnnotationAppearance(pageIndex); } catch (Throwable ignore) {}
        }

        MuPDFPageView pv = pvRef.get();
        if (pv != null) {
            try { pv.awaitInkCommit(1000); } catch (Throwable ignore) {}
        }
    }
}
