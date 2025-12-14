package org.opendroidpdf.app.document;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Keeps light-weight document state helpers out of the activity.
 */
public final class DocumentStateDelegate {
    @Nullable private MuPdfRepository repository;
    @Nullable private OpenDroidPDFCore core;

    public void set(@Nullable OpenDroidPDFCore core, @Nullable MuPdfRepository repository) {
        this.core = core;
        this.repository = repository;
    }

    public boolean hasRepository() { return repository != null; }

    @Nullable
    public Uri currentDocumentUri() {
        if (repository != null) return repository.getDocumentUri();
        return core != null ? core.getUri() : null;
    }

    @NonNull
    public String currentDocumentName(@NonNull Context context) {
        if (repository != null) return repository.getDocumentName();
        return core != null ? core.getFileName() : context.getString(R.string.app_name);
    }

    public boolean canSaveToCurrentUri(@NonNull OpenDroidPDFActivity activity) {
        return core != null && core.canSaveToCurrentUri(activity);
    }

    public boolean hasUnsavedChanges() {
        if (repository != null) return repository.hasUnsavedChanges();
        return core != null && core.hasChanges();
    }
}
