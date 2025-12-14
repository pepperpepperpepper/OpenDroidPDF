package org.opendroidpdf.app.document;

import androidx.annotation.NonNull;

import org.opendroidpdf.InkCommitHelper;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Lightweight host that commits any pending ink to the MuPDF core so
 * exported/printed PDFs always include drawn strokes.
 */
public final class InkCommitHostAdapter {
    private final OpenDroidPDFActivity activity;

    public InkCommitHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    public void commitPendingInkToCoreBlocking() {
        InkCommitHelper.commitPendingInkToCoreBlocking(new InkCommitHelper.Host() {
            @Override public @NonNull MuPdfRepository getRepository() { return activity.getRepository(); }
            @Override public @NonNull MuPDFReaderView getDocView() { return activity.getDocView(); }
            @Override public void runOnUiThread(@NonNull Runnable r) { activity.runOnUiThread(r); }
            @Override public void invalidateOptionsMenu() { activity.invalidateOptionsMenuSafely(); }
        });
    }
}
