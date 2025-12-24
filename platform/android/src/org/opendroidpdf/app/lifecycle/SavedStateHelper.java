package org.opendroidpdf.app.lifecycle;

import android.os.Bundle;
import android.os.Parcelable;

import org.opendroidpdf.SaveInstanceStateManager;
import org.opendroidpdf.app.services.SearchService;

/**
 * Restores UI-relevant state from savedInstanceState and applies it to the activity.
 */
public final class SavedStateHelper {
    private SavedStateHelper() {}

    public interface Host {
        ActivityComposition.Composition compositionOrNull();
        @androidx.annotation.Nullable org.opendroidpdf.MuPDFReaderView docViewOrNull();
        void applySavedUiState(int pageBefore,
                               float normScale,
                               float normX,
                               float normY,
                               @androidx.annotation.Nullable Parcelable docViewState,
                               @androidx.annotation.Nullable String latestSearch);
    }

    public static void save(Host host, Bundle outState) {
        if (host == null || outState == null) return;

        ActivityComposition.Composition comp = host.compositionOrNull();
        org.opendroidpdf.app.navigation.LinkBackState link = (comp != null && comp.linkBackHelper != null)
                ? comp.linkBackHelper.state()
                : null;
        if (link != null) {
            outState.putInt("PageBeforeInternalLinkHit", link.page());
            outState.putFloat("NormalizedScaleBeforeInternalLinkHit", link.scale());
            outState.putFloat("NormalizedXScrollBeforeInternalLinkHit", link.normX());
            outState.putFloat("NormalizedYScrollBeforeInternalLinkHit", link.normY());
        }

        org.opendroidpdf.MuPDFReaderView docView = host.docViewOrNull();
        if (docView != null) {
            outState.putParcelable("mDocView", docView.onSaveInstanceState());
        }

        SearchService searchService = comp != null ? comp.searchService : null;
        if (searchService != null) {
            CharSequence latest = searchService.session().latestQuery();
            if (latest != null) outState.putString("latestTextInSearchBox", latest.toString());
        }

        // Treat the bundle with the SaveInstanceStateManager before saving it
        SaveInstanceStateManager.saveBundleIfNecessary(outState);
    }

    public static void restore(Host host, Bundle savedInstanceState) {
        if (host == null || savedInstanceState == null) return;

        int pageBefore = savedInstanceState.getInt("PageBeforeInternalLinkHit", -1);
        float normScale = savedInstanceState.getFloat("NormalizedScaleBeforeInternalLinkHit", 1.0f);
        float normX = savedInstanceState.getFloat("NormalizedXScrollBeforeInternalLinkHit", 0f);
        float normY = savedInstanceState.getFloat("NormalizedYScrollBeforeInternalLinkHit", 0f);
        Parcelable docViewState = savedInstanceState.getParcelable("mDocView");
        String latestSearch = savedInstanceState.getString("latestTextInSearchBox", null);

        host.applySavedUiState(pageBefore, normScale, normX, normY, docViewState, latestSearch);
    }
}
