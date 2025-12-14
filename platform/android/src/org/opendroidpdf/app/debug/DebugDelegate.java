package org.opendroidpdf.app.debug;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;

/**
 * Houses debug-only hooks (autotest runner flag + dispatch) to keep them out of the activity body.
 */
public final class DebugDelegate {
    private boolean autoTestRan = false;

    public boolean isAutoTestRan() { return autoTestRan; }
    public void markAutoTestRan() { autoTestRan = true; }

    public void runAutotestIfNeeded(@NonNull OpenDroidPDFActivity activity,
                                    @Nullable MuPDFReaderView docView,
                                    @Nullable org.opendroidpdf.core.MuPdfRepository repo,
                                    @Nullable Intent intent) {
        if (docView == null || repo == null) return;
        org.opendroidpdf.DebugAutotestRunner.runIfNeeded(
                new org.opendroidpdf.app.hosts.DebugAutotestHostAdapter(activity, repo, docView),
                intent);
    }
}
