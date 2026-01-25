package org.opendroidpdf.app.document;

import android.app.Activity;

import androidx.annotation.NonNull;

import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.app.services.DrawingService;
import org.opendroidpdf.app.services.Provider;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight host that commits any pending ink to the MuPDF core so
 * exported/printed PDFs always include drawn strokes.
 */
public final class InkCommitHostAdapter {
    private final Activity activity;
    private final DrawingService drawingService;
    private final Provider<MuPDFReaderView> docViewSupplier;

    public InkCommitHostAdapter(@NonNull Activity activity,
                                @NonNull DrawingService drawingService,
                                @NonNull Provider<MuPDFReaderView> docViewSupplier) {
        this.activity = activity;
        this.drawingService = drawingService;
        this.docViewSupplier = docViewSupplier;
    }

    public void commitPendingInkToCoreBlocking() {
        // Export/save flows call this from background threads; drawing/annotation state is owned by the UI.
        final long totalTimeoutMs = 2000L;
        final long startUptime = android.os.SystemClock.uptimeMillis();

        final AtomicReference<MuPDFPageView> pageRef = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);

        Runnable uiWork = new Runnable() {
            @Override public void run() {
                try {
                    // 1) Commit in-progress ink strokes.
                    drawingService.finalizePendingInk();

                    // 2) Commit any focused inline editors (forms/text annotations) via focus-loss hooks.
                    try {
                        android.view.View focus = activity.getCurrentFocus();
                        if (focus != null) focus.clearFocus();
                        android.view.Window w = activity.getWindow();
                        if (w != null && w.getDecorView() != null) w.getDecorView().clearFocus();
                    } catch (Throwable ignore) {
                    }

                    // 3) Capture the active page view so the caller can await any async annotation jobs.
                    try {
                        MuPDFReaderView docView = docViewSupplier.get();
                        if (docView != null) {
                            android.view.View v = docView.getSelectedView();
                            if (v instanceof MuPDFPageView) {
                                pageRef.set((MuPDFPageView) v);
                            }
                        }
                    } catch (Throwable ignore) {
                    }
                } finally {
                    latch.countDown();
                }
            }
        };

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            uiWork.run();
        } else {
            activity.runOnUiThread(uiWork);
        }

        try {
            long remainingForUi = totalTimeoutMs - (android.os.SystemClock.uptimeMillis() - startUptime);
            if (remainingForUi < 0L) remainingForUi = 0L;
            latch.await(remainingForUi, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // Best-effort: wait for any in-flight annotation jobs to finish before export/save snapshots.
        try {
            long remaining = totalTimeoutMs - (android.os.SystemClock.uptimeMillis() - startUptime);
            if (remaining <= 0L) return;
            MuPDFPageView page = pageRef.get();
            if (page != null) {
                page.awaitPendingAnnotationJobsBlocking(remaining);
            }
        } catch (Throwable ignore) {
        }
    }
}
