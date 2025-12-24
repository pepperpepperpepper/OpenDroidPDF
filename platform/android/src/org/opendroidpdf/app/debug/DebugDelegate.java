package org.opendroidpdf.app.debug;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.DebugAutotestRunner;

/**
 * Houses debug-only hooks (autotest runner flag + dispatch) to keep them out of the activity body.
 */
public final class DebugDelegate {
    private boolean autoTestRan = false;

    public boolean isAutoTestRan() { return autoTestRan; }
    public void markAutoTestRan() { autoTestRan = true; }

    public void runAutotestIfNeeded(@NonNull DebugAutotestRunner.Host host,
                                    @Nullable Intent intent) {
        DebugAutotestRunner.runIfNeeded(host, intent);
    }
}
