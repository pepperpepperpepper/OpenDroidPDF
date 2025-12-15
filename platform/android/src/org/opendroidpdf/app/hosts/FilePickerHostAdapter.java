package org.opendroidpdf.app.hosts;

import android.content.Intent;

import androidx.annotation.NonNull;

import org.opendroidpdf.FilePicker;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFFileChooser;

/**
 * Keeps FilePickerSupport out of the activity class so the host surface stays small.
 */
public final class FilePickerHostAdapter implements FilePicker.FilePickerSupport {
    private final OpenDroidPDFActivity activity;

    public FilePickerHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override
    public void performPickFor(FilePicker picker) {
        Intent intent = new Intent(activity, OpenDroidPDFFileChooser.class);
        activity.startActivityForResult(intent, activity.getFilePickRequestCode());
        activity.setPendingFilePicker(picker);
    }
}
