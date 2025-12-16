package org.opendroidpdf.app.services;

import android.content.Intent;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.lifecycle.ActivityComposition;
import org.opendroidpdf.app.document.DocumentNavigationController;
import org.opendroidpdf.app.navigation.NavigationDelegate;
import org.opendroidpdf.app.helpers.StoragePermissionController;
import org.opendroidpdf.app.document.ExportController;
import org.opendroidpdf.app.services.SearchService;
import org.opendroidpdf.app.services.PenPreferencesService;
import org.opendroidpdf.app.services.DrawingService;
import org.opendroidpdf.app.services.RecentFilesService;
import org.opendroidpdf.app.services.Provider;
import java.util.concurrent.Callable;

/**
 * Lightweight per-activity locator so OpenDroidPDFActivity can delegate flows
 * without reaching directly into the composition. Services are thin wrappers
 * over existing controllers; intent is to make later swaps/testing easier.
 */
public class ServiceLocator {

    public interface NavigationService {
        void openDocumentFromIntent(Intent intent);
        void openDocument();
        void showSaveAsActivity();
        void checkSaveThenCall(Callable<?> callable);
        void resetDocumentStateForIntent();
    }

    public interface PermissionService {
        boolean ensureStoragePermissionForIntent(OpenDroidPDFActivity activity, Intent intent);
        void resetAwaiting();
        boolean handleRequestPermissionsResult(int requestCode, int[] grantResults, Runnable onGranted, Runnable onDenied);
        boolean isAwaitingManageStoragePermission();
        void setAwaitingManageStoragePermission(boolean awaiting);
        boolean isShowingStoragePermissionDialog();
        void setShowingStoragePermissionDialog(boolean showing);
    }

    public interface ExportService {
        void printDoc();
        void shareDoc();
        void saveDoc();
    }

    private final PenPreferencesService penPreferencesService;
    private final DrawingService drawingService;
    private final Provider<RecentFilesService> recentFilesSupplier;

    private final NavigationService navigationService;
    private final PermissionService permissionService;
    private final ExportService exportService;
    private final SearchService searchService;

    public ServiceLocator(ActivityComposition.Composition comp,
                          DocumentNavigationController navigationController,
                          org.opendroidpdf.app.document.DocumentLifecycleManager lifecycleManager,
                          org.opendroidpdf.app.lifecycle.SaveFlagController saveFlagController,
                          ExportController exportController,
                          SearchService searchService,
                          PenPreferencesService penPreferencesService,
                          DrawingService drawingService,
                          Provider<RecentFilesService> recentFilesSupplier) {
        NavigationDelegate navDelegate = comp != null ? comp.navigationDelegate : null;
        StoragePermissionController permissionController = comp != null ? comp.storagePermissionController : null;
        this.navigationService = new NavigationServiceImpl(navDelegate, navigationController, lifecycleManager, saveFlagController);
        this.permissionService = new PermissionServiceImpl(permissionController);
        this.exportService = new ExportServiceImpl(exportController);
        this.searchService = searchService;
        this.penPreferencesService = penPreferencesService;
        this.drawingService = drawingService;
        this.recentFilesSupplier = recentFilesSupplier;
    }

    public NavigationService navigation() { return navigationService; }
    public PermissionService permissions() { return permissionService; }
    public ExportService export() { return exportService; }
    public SearchService search() { return searchService; }
    public PenPreferencesService penPreferences() { return penPreferencesService; }
    public DrawingService drawing() { return drawingService; }
    public RecentFilesService recentFiles() {
        return recentFilesSupplier != null ? recentFilesSupplier.get() : null;
    }

    private static class NavigationServiceImpl implements NavigationService {
        private final NavigationDelegate navigationDelegate;
        private final DocumentNavigationController navigationController;
        private final org.opendroidpdf.app.document.DocumentLifecycleManager lifecycleManager;
        private final org.opendroidpdf.app.lifecycle.SaveFlagController saveFlagController;

        NavigationServiceImpl(NavigationDelegate navigationDelegate,
                              DocumentNavigationController navigationController,
                              org.opendroidpdf.app.document.DocumentLifecycleManager lifecycleManager,
                              org.opendroidpdf.app.lifecycle.SaveFlagController saveFlagController) {
            this.navigationDelegate = navigationDelegate;
            this.navigationController = navigationController;
            this.lifecycleManager = lifecycleManager;
            this.saveFlagController = saveFlagController;
        }

        @Override
        public void openDocumentFromIntent(Intent intent) {
            if (navigationDelegate != null) navigationDelegate.openDocumentFromIntent(intent);
        }

        @Override
        public void openDocument() {
            if (navigationDelegate != null) navigationDelegate.openDocument();
        }

        @Override
        public void showSaveAsActivity() {
            if (saveFlagController != null) saveFlagController.markIgnoreSaveOnStop();
            if (navigationController != null) navigationController.showSaveAsActivity();
        }

        @Override
        public void checkSaveThenCall(Callable<?> callable) {
            if (navigationController != null) navigationController.checkSaveThenCall(callable);
        }

        @Override
        public void resetDocumentStateForIntent() {
            if (lifecycleManager != null) lifecycleManager.resetDocumentStateForIntent();
        }
    }

    private static class PermissionServiceImpl implements PermissionService {
        private final StoragePermissionController permissionController;

        PermissionServiceImpl(StoragePermissionController controller) {
            this.permissionController = controller;
        }

        @Override
        public boolean ensureStoragePermissionForIntent(OpenDroidPDFActivity activity, Intent intent) {
            if (permissionController != null) return permissionController.ensureForIntent(activity, intent);
            return org.opendroidpdf.app.helpers.StoragePermissionHelper.ensureStoragePermissionForIntent(activity, intent);
        }

        @Override
        public void resetAwaiting() {
            if (permissionController != null) permissionController.resetAwaiting();
        }

        @Override
        public boolean handleRequestPermissionsResult(int requestCode, int[] grantResults, Runnable onGranted, Runnable onDenied) {
            if (permissionController == null) return false;
            return permissionController.handleRequestPermissionsResult(requestCode, grantResults, onGranted, onDenied);
        }

        @Override
        public boolean isAwaitingManageStoragePermission() {
            return permissionController != null && permissionController.isAwaitingManageStoragePermission();
        }

        @Override
        public void setAwaitingManageStoragePermission(boolean awaiting) {
            if (permissionController != null) permissionController.setAwaitingManageStoragePermission(awaiting);
        }

        @Override
        public boolean isShowingStoragePermissionDialog() {
            return permissionController != null && permissionController.isShowingStoragePermissionDialog();
        }

        @Override
        public void setShowingStoragePermissionDialog(boolean showing) {
            if (permissionController != null) permissionController.setShowingStoragePermissionDialog(showing);
        }
    }

    private static class ExportServiceImpl implements ExportService {
        private final ExportController exportController;

        ExportServiceImpl(ExportController controller) { this.exportController = controller; }

        @Override public void printDoc() { if (exportController != null) exportController.printDoc(); }
        @Override public void shareDoc() { if (exportController != null) exportController.shareDoc(); }
        @Override public void saveDoc() { if (exportController != null) exportController.saveDoc(); }
    }
}
