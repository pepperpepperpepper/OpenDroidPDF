package org.opendroidpdf;

/**
 * Wraps SearchResultNavigator so MuPDFReaderView can delegate search state
 * without carrying navigation logic or direct mutation of the view list.
 */
public class SearchResultsController {
    public interface Host {
        int currentPage();
        void setDisplayedViewIndex(int page);
        void doNextScrollWithCenter();
        void setDocRelXScroll(float docRelXScroll);
        void setDocRelYScroll(float docRelYScroll);
        void resetupChildren();
    }

    private final SearchResultNavigator navigator;

    public SearchResultsController(Host host) {
        this.navigator = new SearchResultNavigator(new SearchResultNavigator.Host() {
            @Override public int currentPage() { return host.currentPage(); }
            @Override public void setDisplayedViewIndex(int page) { host.setDisplayedViewIndex(page); }
            @Override public void doNextScrollWithCenter() { host.doNextScrollWithCenter(); }
            @Override public void setDocRelXScroll(float docRelXScroll) { host.setDocRelXScroll(docRelXScroll); }
            @Override public void setDocRelYScroll(float docRelYScroll) { host.setDocRelYScroll(docRelYScroll); }
            @Override public void resetupChildren() { host.resetupChildren(); }
        });
    }

    public void addResult(SearchResult result) { navigator.add(result); }
    public void clear() { navigator.clear(); }
    public boolean hasResults() { return navigator.hasAny(); }
    public void goToNext(int direction) { navigator.goToNext(direction); }
    public void applyToView(int pageIndex, MuPDFView view) { navigator.applyToView(pageIndex, view); }
}
