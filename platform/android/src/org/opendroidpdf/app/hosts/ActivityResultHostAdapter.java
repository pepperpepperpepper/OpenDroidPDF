package org.opendroidpdf.app.hosts;

import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import org.opendroidpdf.FilePickerCoordinator;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.document.OrganizePagesController;
import org.opendroidpdf.app.helpers.ActivityResultRouter;
import org.opendroidpdf.app.helpers.RequestCodes;
import org.opendroidpdf.app.document.DocumentNavigationController;

public class ActivityResultHostAdapter implements ActivityResultRouter.Host {
    private final OpenDroidPDFActivity activity;
    private final DocumentNavigationController documentNavigationController;
    private final FilePickerCoordinator filePickerCoordinator;
    private final ExportController exportController;
    private final OrganizePagesController organizePagesController;

    public ActivityResultHostAdapter(OpenDroidPDFActivity activity,
                                     DocumentNavigationController docNav,
                                     FilePickerCoordinator filePickerCoordinator,
                                     ExportController exportController,
                                     OrganizePagesController organizePagesController) {
        this.activity = activity;
        this.documentNavigationController = docNav;
        this.filePickerCoordinator = filePickerCoordinator;
        this.exportController = exportController;
        this.organizePagesController = organizePagesController;
    }

    @Override public int EDIT_REQUEST() { return RequestCodes.EDIT; }
    @Override public int OUTLINE_REQUEST() { return RequestCodes.OUTLINE; }
    @Override public int PRINT_REQUEST() { return RequestCodes.PRINT; }
    @Override public int FILEPICK_REQUEST() { return RequestCodes.FILE_PICK; }
    @Override public int SAVEAS_REQUEST() { return RequestCodes.SAVE_AS; }
    @Override public int SAVE_COPY_REQUEST() { return RequestCodes.SAVE_COPY; }
    @Override public int SAVE_LINEARIZED_REQUEST() { return RequestCodes.SAVE_LINEARIZED; }
    @Override public int SAVE_ENCRYPTED_REQUEST() { return RequestCodes.SAVE_ENCRYPTED; }
    @Override public int IMPORT_ANNOTATIONS_REQUEST() { return RequestCodes.IMPORT_ANNOTATIONS; }
    @Override public int MANAGE_STORAGE_REQUEST() { return RequestCodes.MANAGE_STORAGE; }
    @Override public int ORGANIZE_PAGES_PICK_MERGE_REQUEST() { return RequestCodes.ORGANIZE_PAGES_PICK_MERGE; }
    @Override public int ORGANIZE_PAGES_PICK_INSERT_REQUEST() { return RequestCodes.ORGANIZE_PAGES_PICK_INSERT; }
    @Override public int ORGANIZE_PAGES_SAVE_OUTPUT_REQUEST() { return RequestCodes.ORGANIZE_PAGES_SAVE_OUTPUT; }

    @Override public void overridePendingTransition(int enter, int exit) { activity.overridePendingTransition(enter, exit); }
    @Override public void hideDashboard() { activity.hideDashboard(); }
    @Override public void setIntent(Intent intent) { activity.setIntent(intent); }
    @Override public Intent getIntent() { return activity.getIntent(); }
    @Override public void openDocumentFromIntent(Intent intent) { activity.openDocumentFromIntent(intent); }
    @Override public boolean canResumeAfterManageStorage() { return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) && Environment.isExternalStorageManager(); }
    @Override public void showToast(int resId) { Toast.makeText(activity, resId, Toast.LENGTH_LONG).show(); }
    @Override public void setDisplayedViewIndex(int pageIndex) { MuPDFReaderView v = activity.getDocView(); if (v != null) v.setDisplayedViewIndex(pageIndex); }
    @Override public void documentNavigation_onActivityResultSaveAs(int resultCode, Intent intent) {
        if (documentNavigationController != null) documentNavigationController.onActivityResultSaveAs(resultCode, intent);
    }
    @Override public void export_onActivityResultSaveLinearized(int resultCode, Intent intent) {
        if (exportController != null) exportController.onActivityResultSaveLinearized(resultCode, intent);
    }
    @Override public void export_onActivityResultSaveCopy(int resultCode, Intent intent) {
        if (exportController != null) exportController.onActivityResultSaveCopy(resultCode, intent);
    }
    @Override public void export_onActivityResultSaveEncrypted(int resultCode, Intent intent) {
        if (exportController != null) exportController.onActivityResultSaveEncrypted(resultCode, intent);
    }
    @Override public void organizePages_onActivityResultPickMerge(int resultCode, Intent intent) {
        if (organizePagesController != null) organizePagesController.onActivityResultPickMergeInput(resultCode, intent);
    }
    @Override public void organizePages_onActivityResultPickInsert(int resultCode, Intent intent) {
        if (organizePagesController != null) organizePagesController.onActivityResultPickInsertInput(resultCode, intent);
    }
    @Override public void organizePages_onActivityResultSaveOutput(int resultCode, Intent intent) {
        if (organizePagesController != null) organizePagesController.onActivityResultSaveOutput(resultCode, intent);
    }

    @Override
    public boolean filePicker_onActivityResult(int resultCode, Intent intent) {
        return filePickerCoordinator != null && filePickerCoordinator.handleActivityResult(resultCode, intent);
    }

    @Override
    public boolean importAnnotations_onActivityResult(int resultCode, Intent intent) {
        if (exportController == null) return true;
        exportController.onActivityResultImportAnnotations(resultCode, intent);
        return true;
    }
}
