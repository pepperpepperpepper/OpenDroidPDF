package org.opendroidpdf.app.hosts;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.document.DocumentNavigationController;

import java.io.File;
import java.util.concurrent.Callable;

/** Adapter for DocumentNavigationController.Host that delegates to the activity. */
public final class NavigationHostAdapter implements DocumentNavigationController.Host {
    private final OpenDroidPDFActivity activity;

    public NavigationHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override public boolean hasUnsavedChanges() { return activity.hasUnsavedChanges(); }
    @Override public boolean canSaveToCurrentUri() { return activity.canSaveToCurrentUri(); }
    @Override public void saveInBackground(Callable<?> success, Callable<?> failure) { activity.saveInBackground(success, failure); }
    @Override public void saveAsInBackground(Uri uri, Callable<?> success, Callable<?> failure) { activity.saveAsInBackgroundCompat(uri, success, failure); }
    @Override public void callInBackgroundAndShowDialog(String message, Callable<Exception> saveCallable, Callable<?> success, Callable<?> failure) {
        activity.callInBackgroundAndShowDialog(message, saveCallable, success, failure);
    }
    @Override public void commitPendingInkToCoreBlocking() { activity.commitPendingInkToCoreBlocking(); }
    @Override public void showInfo(String message) { activity.showInfo(message); }
    @Override public AlertDialog.Builder alertBuilder() { return activity.getAlertBuilder(); }
    @Override public void startActivityForResult(Intent intent, int requestCode) { activity.startActivityForResult(intent, requestCode); }
    @Override public void overridePendingTransition(int enterAnim, int exitAnim) { activity.overridePendingTransition(enterAnim, exitAnim); }
    @Override public void hideDashboard() { activity.hideDashboard(); }
    @Override public OpenDroidPDFCore getCore() { return activity.getCore(); }
    @Override public void setCoreInstance(OpenDroidPDFCore core) { activity.setCoreInstance(core); }
    @Override public void finish() { activity.finish(); }
    @Override public void checkSaveThenCall(Callable<?> callable) { activity.checkSaveThenCall(callable); }
    @Override public void setTitle() { activity.setTitle(); }
    @Override public File getNotesDir() { return OpenDroidPDFActivity.getNotesDir(activity); }
    @Override public void openNewDocument(String filename) throws java.io.IOException { activity.openNewDocument(filename); }
    @Override public void setupCore() { activity.setupCore(); }
    @Override public void setupDocView() { activity.setupDocView(); }
    @Override public void setupSearchTaskManager() { activity.setupSearchTaskManager(); }
    @Override public void tryToTakePersistablePermissions(Intent intent) { activity.tryToTakePersistablePermissions(intent); }
    @Override public void rememberTemporaryUriPermission(Intent intent) { activity.rememberTemporaryUriPermission(intent); }
    @Override public void saveRecentFiles(SharedPreferences prefs, SharedPreferences.Editor edit, Uri uri) { activity.saveRecentFiles(prefs, edit, uri); }
    @Override public SharedPreferences getSharedPreferences(String name, int mode) { return activity.getSharedPreferences(name, mode); }
    @Override public void runAutotestIfNeeded(Intent intent) { activity.runAutotestIfNeeded(intent); }
    @Override public OpenDroidPDFActivity getActivity() { return activity; }
}

