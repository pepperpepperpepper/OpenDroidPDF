package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.SearchResult;
import org.opendroidpdf.app.services.search.SearchDocumentView;

/**
 * Adapts MuPDFReaderView to the minimal surface SearchService needs.
 * Keeps the SearchService boundary free of UI/view types.
 */
public final class SearchDocumentHostAdapter implements SearchDocumentView {
    private final MuPDFReaderView docView;

    public SearchDocumentHostAdapter(@NonNull MuPDFReaderView docView) {
        this.docView = docView;
    }

    @Override
    public void addSearchResult(@NonNull SearchResult result) {
        docView.addSearchResult(result);
    }

    @Override
    public void goToResult(@NonNull SearchResult result) {
        docView.resetupChildren();
        if (docView.getSelectedItemPosition() != result.getPageNumber()) {
            docView.setDisplayedViewIndex(result.getPageNumber());
        }
        if (result.getFocusedSearchBox() != null) {
            docView.doNextScrollWithCenter();
            docView.setDocRelXScroll(result.getFocusedSearchBox().left);
            docView.setDocRelYScroll(result.getFocusedSearchBox().top);
        }
    }
}

