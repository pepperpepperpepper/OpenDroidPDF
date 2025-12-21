package org.opendroidpdf.app.document;

import android.content.Context;
import android.net.Uri;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFPageAdapter;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.preferences.PreferencesCoordinator;
import org.opendroidpdf.app.services.recent.ViewportSnapshot;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.MuPdfRepository;
import org.opendroidpdf.app.document.DocumentIds;

/**
 * Handles docView creation/attachment, adapter setup, and viewport/state restore.
 * Keeps these concerns out of OpenDroidPDFActivity.
 */
public final class DocumentViewDelegate {
    private final OpenDroidPDFActivity activity;
    private final DocumentViewportController viewportController;
    private final PreferencesCoordinator preferencesCoordinator;

    private Parcelable pendingDocState;
    private boolean needsNewAdapter = false;

    public DocumentViewDelegate(@NonNull OpenDroidPDFActivity activity,
                                @NonNull DocumentViewportController viewportController,
                                @NonNull PreferencesCoordinator preferencesCoordinator) {
        this.activity = activity;
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
        MuPDFReaderView doc = activity.getDocView();
        if (pendingDocState != null && doc != null) {
            doc.onRestoreInstanceState(pendingDocState);
        }
        pendingDocState = null;
    }

    public void syncPreferences() {
        MuPDFReaderView doc = activity.getDocView();
        if (doc == null) return;
        preferencesCoordinator.applyToDocView(doc);
        preferencesCoordinator.applyToCore(activity.getCore());
    }

    public void ensureDocAdapter(@Nullable OpenDroidPDFCore core,
                                 @Nullable MuPdfRepository repo,
                                 @Nullable MuPdfController controller,
                                 boolean needsNewAdapterFlag) {
        MuPDFReaderView doc = activity.getDocView();
        if (doc == null || core == null || repo == null || controller == null) return;
        if (needsNewAdapterFlag) {
            String docId = core.getUri() != null ? DocumentIds.fromUri(core.getUri()) : "";
            doc.setAdapter(new MuPDFPageAdapter(
                    activity,
                    controller,
                    activity.getFilePickerHost(),
                    docId,
                    activity.currentDocumentType(),
                    activity.canSaveToCurrentUri()));
            needsNewAdapter = false;
        }
    }

    /**
     * Recreate the adapter (e.g., after reflow relayout) while preserving the current viewport
     * as best as possible.
     */
    public void recreateAdapterPreservingViewport(@Nullable ViewportSnapshot snapshot) {
        MuPDFReaderView doc = activity.getDocView();
        if (doc == null) return;

        ViewportSnapshot snap = snapshot != null ? snapshot : ViewportHelper.snapshot(doc);
        OpenDroidPDFCore core = activity.getCore();
        MuPdfRepository repo = activity.getRepository();
        MuPdfController controller = activity.getMuPdfController();
        if (core == null || repo == null || controller == null) return;

        // Reflow relayout changes pagination; any prior search results are now stale and should not
        // be re-applied to the new page indices.
        doc.clearSearchResults();

        String docId = core.getUri() != null ? DocumentIds.fromUri(core.getUri()) : "";
        doc.setAdapter(new MuPDFPageAdapter(
                activity,
                controller,
                activity.getFilePickerHost(),
                docId,
                activity.currentDocumentType(),
                activity.canSaveToCurrentUri()));
        needsNewAdapter = false;
        if (activity.currentDocumentType() == DocumentType.EPUB && snap != null && snap.docProgress01() >= 0f) {
            int approx = ViewportHelper.approximatePageIndexFromProgress01(doc, snap.docProgress01());
            if (approx >= 0) {
                doc.setDisplayedViewIndex(approx);
                return;
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
    public boolean hasDocView() { return activity.getDocView() != null; }

    public void requestDocViewFocus() {
        MuPDFReaderView doc = activity.getDocView();
        if (doc != null) doc.requestFocus();
    }

    public void clearSearchResults() {
        MuPDFReaderView doc = activity.getDocView();
        if (doc != null) doc.clearSearchResults();
    }

    public void resetupChildren() {
        MuPDFReaderView doc = activity.getDocView();
        if (doc != null) doc.resetupChildren();
    }

    public void setViewingMode() {
        org.opendroidpdf.DocViewControls.setViewingMode(activity.getDocView());
    }

    public boolean docHasSearchResults() {
        MuPDFReaderView doc = activity.getDocView();
        return doc != null && doc.hasSearchResults();
    }

    public void goToNextSearchResult(int direction) {
        MuPDFReaderView doc = activity.getDocView();
        if (doc != null) doc.goToNextSearchResult(direction);
    }

    public int currentDisplayPage() {
        MuPDFReaderView doc = activity.getDocView();
        return doc != null ? doc.getSelectedItemPosition() : 0;
    }
}
