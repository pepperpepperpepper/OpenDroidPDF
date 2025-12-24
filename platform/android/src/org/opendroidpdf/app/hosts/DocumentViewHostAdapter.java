package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFPageAdapter;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.MuPDFView;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.services.Provider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;

/**
 * Centralizes lightweight "current document/view" helpers so OpenDroidPDFActivity stays a thin host.
 */
public final class DocumentViewHostAdapter {
    private final Provider<MuPDFReaderView> docViewProvider;
    private final Provider<OpenDroidPDFCore> coreProvider;

    public DocumentViewHostAdapter(@NonNull Provider<MuPDFReaderView> docViewProvider,
                                   @NonNull Provider<OpenDroidPDFCore> coreProvider) {
        this.docViewProvider = docViewProvider;
        this.coreProvider = coreProvider;
    }

    @Nullable
    public MuPDFReaderView docViewOrNull() {
        return docViewProvider != null ? docViewProvider.get() : null;
    }

    @Nullable
    public MuPDFPageView currentPageViewOrNull() {
        MuPDFReaderView doc = docViewOrNull();
        if (doc == null) return null;
        try {
            MuPDFView v = (MuPDFView) doc.getSelectedView();
            if (v instanceof MuPDFPageView) return (MuPDFPageView) v;
        } catch (Throwable ignore) {
        }
        return null;
    }

    /** Returns the active sidecar annotation provider for the current document, if any. */
    @Nullable
    public SidecarAnnotationProvider sidecarAnnotationProviderOrNull() {
        MuPDFReaderView doc = docViewOrNull();
        if (doc == null) return null;
        try {
            android.widget.Adapter adapter = doc.getAdapter();
            if (adapter instanceof MuPDFPageAdapter) {
                return ((MuPDFPageAdapter) adapter).sidecarSessionOrNull();
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    /** Returns the current document type as reported by MuPDF. */
    @NonNull
    public DocumentType currentDocumentType() {
        OpenDroidPDFCore core = coreProvider != null ? coreProvider.get() : null;
        if (core == null) return DocumentType.OTHER;
        String format = null;
        try {
            format = core.fileFormat();
        } catch (Throwable ignore) {
        }
        return DocumentType.fromFileFormat(format);
    }

    public boolean isPdfDocument() { return currentDocumentType() == DocumentType.PDF; }

    public boolean isEpubDocument() { return currentDocumentType() == DocumentType.EPUB; }
}

