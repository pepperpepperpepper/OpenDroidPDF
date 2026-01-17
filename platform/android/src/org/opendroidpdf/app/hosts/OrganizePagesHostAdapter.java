package org.opendroidpdf.app.hosts;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.document.OrganizePagesController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.concurrent.Callable;

/**
 * Host adapter for {@link OrganizePagesController}; keeps Activity responsibilities out of
 * controller code.
 */
public final class OrganizePagesHostAdapter implements OrganizePagesController.Host {
    private final OpenDroidPDFActivity activity;
    private final DocumentViewHostAdapter documentViewHostAdapter;
    private final org.opendroidpdf.app.document.SaveUiDelegate saveUi;

    public OrganizePagesHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                    @NonNull DocumentViewHostAdapter documentViewHostAdapter,
                                    @Nullable org.opendroidpdf.app.document.SaveUiDelegate saveUi) {
        this.activity = activity;
        this.documentViewHostAdapter = documentViewHostAdapter;
        this.saveUi = saveUi;
    }

    @NonNull @Override public AppCompatActivity getActivity() { return activity; }
    @NonNull @Override public Context getContext() { return activity; }
    @NonNull @Override public android.content.ContentResolver getContentResolver() { return activity.getContentResolver(); }
    @Override public MuPdfRepository getRepository() { return activity.getRepository(); }
    @Override public void commitPendingInkToCoreBlocking() { activity.commitPendingInkToCoreBlocking(); }
    @Override public void showInfo(@NonNull String message) { activity.showInfo(message); }
    @NonNull @Override public String currentDocumentName() { return activity.currentDocumentNameOrAppName(); }
    @Override public void startActivityForResult(@NonNull Intent intent, int requestCode) { activity.startActivityForResult(intent, requestCode); }
    @Override public void callInBackgroundAndShowDialog(@NonNull String message, @NonNull Callable<Exception> background, @Nullable Callable<Void> success, @Nullable Callable<Void> failure) {
        if (saveUi != null) saveUi.callInBackgroundAndShowDialog(message, background, success, failure);
    }
    @Override public SidecarAnnotationProvider sidecarAnnotationProviderOrNull() {
        return documentViewHostAdapter != null ? documentViewHostAdapter.sidecarAnnotationProviderOrNull() : null;
    }
}

