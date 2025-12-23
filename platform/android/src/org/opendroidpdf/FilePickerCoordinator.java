package org.opendroidpdf;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Owns the transient pending {@link FilePicker} and delivers the resulting Uri back
 * to the caller from {@code onActivityResult}.
 *
 * <p>This lives in the {@code org.opendroidpdf} package so it can call the
 * package-private {@link FilePicker#onPick(Uri)} callback without widening that API.</p>
 */
public final class FilePickerCoordinator {
    private static final String TAG = "FilePickerCoordinator";

    @Nullable private FilePicker pendingPicker;

    public void setPendingPicker(@Nullable FilePicker picker) {
        pendingPicker = picker;
    }

    public boolean handleActivityResult(int resultCode, @Nullable Intent intent) {
        FilePicker picker = pendingPicker;
        pendingPicker = null;
        if (picker == null) return false;

        if (resultCode != Activity.RESULT_OK) return true;

        Uri uri = intent != null ? intent.getData() : null;
        if (uri == null) return true;

        try {
            picker.onPick(uri);
        } catch (Throwable t) {
            Log.w(TAG, "Failed delivering picked uri", t);
        }
        return true;
    }
}

