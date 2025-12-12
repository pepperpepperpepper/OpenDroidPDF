package org.opendroidpdf.app.hosts;

import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.helpers.ActivityResultRouter;
import org.opendroidpdf.app.document.DocumentNavigationController;

public class ActivityResultHostAdapter implements ActivityResultRouter.Host {
    private final OpenDroidPDFActivity activity;
    private final DocumentNavigationController documentNavigationController;
    private final int editRequest, outlineRequest, printRequest, saveAsRequest, manageStorageRequest;

    public ActivityResultHostAdapter(OpenDroidPDFActivity activity,
                                     DocumentNavigationController docNav,
                                     int editRequest,
                                     int outlineRequest,
                                     int printRequest,
                                     int saveAsRequest,
                                     int manageStorageRequest) {
        this.activity = activity;
        this.documentNavigationController = docNav;
        this.editRequest = editRequest;
        this.outlineRequest = outlineRequest;
        this.printRequest = printRequest;
        this.saveAsRequest = saveAsRequest;
        this.manageStorageRequest = manageStorageRequest;
    }

    @Override public int EDIT_REQUEST() { return editRequest; }
    @Override public int OUTLINE_REQUEST() { return outlineRequest; }
    @Override public int PRINT_REQUEST() { return printRequest; }
    @Override public int SAVEAS_REQUEST() { return saveAsRequest; }
    @Override public int MANAGE_STORAGE_REQUEST() { return manageStorageRequest; }

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
}

