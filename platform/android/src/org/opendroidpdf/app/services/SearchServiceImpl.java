package org.opendroidpdf.app.services;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.SearchResult;
import org.opendroidpdf.SearchTaskManager;
import org.opendroidpdf.app.services.search.SearchDirection;
import org.opendroidpdf.app.services.search.SearchListener;
import org.opendroidpdf.app.services.search.SearchRequest;
import org.opendroidpdf.app.services.search.SearchSession;
import org.opendroidpdf.core.SearchController;

/**
 * Default SearchService backed by the existing SearchTaskManager lifecycle,
 * but exposed as a simple SearchSession to the UI.
 */
public class SearchServiceImpl implements SearchService {
    private static final SearchSession NULL_SESSION = new NullSearchSession();

    @Nullable
    private ActiveSearchSession currentSession;

    @Override
    public void bindDocument(@NonNull String docId,
                             @NonNull SearchController searchController,
                             @NonNull MuPDFReaderView docView) {
        if (currentSession != null) currentSession.stop();
        currentSession = new ActiveSearchSession(docId, searchController, docView);
    }

    @Override
    public void clearDocument() {
        if (currentSession != null) {
            currentSession.stop();
            currentSession = null;
        }
    }

    @NonNull
    @Override
    public SearchSession session() {
        return currentSession != null ? currentSession : NULL_SESSION;
    }

    /** Active session implementation that wraps SearchTaskManager. */
    private static final class ActiveSearchSession implements SearchSession {
        private final String docId;
        private final SearchTaskManager manager;
        private @Nullable SearchListener listener;
        private String latestQuery = "";
        private String lastSubmitted = "";

        ActiveSearchSession(String docId,
                            SearchController searchController,
                            MuPDFReaderView docView) {
            this.docId = docId;
            this.manager = new SearchTaskManager(docView.getContext(), searchController) {
                @Override
                protected void onTextFound(SearchResult result) {
                    docView.addSearchResult(result);
                    if (listener != null) listener.onResult(result);
                }

                @Override
                protected void goToResult(SearchResult result) {
                    docView.resetupChildren();
                    if (docView.getSelectedItemPosition() != result.getPageNumber())
                        docView.setDisplayedViewIndex(result.getPageNumber());
                    if (result.getFocusedSearchBox() != null) {
                        docView.doNextScrollWithCenter();
                        docView.setDocRelXScroll(result.getFocusedSearchBox().left);
                        docView.setDocRelYScroll(result.getFocusedSearchBox().top);
                    }
                    if (listener != null) listener.onFirstResult(result);
                }
            };
        }

        @Override
        public void start(SearchRequest request) {
            manager.start(request.query(), request.direction().step(), request.startPage());
        }

        @Override
        public void stop() {
            manager.stop();
            if (listener != null) listener.onCancelled();
        }

        @Override
        public boolean isActive() {
            return manager != null;
        }

        @Override
        public void setListener(@Nullable SearchListener listener) {
            this.listener = listener;
        }

        @NonNull
        @Override
        public CharSequence latestQuery() {
            return latestQuery != null ? latestQuery : "";
        }

        @Override
        public void setLatestQuery(@NonNull CharSequence query) {
            latestQuery = query != null ? query.toString() : "";
        }

        @NonNull
        @Override
        public CharSequence lastSubmittedQuery() {
            return lastSubmitted != null ? lastSubmitted : "";
        }

        @Override
        public void setLastSubmittedQuery(@NonNull CharSequence query) {
            lastSubmitted = query != null ? query.toString() : "";
        }
    }

    /** Null-object session to avoid null checks at call sites. */
    private static final class NullSearchSession implements SearchSession {
        @Override public void start(SearchRequest request) {}
        @Override public void stop() {}
        @Override public boolean isActive() { return false; }
        @Override public void setListener(@Nullable SearchListener listener) {}
        @NonNull @Override public CharSequence latestQuery() { return ""; }
        @Override public void setLatestQuery(@NonNull CharSequence query) {}
        @NonNull @Override public CharSequence lastSubmittedQuery() { return ""; }
        @Override public void setLastSubmittedQuery(@NonNull CharSequence query) {}
    }
}
