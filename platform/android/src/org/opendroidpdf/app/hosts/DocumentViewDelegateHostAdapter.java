package org.opendroidpdf.app.hosts;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.FilePicker;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.document.DocumentIdentity;
import org.opendroidpdf.app.document.DocumentViewDelegate;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Bridges OpenDroidPDFActivity to DocumentViewDelegate.Host so the delegate does not depend on the
 * concrete activity type.
 */
public final class DocumentViewDelegateHostAdapter implements DocumentViewDelegate.Host {
    private final OpenDroidPDFActivity activity;

    public DocumentViewDelegateHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @NonNull
    @Override
    public Context context() {
        return activity;
    }

    @Nullable
    @Override
    public MuPDFReaderView docViewOrNull() {
        return activity.getDocView();
    }

    @Nullable
    @Override
    public OpenDroidPDFCore coreOrNull() {
        return activity.getCore();
    }

    @Nullable
    @Override
    public MuPdfRepository repositoryOrNull() {
        return activity.getRepository();
    }

    @Nullable
    @Override
    public MuPdfController muPdfControllerOrNull() {
        return activity.getMuPdfController();
    }

    @NonNull
    @Override
    public FilePicker.FilePickerSupport filePickerSupport() {
        FilePicker.FilePickerSupport host = activity.getFilePickerHost();
        if (host == null) throw new IllegalStateException("FilePickerHostAdapter not initialized");
        return host;
    }

    @Nullable
    @Override
    public DocumentIdentity currentDocumentIdentityOrNull() {
        return activity.currentDocumentIdentityOrNull();
    }

    @Override
    public boolean canSaveToCurrentUri() {
        return activity.canSaveToCurrentUri();
    }
}

