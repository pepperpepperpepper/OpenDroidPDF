package org.opendroidpdf.app.hosts;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.reflow.ReflowSettingsController;
import org.opendroidpdf.core.MuPdfRepository;

/** Adapter for {@link ReflowSettingsController.Host} that delegates to {@link OpenDroidPDFActivity}. */
public final class ReflowSettingsHostAdapter implements ReflowSettingsController.Host {
    private final OpenDroidPDFActivity activity;

    public ReflowSettingsHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Nullable @Override public OpenDroidPDFCore getCore() { return activity.getCore(); }
    @Nullable @Override public MuPDFReaderView getDocView() { return activity.getDocView(); }
    @Nullable @Override public MuPdfRepository getRepository() { return activity.getRepository(); }
    @Nullable @Override public org.opendroidpdf.app.document.DocumentIdentity currentDocumentIdentityOrNull() { return activity.currentDocumentIdentityOrNull(); }
    @Nullable @Override public org.opendroidpdf.app.ui.UiStateDelegate getUiStateDelegate() { return activity.getUiStateDelegate(); }
    @NonNull @Override public AlertDialog.Builder alertBuilder() { return activity.getAlertBuilder(); }
    @Override public void stopSearchTasks() { activity.stopSearchTasks(); }
    @Override public void showInfo(@NonNull String message) { activity.showInfo(message); }
    @NonNull @Override public String t(int resId) { return activity.getString(resId); }
    @NonNull @Override public Resources resources() { return activity.getResources(); }
    @NonNull @Override public Context context() { return activity; }
}
