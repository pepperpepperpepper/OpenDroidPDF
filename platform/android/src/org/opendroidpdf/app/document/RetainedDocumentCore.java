package org.opendroidpdf.app.document;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.OpenDroidPDFCore;

/** Non-config state retained across configuration changes (rotation, multi-window, etc.). */
public final class RetainedDocumentCore {
    @NonNull public final OpenDroidPDFCore core;
    @NonNull public final DocumentOrigin origin;
    public final boolean saveToCurrentUriDisabledByPolicy;
    @Nullable public final DocumentIdentity identity;
    @Nullable public final Uri userFacingUri;
    @Nullable public final String userFacingDisplayName;

    public RetainedDocumentCore(@NonNull OpenDroidPDFCore core,
                                @NonNull DocumentOrigin origin,
                                boolean saveToCurrentUriDisabledByPolicy,
                                @Nullable DocumentIdentity identity,
                                @Nullable Uri userFacingUri,
                                @Nullable String userFacingDisplayName) {
        this.core = core;
        this.origin = origin;
        this.saveToCurrentUriDisabledByPolicy = saveToCurrentUriDisabledByPolicy;
        this.identity = identity;
        this.userFacingUri = userFacingUri;
        this.userFacingDisplayName = userFacingDisplayName;
    }
}
