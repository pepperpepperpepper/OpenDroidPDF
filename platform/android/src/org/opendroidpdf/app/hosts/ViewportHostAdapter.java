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
    private final DocumentViewHostAdapter documentViewHostAdapter;

    public ViewportHostAdapter(@NonNull OpenDroidPDFActivity activity,
                               @NonNull DocumentViewHostAdapter documentViewHostAdapter) {
        this.activity = activity;
        this.documentViewHostAdapter = documentViewHostAdapter;
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
        return documentViewHostAdapter != null ? documentViewHostAdapter.currentDocumentType() : DocumentType.OTHER;
    }

    @Nullable
    @Override
    public SidecarAnnotationProvider getSidecarAnnotationProviderOrNull() {
        return documentViewHostAdapter != null ? documentViewHostAdapter.sidecarAnnotationProviderOrNull() : null;
    }

    @Nullable
    @Override
    public org.opendroidpdf.app.document.DocumentIdentity currentDocumentIdentityOrNull() {
        return activity.currentDocumentIdentityOrNull();
    }
}
