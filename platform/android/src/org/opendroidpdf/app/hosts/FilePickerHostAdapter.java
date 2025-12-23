package org.opendroidpdf.app.hosts;

import android.content.Intent;

import androidx.annotation.NonNull;

import org.opendroidpdf.FilePicker;
import org.opendroidpdf.FilePickerCoordinator;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFFileChooser;
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
        Intent intent = new Intent(activity, OpenDroidPDFFileChooser.class);
        coordinator.setPendingPicker(picker);
        activity.startActivityForResult(intent, RequestCodes.FILE_PICK);
    }
}
