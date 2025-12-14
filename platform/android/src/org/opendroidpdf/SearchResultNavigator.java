package org.opendroidpdf;

import android.graphics.RectF;
import android.util.SparseArray;

/**
 * Manages search results and navigation between them for MuPDFReaderView.
 */
class SearchResultNavigator {
    interface Host {
        int currentPage();
        void setDisplayedViewIndex(int page);
        void doNextScrollWithCenter();
        void setDocRelXScroll(float docRelXScroll);
        void setDocRelYScroll(float docRelYScroll);
        void resetupChildren();
    }

    private final Host host;
    private final SparseArray<SearchResult> results = new SparseArray<>();

    SearchResultNavigator(Host host) {
        this.host = host;
    }

    void add(SearchResult result) {
        results.put(result.getPageNumber(), result);
    }

    void clear() {
        results.clear();
    }

    boolean hasAny() {
        return results.size() != 0;
    }

    void applyToView(int pageIndex, MuPDFView view) {
        view.setSearchResult(results.get(pageIndex));
    }

    void goToNext(int direction) {
        RectF resultRect = null;
        int resultPage = -1;
        SearchResult current = results.get(host.currentPage());
        if (current != null && current.incrementFocus(direction)) {
            resultRect = current.getFocusedSearchBox();
        } else {
            for (int i = 0, size = results.size(); i < size; i++) {
                SearchResult result = results.valueAt(direction == 1 ? i : size - 1 - i);
                if (direction * result.getPageNumber() > direction * host.currentPage()) {
                    if (direction == 1) result.focusFirst();
                    else result.focusLast();
                    resultPage = result.getPageNumber();
                    resultRect = result.getFocusedSearchBox();
                    break;
                }
            }
        }

        if (resultPage != -1) {
            host.setDisplayedViewIndex(resultPage);
        }
        if (resultRect != null) {
            host.doNextScrollWithCenter();
            host.setDocRelXScroll(resultRect.centerX());
            host.setDocRelYScroll(resultRect.centerY());
            host.resetupChildren();
        }
    }
}
