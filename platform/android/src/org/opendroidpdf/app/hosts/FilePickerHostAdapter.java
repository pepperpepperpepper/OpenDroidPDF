package org.opendroidpdf.app.hosts;

import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import org.opendroidpdf.FilePicker;
import org.opendroidpdf.FilePickerCoordinator;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFFileChooser;
import org.opendroidpdf.app.document.DocumentAccessIntents;
import org.opendroidpdf.app.helpers.RequestCodes;

/**
 * Keeps FilePickerSupport out of the activity class so the host surface stays small.
 */
public final class FilePickerHostAdapter implements FilePicker.FilePickerSupport {
    private final OpenDroidPDFActivity activity;
    private final FilePickerCoordinator coordinator;

    public FilePickerHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                 @NonNull FilePickerCoordinator coordinator) {
        this.activity = activity;
        this.coordinator = coordinator;
    }

    @Override
    public void performPickFor(FilePicker picker) {
        coordinator.setPendingPicker(picker);
        // Key-file selection is only used for signing PDF signature widgets. On modern Android,
        // scoped storage makes filesystem-based pickers unreliable; prefer SAF.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activity.startActivityForResult(DocumentAccessIntents.newOpenPkcs12Intent(), RequestCodes.FILE_PICK);
            return;
        }
        activity.startActivityForResult(new Intent(activity, OpenDroidPDFFileChooser.class), RequestCodes.FILE_PICK);
    }
}
