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
    private int matchOrderStartPage = 0;
    private int matchOrderPageCount = 0;

    SearchResultNavigator(Host host) {
        this.host = host;
    }

    void setMatchOrdering(int startPage, int pageCount) {
        matchOrderPageCount = Math.max(0, pageCount);
        if (matchOrderPageCount > 0) {
            int normalized = startPage % matchOrderPageCount;
            if (normalized < 0) normalized += matchOrderPageCount;
            matchOrderStartPage = normalized;
        } else {
            matchOrderStartPage = Math.max(0, startPage);
        }
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

    int matchCount() {
        int total = 0;
        for (int i = 0, size = results.size(); i < size; i++) {
            SearchResult r = results.valueAt(i);
            total += pageMatchCount(r);
        }
        return total;
    }

    int focusedMatchIndex1Based() {
        FocusedMatch focus = focusedMatchOrNull();
        if (focus == null) return 0;

        int pageRank = matchRank(focus.pageNumber);
        int before = 0;
        for (int i = 0, size = results.size(); i < size; i++) {
            int page = results.keyAt(i);
            SearchResult r = results.valueAt(i);
            if (matchRank(page) < pageRank) before += pageMatchCount(r);
        }
        int within = Math.max(0, Math.min(pageMatchCount(focus.result) - 1, focus.focusIndex));
        return before + within + 1;
    }

    void applyToView(int pageIndex, MuPDFView view) {
        view.setSearchResult(results.get(pageIndex));
    }

    void goToNext(int direction) {
        if (results.size() == 0) return;
        int step = direction >= 0 ? 1 : -1;

        FocusedMatch focus = focusedMatchOrNull();
        if (focus == null) {
            int seedPage = step == 1 ? minRankPageOrCurrent() : maxRankPageOrCurrent();
            SearchResult seed = results.get(seedPage);
            if (seed == null) return;
            int boxCount = pageMatchCount(seed);
            if (boxCount <= 0) return;
            int idx = step == 1 ? 0 : boxCount - 1;
            setExclusiveFocus(seedPage, idx);
            navigateToFocused(seedPage, seed.getFocusedSearchBox());
            return;
        }

        int focusIndex = focus.focusIndex;
        int boxCount = pageMatchCount(focus.result);
        if (boxCount <= 0) return;

        // In-page navigation when multiple hits exist on the same page.
        if (step == 1 && focusIndex + 1 < boxCount) {
            setExclusiveFocus(focus.pageNumber, focusIndex + 1);
            navigateToFocused(focus.pageNumber, focus.result.getFocusedSearchBox());
            return;
        }
        if (step == -1 && focusIndex - 1 >= 0) {
            setExclusiveFocus(focus.pageNumber, focusIndex - 1);
            navigateToFocused(focus.pageNumber, focus.result.getFocusedSearchBox());
            return;
        }

        int currentRank = matchRank(focus.pageNumber);
        int nextPage = nextPageWithHits(currentRank, step);
        if (nextPage < 0) return;

        SearchResult next = results.get(nextPage);
        if (next == null) return;
        int nextCount = pageMatchCount(next);
        if (nextCount <= 0) return;
        int nextIndex = step == 1 ? 0 : nextCount - 1;
        setExclusiveFocus(nextPage, nextIndex);
        navigateToFocused(nextPage, next.getFocusedSearchBox());
    }

    private void navigateToFocused(int pageNumber, RectF box) {
        if (pageNumber != host.currentPage()) {
            host.setDisplayedViewIndex(pageNumber);
        }
        if (box != null) {
            host.doNextScrollWithCenter();
            host.setDocRelXScroll(box.centerX());
            host.setDocRelYScroll(box.centerY());
            host.resetupChildren();
        }
    }

    private void setExclusiveFocus(int pageNumber, int focusIndex) {
        for (int i = 0, size = results.size(); i < size; i++) {
            SearchResult r = results.valueAt(i);
            if (r == null) continue;
            if (results.keyAt(i) != pageNumber) {
                try { r.setFocus(-1); } catch (Throwable ignore) {}
            }
        }
        SearchResult target = results.get(pageNumber);
        if (target != null) {
            try { target.setFocus(focusIndex); } catch (Throwable ignore) {}
        }
    }

    private int nextPageWithHits(int currentRank, int step) {
        int candidatePage = -1;
        int candidateRank = step == 1 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        int wrapPage = -1;
        int wrapRank = step == 1 ? Integer.MAX_VALUE : Integer.MIN_VALUE;

        for (int i = 0, size = results.size(); i < size; i++) {
            int page = results.keyAt(i);
            int rank = matchRank(page);
            if (step == 1) {
                if (rank > currentRank && rank < candidateRank) {
                    candidateRank = rank;
                    candidatePage = page;
                }
                if (rank < wrapRank) {
                    wrapRank = rank;
                    wrapPage = page;
                }
            } else {
                if (rank < currentRank && rank > candidateRank) {
                    candidateRank = rank;
                    candidatePage = page;
                }
                if (rank > wrapRank) {
                    wrapRank = rank;
                    wrapPage = page;
                }
            }
        }

        return candidatePage != -1 ? candidatePage : wrapPage;
    }

    private int minRankPageOrCurrent() {
        int bestPage = host.currentPage();
        int bestRank = Integer.MAX_VALUE;
        for (int i = 0, size = results.size(); i < size; i++) {
            int page = results.keyAt(i);
            int rank = matchRank(page);
            if (rank < bestRank) {
                bestRank = rank;
                bestPage = page;
            }
        }
        return bestPage;
    }

    private int maxRankPageOrCurrent() {
        int bestPage = host.currentPage();
        int bestRank = Integer.MIN_VALUE;
        for (int i = 0, size = results.size(); i < size; i++) {
            int page = results.keyAt(i);
            int rank = matchRank(page);
            if (rank > bestRank) {
                bestRank = rank;
                bestPage = page;
            }
        }
        return bestPage;
    }

    private int matchRank(int pageNumber) {
        if (matchOrderPageCount > 0) {
            int r = pageNumber - matchOrderStartPage;
            if (r < 0) r += matchOrderPageCount;
            return r;
        }
        return pageNumber;
    }

    private int pageMatchCount(SearchResult result) {
        if (result == null) return 0;
        RectF[] boxes = result.getSearchBoxes();
        return boxes != null ? boxes.length : 0;
    }

    private FocusedMatch focusedMatchOrNull() {
        int current = host.currentPage();
        SearchResult onCurrent = results.get(current);
        if (onCurrent != null && onCurrent.getFocus() >= 0) {
            return new FocusedMatch(current, onCurrent, onCurrent.getFocus());
        }
        for (int i = 0, size = results.size(); i < size; i++) {
            int page = results.keyAt(i);
            SearchResult r = results.valueAt(i);
            if (r != null && r.getFocus() >= 0) {
                return new FocusedMatch(page, r, r.getFocus());
            }
        }
        return null;
    }

    private static final class FocusedMatch {
        final int pageNumber;
        final SearchResult result;
        final int focusIndex;

        FocusedMatch(int pageNumber, SearchResult result, int focusIndex) {
            this.pageNumber = pageNumber;
            this.result = result;
            this.focusIndex = focusIndex;
        }
    }
}
