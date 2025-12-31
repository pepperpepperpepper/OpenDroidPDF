package org.opendroidpdf.app.document;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFCore;

/** Non-config state retained across configuration changes (rotation, multi-window, etc.). */
public final class RetainedDocumentCore {
    @NonNull public final OpenDroidPDFCore core;
    @NonNull public final DocumentOrigin origin;
    public final boolean saveToCurrentUriDisabledByPolicy;

    public RetainedDocumentCore(@NonNull OpenDroidPDFCore core,
                                @NonNull DocumentOrigin origin,
                                boolean saveToCurrentUriDisabledByPolicy) {
        this.core = core;
        this.origin = origin;
        this.saveToCurrentUriDisabledByPolicy = saveToCurrentUriDisabledByPolicy;
    }
}

