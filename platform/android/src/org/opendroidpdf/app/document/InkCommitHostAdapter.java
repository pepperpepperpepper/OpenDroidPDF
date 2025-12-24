package org.opendroidpdf.app.document;

import android.app.Activity;

import androidx.annotation.NonNull;

import org.opendroidpdf.app.services.DrawingService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight host that commits any pending ink to the MuPDF core so
 * exported/printed PDFs always include drawn strokes.
 */
public final class InkCommitHostAdapter {
    private final Activity activity;
    private final DrawingService drawingService;

    public InkCommitHostAdapter(@NonNull Activity activity,
                                @NonNull DrawingService drawingService) {
        this.activity = activity;
        this.drawingService = drawingService;
    }

    public void commitPendingInkToCoreBlocking() {
        // Export/save flows call this from background threads; drawing state is owned by the UI.
        final CountDownLatch latch = new CountDownLatch(1);
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    drawingService.finalizePendingInk();
                } finally {
                    latch.countDown();
                }
            }
        });
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
