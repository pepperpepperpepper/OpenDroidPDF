package org.opendroidpdf.app.services;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.SearchResult;
import org.opendroidpdf.SearchTaskManager;
import org.opendroidpdf.app.services.search.SearchDocumentView;
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

    private final Context context;

    @Nullable
    private ActiveSearchSession currentSession;

    public SearchServiceImpl(@NonNull Context context) {
        this.context = context;
    }

    @Override
    public void bindDocument(@NonNull String docId,
                             @NonNull SearchController searchController,
                             @NonNull SearchDocumentView documentView) {
        if (currentSession != null) currentSession.stop();
        currentSession = new ActiveSearchSession(docId, context, searchController, documentView);
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
                            Context context,
                            SearchController searchController,
                            SearchDocumentView documentView) {
            this.docId = docId;
            this.manager = new SearchTaskManager(context, searchController) {
                @Override
                protected void onTextFound(SearchResult result) {
                    documentView.addSearchResult(result);
                    if (listener != null) listener.onResult(result);
                }

                @Override
                protected void goToResult(SearchResult result) {
                    documentView.goToResult(result);
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
