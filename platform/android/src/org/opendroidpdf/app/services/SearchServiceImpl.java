package org.opendroidpdf.app.services;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.SearchResult;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.services.search.SearchDocumentView;
import org.opendroidpdf.app.services.search.SearchListener;
import org.opendroidpdf.app.services.search.SearchRequest;
import org.opendroidpdf.app.services.search.SearchSession;
import org.opendroidpdf.core.SearchCallbacks;
import org.opendroidpdf.core.SearchController;

import kotlinx.coroutines.CoroutineScope;

/**
 * Default SearchService backed by {@link SearchController} coroutines,
 * exposed as a simple {@link SearchSession} to the UI.
 */
public class SearchServiceImpl implements SearchService {
    private static final SearchSession NULL_SESSION = new NullSearchSession();

    @Nullable
    private ActiveSearchSession currentSession;

    public SearchServiceImpl(@NonNull Context context) {}

    @Override
    public void bindDocument(@NonNull String docId,
                             @NonNull SearchController searchController,
                             @NonNull SearchDocumentView documentView) {
        if (currentSession != null) currentSession.stop();
        currentSession = new ActiveSearchSession(docId, searchController, documentView);
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

    /** Active session implementation backed by SearchController. */
    private static final class ActiveSearchSession implements SearchSession {
        private final String docId;
        private final SearchController searchController;
        private final SearchDocumentView documentView;
        private @Nullable SearchListener listener;
        private String latestQuery = "";
        private String lastSubmitted = "";
        private long searchSeq = 0L;
        private boolean active = false;
        private @Nullable SearchController.SearchJob searchJob;
        private @Nullable CoroutineScope searchScope;

        ActiveSearchSession(String docId,
                            SearchController searchController,
                            SearchDocumentView documentView) {
            this.docId = docId;
            this.searchController = searchController;
            this.documentView = documentView;
        }

        @Override
        public void start(SearchRequest request) {
            if (searchController == null) return;
            cancelActiveJob(/*notify=*/false);

            final String query = request != null ? request.query() : "";
            final int step = request != null && request.direction() != null ? request.direction().step() : 1;
            final int startPage = request != null ? request.startPage() : 0;
            final boolean jumpToFirst = request != null && request.jumpToFirstResult();
            final int pageCount = safePageCount();

            final long seq = ++searchSeq;
            active = true;
            if (listener != null) {
                try { listener.onStarted(pageCount); } catch (Throwable ignore) {}
            }

            searchScope = AppCoroutines.newMainScope();
            final CoroutineScope scope = searchScope;
            searchJob = searchController.startSearch(
                    query != null ? query : "",
                    step,
                    startPage,
                    new SearchCallbacks() {
                        @Override
                        public void onProgress(int pageIndex) {
                            if (seq != searchSeq) return;
                            if (listener != null) {
                                try { listener.onProgress(pageIndex, pageCount); } catch (Throwable ignore) {}
                            }
                        }

                        @Override
                        public void onResult(SearchResult result) {
                            if (seq != searchSeq) return;
                            if (result == null) return;
                            // Avoid highlighting multiple focused hits across pages; focus is owned by navigation.
                            try { result.setFocus(-1); } catch (Throwable ignore) {}
                            try { documentView.addSearchResult(result); } catch (Throwable ignore) {}
                            if (listener != null) {
                                try { listener.onResult(result); } catch (Throwable ignore) {}
                            }
                        }

                        @Override
                        public void onFirstResult(SearchResult result) {
                            if (seq != searchSeq) return;
                            if (result == null) return;
                            if (jumpToFirst) {
                                try {
                                    if (step == 1) result.focusFirst();
                                    else result.focusLast();
                                } catch (Throwable ignore) {}
                                try { documentView.goToResult(result); } catch (Throwable ignore) {}
                            }
                            if (listener != null) {
                                try { listener.onFirstResult(result); } catch (Throwable ignore) {}
                            }
                        }

                        @Override
                        public void onComplete(SearchResult firstResult) {
                            if (seq != searchSeq) return;
                            active = false;
                            cleanupAfterJob();
                            boolean found = firstResult != null;
                            if (listener != null) {
                                try { listener.onComplete(found); } catch (Throwable ignore) {}
                            }
                        }

                        @Override
                        public void onCancelled() {
                            if (seq != searchSeq) return;
                            active = false;
                            cleanupAfterJob();
                            if (listener != null) {
                                try { listener.onCancelled(); } catch (Throwable ignore) {}
                            }
                        }
                    },
                    scope);
        }

        @Override
        public void stop() {
            cancelActiveJob(/*notify=*/true);
        }

        @Override
        public boolean isActive() {
            return active;
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

        private int safePageCount() {
            try {
                return Math.max(0, searchController != null ? searchController.pageCount() : 0);
            } catch (Throwable ignore) {
                return 0;
            }
        }

        private void cleanupAfterJob() {
            if (searchScope != null) {
                AppCoroutines.cancelScope(searchScope);
                searchScope = null;
            }
            searchJob = null;
        }

        private void cancelActiveJob(boolean notify) {
            // Invalidate any in-flight callbacks.
            searchSeq++;
            active = false;

            if (searchJob != null) {
                try { searchJob.cancel(); } catch (Throwable ignore) {}
                searchJob = null;
            }
            if (searchScope != null) {
                AppCoroutines.cancelScope(searchScope);
                searchScope = null;
            }

            if (notify && listener != null) {
                try { listener.onCancelled(); } catch (Throwable ignore) {}
            }
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
