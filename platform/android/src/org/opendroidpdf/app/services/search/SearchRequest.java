package org.opendroidpdf.app.services.search;

/** Immutable search request parameters. */
public final class SearchRequest {
    private final String query;
    private final SearchDirection direction;
    private final int startPage;
    private final boolean jumpToFirstResult;

    public SearchRequest(String query, SearchDirection direction, int startPage, boolean jumpToFirstResult) {
        this.query = query;
        this.direction = direction;
        this.startPage = startPage;
        this.jumpToFirstResult = jumpToFirstResult;
    }

    public String query() { return query; }
    public SearchDirection direction() { return direction; }
    public int startPage() { return startPage; }
    public boolean jumpToFirstResult() { return jumpToFirstResult; }
}
