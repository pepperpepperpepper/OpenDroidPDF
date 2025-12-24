package org.opendroidpdf.app.document;

import android.content.Context;
import android.net.Uri;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.FilePicker;
import org.opendroidpdf.MuPDFPageAdapter;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.hosts.DocumentViewHostAdapter;
import org.opendroidpdf.app.preferences.EditorPreferences;
import org.opendroidpdf.app.preferences.PreferencesCoordinator;
import org.opendroidpdf.app.services.recent.ViewportSnapshot;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.app.document.DocumentIds;

/**
 * Handles docView creation/attachment, adapter setup, and viewport/state restore.
 * Keeps these concerns out of the Activity host.
 */
public final class DocumentViewDelegate {
    public interface Host {
        @NonNull Context context();
        @Nullable MuPDFReaderView docViewOrNull();
        @Nullable OpenDroidPDFCore coreOrNull();
        @Nullable MuPdfRepository repositoryOrNull();
        @Nullable MuPdfController muPdfControllerOrNull();
        @NonNull FilePicker.FilePickerSupport filePickerSupport();
        @Nullable DocumentIdentity currentDocumentIdentityOrNull();
        boolean canSaveToCurrentUri();
    }

    private final Host host;
    private final DocumentViewHostAdapter documentViewHostAdapter;
    private final DocumentViewportController viewportController;
    private final PreferencesCoordinator preferencesCoordinator;

    private Parcelable pendingDocState;
    private boolean needsNewAdapter = false;

    public DocumentViewDelegate(@NonNull Host host,
                                @NonNull DocumentViewHostAdapter documentViewHostAdapter,
                                @NonNull DocumentViewportController viewportController,
                                @NonNull PreferencesCoordinator preferencesCoordinator) {
        this.host = host;
        this.documentViewHostAdapter = documentViewHostAdapter;
        this.viewportController = viewportController;
        this.preferencesCoordinator = preferencesCoordinator;
    }

    public DocumentViewportController getViewportController() {
        return viewportController;
    }

    public void rememberDocViewState(@Nullable Parcelable state) {
        pendingDocState = state;
    }

    public void restoreDocViewStateIfAny() {
        MuPDFReaderView doc = host.docViewOrNull();
        if (pendingDocState != null && doc != null) {
            doc.onRestoreInstanceState(pendingDocState);
        }
        pendingDocState = null;
    }

    public void syncPreferences() {
        MuPDFReaderView doc = host.docViewOrNull();
        if (doc == null) return;
        preferencesCoordinator.applyToDocView(doc);
        preferencesCoordinator.applyToCore(host.coreOrNull());
    }

    public void ensureDocAdapter(@Nullable OpenDroidPDFCore core,
                                 @Nullable MuPdfRepository repo,
                                 @Nullable MuPdfController controller,
                                 boolean needsNewAdapterFlag) {
        MuPDFReaderView doc = host.docViewOrNull();
        if (doc == null || core == null || repo == null || controller == null) return;
        if (needsNewAdapterFlag) {
            DocumentIdentity ident = host.currentDocumentIdentityOrNull();
            String docId = ident != null ? ident.docId()
                    : (core.getUri() != null ? DocumentIds.fromUri(core.getUri()) : "");
            String legacyDocId = ident != null ? ident.legacyDocId() : docId;
            EditorPreferences editorPreferences = new EditorPreferences(
                    preferencesCoordinator::penPrefsSnapshot,
                    preferencesCoordinator::editorPrefsSnapshot);
            doc.setAdapter(new MuPDFPageAdapter(
                    host.context(),
                    controller,
                    host.filePickerSupport(),
                    docId,
                    legacyDocId,
                    documentViewHostAdapter.currentDocumentType(),
                    host.canSaveToCurrentUri(),
                    editorPreferences));
            needsNewAdapter = false;
        }
    }

    /**
     * Recreate the adapter (e.g., after reflow relayout) while preserving the current viewport
     * as best as possible.
     */
    public void recreateAdapterPreservingViewport(@Nullable ViewportSnapshot snapshot) {
        MuPDFReaderView doc = host.docViewOrNull();
        if (doc == null) return;

        ViewportSnapshot snap = snapshot != null ? snapshot : ViewportHelper.snapshot(doc);
        OpenDroidPDFCore core = host.coreOrNull();
        MuPdfRepository repo = host.repositoryOrNull();
        MuPdfController controller = host.muPdfControllerOrNull();
        if (core == null || repo == null || controller == null) return;

        // Reflow relayout changes pagination; any prior search results are now stale and should not
        // be re-applied to the new page indices.
        doc.clearSearchResults();

        DocumentIdentity ident = host.currentDocumentIdentityOrNull();
        String docId = ident != null ? ident.docId()
                : (core.getUri() != null ? DocumentIds.fromUri(core.getUri()) : "");
        String legacyDocId = ident != null ? ident.legacyDocId() : docId;
        EditorPreferences editorPreferences = new EditorPreferences(
                preferencesCoordinator::penPrefsSnapshot,
                preferencesCoordinator::editorPrefsSnapshot);
        doc.setAdapter(new MuPDFPageAdapter(
                host.context(),
                controller,
                host.filePickerSupport(),
                docId,
                legacyDocId,
                documentViewHostAdapter.currentDocumentType(),
                host.canSaveToCurrentUri(),
                editorPreferences));
        needsNewAdapter = false;
        if (documentViewHostAdapter.currentDocumentType() == DocumentType.EPUB && snap != null) {
            long loc = snap.reflowLocation();
            if (loc != -1L) {
                int pageFromLoc = repo.pageNumberFromLocation(loc);
                if (pageFromLoc >= 0) {
                    doc.setDisplayedViewIndex(pageFromLoc);
                    return;
                }
            }
            if (snap.docProgress01() >= 0f) {
                int approx = ViewportHelper.approximatePageIndexFromProgress01(doc, snap.docProgress01());
                if (approx >= 0) {
                    doc.setDisplayedViewIndex(approx);
                    return;
                }
            }
        }

        ViewportHelper.applySnapshot(doc, snap);
    }

    public void restoreViewportIfAny(@Nullable Uri uri) {
        if (uri == null) return;
        viewportController.restoreViewport();
    }

    public void markDocViewNeedsNewAdapter() { needsNewAdapter = true; }
    public boolean docViewNeedsNewAdapter() { return needsNewAdapter; }

    // Lightweight view helpers to keep search/navigation logic out of the activity
    public boolean hasDocView() { return host.docViewOrNull() != null; }

    public void requestDocViewFocus() {
        MuPDFReaderView doc = host.docViewOrNull();
        if (doc != null) doc.requestFocus();
    }

    public void clearSearchResults() {
        MuPDFReaderView doc = host.docViewOrNull();
        if (doc != null) doc.clearSearchResults();
    }

    public void resetupChildren() {
        MuPDFReaderView doc = host.docViewOrNull();
        if (doc != null) doc.resetupChildren();
    }

    public void setViewingMode() {
        org.opendroidpdf.DocViewControls.setViewingMode(host.docViewOrNull());
    }

    public boolean docHasSearchResults() {
        MuPDFReaderView doc = host.docViewOrNull();
        return doc != null && doc.hasSearchResults();
    }

    public void goToNextSearchResult(int direction) {
        MuPDFReaderView doc = host.docViewOrNull();
        if (doc != null) doc.goToNextSearchResult(direction);
    }

    public int currentDisplayPage() {
        MuPDFReaderView doc = host.docViewOrNull();
        return doc != null ? doc.getSelectedItemPosition() : 0;
    }
}
