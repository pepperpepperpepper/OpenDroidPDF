package org.opendroidpdf.app.hosts;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.document.DocumentViewportController;
import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.services.RecentFilesService;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Adapter to expose required hooks from the activity to DocumentViewportController
 * without keeping large helper code in the activity.
 */
public final class ViewportHostAdapter implements DocumentViewportController.Host {
    private final OpenDroidPDFActivity activity;

    public ViewportHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Nullable @Override public MuPDFReaderView getDocView() { return activity.getDocView(); }
    @Nullable @Override public RecentFilesService getRecentFilesService() { return activity.getRecentFilesService(); }
    @Nullable @Override public Uri getCoreUri() {
        MuPdfRepository repo = activity.getRepository();
        return repo != null ? repo.getDocumentUri() : null;
    }
    @Nullable @Override public MuPdfRepository getRepository() { return activity.getRepository(); }

    @NonNull
    @Override
    public DocumentType getCurrentDocumentType() {
        return activity.currentDocumentType();
    }

    @Nullable
    @Override
    public SidecarAnnotationProvider getSidecarAnnotationProviderOrNull() {
        return activity.currentSidecarAnnotationProviderOrNull();
    }

    @Nullable
    @Override
    public org.opendroidpdf.app.document.DocumentIdentity currentDocumentIdentityOrNull() {
        return activity.currentDocumentIdentityOrNull();
    }
}
