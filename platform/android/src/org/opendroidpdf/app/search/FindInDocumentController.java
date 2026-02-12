package org.opendroidpdf.app.search;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.SearchResult;
import org.opendroidpdf.app.reader.gesture.ReaderMode;
import org.opendroidpdf.app.services.SearchService;
import org.opendroidpdf.app.services.search.SearchDirection;
import org.opendroidpdf.app.services.search.SearchListener;
import org.opendroidpdf.app.services.search.SearchRequest;
import org.opendroidpdf.app.services.search.SearchSession;
import org.opendroidpdf.app.ui.KeyboardHostAdapter;
import org.opendroidpdf.app.ui.ReadingModeController;

import java.util.Locale;

/**
 * Acrobat-style in-document "Find in document" controller.
 *
 * <p>Unlike the legacy ActionBar SearchView implementation, this bar lives inside the
 * document-host fragment so it works even when the top toolbar is hidden (Reading mode).</p>
 */
public final class FindInDocumentController {
    private static final long SEARCH_DEBOUNCE_MS = 250L;

    private final OpenDroidPDFActivity activity;
    private final SearchService searchService;
    private final KeyboardHostAdapter keyboardHostAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable private Ui ui;
    @Nullable private Runnable pendingDebouncedSearch;
    private boolean ignoreTextChanges = false;
    private boolean shown = false;
    private boolean searching = false;

    public FindInDocumentController(@NonNull OpenDroidPDFActivity activity,
                                    @NonNull SearchService searchService,
                                    @NonNull KeyboardHostAdapter keyboardHostAdapter) {
        this.activity = activity;
        this.searchService = searchService;
        this.keyboardHostAdapter = keyboardHostAdapter;
    }

    public boolean isShowing() {
        Ui u = uiOrNull();
        return u != null && u.bar.getVisibility() == View.VISIBLE;
    }

    public void show() {
        if (!ensureUiBound()) return;
        shown = true;

        MuPDFReaderView docView = activity.getDocView();
        if (docView != null) {
            try { docView.requestMode(ReaderMode.SEARCHING); } catch (Throwable ignore) {}
            // Hide the ActionBar while find is open to avoid "double toolbars".
            try { ReadingModeController.applyToDocumentView(activity, docView, true); } catch (Throwable ignore) {}
        } else {
            // Fallback: hide anyway so the bar isn't occluded.
            try {
                ActionBar bar = activity.getSupportActionBar();
                if (bar != null) bar.hide();
            } catch (Throwable ignore) {
            }
        }

        ui.bar.setVisibility(View.VISIBLE);
        ui.query.requestFocus();
        ui.query.post(() -> showKeyboard(ui.query));

        // Restore last query (if any).
        SearchSession session = searchService.session();
        CharSequence latest = session.latestQuery();
        if (latest == null) latest = "";
        setQueryText(latest.toString(), false);

        updateClearButtonVisibility();
        updateCounterFromDocView();
        setSearchingUi(false);

        session.setListener(searchListener);
    }

    public void close() {
        if (!ensureUiBound()) return;
        shown = false;
        cancelPendingDebouncedSearch();

        SearchSession session = searchService.session();
        try { session.stop(); } catch (Throwable ignore) {}
        try { session.setListener(null); } catch (Throwable ignore) {}
        try { session.setLatestQuery(""); } catch (Throwable ignore) {}
        try { session.setLastSubmittedQuery(""); } catch (Throwable ignore) {}

        MuPDFReaderView docView = activity.getDocView();
        if (docView != null) {
            try { docView.clearSearchResults(); } catch (Throwable ignore) {}
            try { docView.resetupChildren(); } catch (Throwable ignore) {}
            try { docView.requestMode(ReaderMode.VIEWING); } catch (Throwable ignore) {}
            try { ReadingModeController.applyToDocumentView(activity, docView); } catch (Throwable ignore) {}
        } else {
            try {
                // Restore toolbar to reading-mode preference even if docView is unavailable.
                if (!ReadingModeController.isEnabled(activity)) {
                    ActionBar bar = activity.getSupportActionBar();
                    if (bar != null) bar.show();
                }
            } catch (Throwable ignore) {
            }
        }

        setQueryText("", false);
        updateClearButtonVisibility();
        setSearchingUi(false);
        updateCounter(0, 0);

        ui.bar.setVisibility(View.GONE);
        try { keyboardHostAdapter.hideKeyboard(); } catch (Throwable ignore) {}
        try { activity.invalidateOptionsMenuSafely(); } catch (Throwable ignore) {}
    }

    /** Called by back-press and mode transitions to ensure find doesn't linger across modes. */
    public void closeIfShowing() {
        if (isShowing()) close();
    }

    private final SearchListener searchListener = new SearchListener() {
        @Override
        public void onStarted(int pageCount) {
            setSearchingUi(true);
        }

        @Override
        public void onProgress(int pageIndex, int pageCount) {
            // We intentionally don't show a modal progress dialog; progress is indicated inline.
        }

        @Override
        public void onResult(SearchResult result) {
            updateCounterFromDocView();
        }

        @Override
        public void onFirstResult(SearchResult result) {
            updateCounterFromDocView();
        }

        @Override
        public void onComplete(boolean found) {
            setSearchingUi(false);
            updateCounterFromDocView();
        }

        @Override
        public void onCancelled() {
            setSearchingUi(false);
            updateCounterFromDocView();
        }
    };

    private void setSearchingUi(boolean searching) {
        this.searching = searching;
        if (!ensureUiBound()) return;
        ui.progress.setVisibility(searching ? View.VISIBLE : View.GONE);
        ui.stop.setVisibility(searching ? View.VISIBLE : View.GONE);
    }

    private void startSearchDebounced(@NonNull String query) {
        cancelPendingDebouncedSearch();
        pendingDebouncedSearch = () -> startSearch(query, SearchDirection.FORWARD, false);
        mainHandler.postDelayed(pendingDebouncedSearch, SEARCH_DEBOUNCE_MS);
    }

    private void cancelPendingDebouncedSearch() {
        if (pendingDebouncedSearch != null) {
            mainHandler.removeCallbacks(pendingDebouncedSearch);
            pendingDebouncedSearch = null;
        }
    }

    private void startSearch(@NonNull String query,
                             @NonNull SearchDirection direction,
                             boolean jumpToFirstResult) {
        if (TextUtils.isEmpty(query)) return;

        MuPDFReaderView docView = activity.getDocView();
        if (docView == null) return;

        SearchSession session = searchService.session();
        try { session.stop(); } catch (Throwable ignore) {}

        try {
            docView.clearSearchResults();
            docView.resetupChildren();
        } catch (Throwable ignore) {
        }

        int startPage = 0;
        try { startPage = docView.getSelectedItemPosition(); } catch (Throwable ignore) { startPage = 0; }

        // Tell the navigator how to order matches for counter/navigation.
        try {
            int pageCount = 0;
            try {
                org.opendroidpdf.core.MuPdfRepository repo = activity.getRepository();
                pageCount = repo != null ? repo.getPageCount() : 0;
            } catch (Throwable ignore) {
                pageCount = 0;
            }
            docView.setSearchMatchOrdering(startPage, pageCount);
        } catch (Throwable ignore) {
        }

        try { session.setLatestQuery(query); } catch (Throwable ignore) {}
        try { session.setLastSubmittedQuery(query); } catch (Throwable ignore) {}

        setSearchingUi(true);
        session.start(new SearchRequest(query, direction, startPage, jumpToFirstResult));
    }

    private void navigateWithinResults(int direction) {
        if (!ensureUiBound()) return;

        String query = ui.query.getText() != null ? ui.query.getText().toString() : "";
        if (TextUtils.isEmpty(query)) return;

        MuPDFReaderView docView = activity.getDocView();
        if (docView == null) return;

        SearchSession session = searchService.session();
        CharSequence last = session.lastSubmittedQuery();
        boolean queryMatches = last != null && query.contentEquals(last);
        boolean hasResults = false;
        try { hasResults = docView.hasSearchResults(); } catch (Throwable ignore) { hasResults = false; }

        if (!hasResults || !queryMatches) {
            cancelPendingDebouncedSearch();
            startSearch(query, SearchDirection.FORWARD, true);
            return;
        }

        try { keyboardHostAdapter.hideKeyboard(); } catch (Throwable ignore) {}
        try { docView.requestFocus(); } catch (Throwable ignore) {}
        try { docView.goToNextSearchResult(direction); } catch (Throwable ignore) {}
        updateCounterFromDocView();
    }

    private void updateCounterFromDocView() {
        if (!ensureUiBound()) return;
        MuPDFReaderView docView = activity.getDocView();
        if (docView == null) {
            updateCounter(0, 0);
            return;
        }
        int total = 0;
        int current = 0;
        try { total = docView.searchMatchCount(); } catch (Throwable ignore) { total = 0; }
        try { current = docView.searchFocusedMatchIndex1Based(); } catch (Throwable ignore) { current = 0; }
        updateCounter(current, total);
    }

    private void updateCounter(int current1BasedOrZero, int total) {
        if (!ensureUiBound()) return;
        int cur = Math.max(0, current1BasedOrZero);
        int tot = Math.max(0, total);
        ui.counter.setText(String.format(Locale.getDefault(), "%d / %d", cur, tot));
    }

    private void updateClearButtonVisibility() {
        if (!ensureUiBound()) return;
        Editable text = ui.query.getText();
        boolean visible = text != null && text.length() > 0;
        ui.clear.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void clearQueryAndResults() {
        cancelPendingDebouncedSearch();
        SearchSession session = searchService.session();
        try { session.stop(); } catch (Throwable ignore) {}
        try { session.setLatestQuery(""); } catch (Throwable ignore) {}
        try { session.setLastSubmittedQuery(""); } catch (Throwable ignore) {}

        MuPDFReaderView docView = activity.getDocView();
        if (docView != null) {
            try { docView.clearSearchResults(); } catch (Throwable ignore) {}
            try { docView.resetupChildren(); } catch (Throwable ignore) {}
        }

        setQueryText("", false);
        updateClearButtonVisibility();
        setSearchingUi(false);
        updateCounter(0, 0);
    }

    private void setQueryText(@NonNull String text, boolean submit) {
        if (!ensureUiBound()) return;
        ignoreTextChanges = true;
        try {
            ui.query.setText(text);
            ui.query.setSelection(ui.query.getText() != null ? ui.query.getText().length() : 0);
        } catch (Throwable ignore) {
        } finally {
            ignoreTextChanges = false;
        }
        if (submit && !TextUtils.isEmpty(text)) {
            startSearch(text, SearchDirection.FORWARD, true);
        }
    }

    private boolean ensureUiBound() {
        Ui current = uiOrNull();
        if (current != null) return true;

        View bar = activity.findViewById(R.id.find_in_document_bar);
        if (bar == null) return false;

        ImageButton close = bar.findViewById(R.id.find_in_document_close);
        EditText query = bar.findViewById(R.id.find_in_document_query);
        ImageButton clear = bar.findViewById(R.id.find_in_document_clear);
        TextView counter = bar.findViewById(R.id.find_in_document_counter);
        ImageButton prev = bar.findViewById(R.id.find_in_document_prev);
        ImageButton next = bar.findViewById(R.id.find_in_document_next);
        ProgressBar progress = bar.findViewById(R.id.find_in_document_progress);
        ImageButton stop = bar.findViewById(R.id.find_in_document_stop);
        if (close == null || query == null || clear == null || counter == null || prev == null || next == null || progress == null || stop == null) {
            return false;
        }

        ui = new Ui(bar, close, query, clear, counter, prev, next, progress, stop);
        bindUi(ui);
        return true;
    }

    @Nullable
    private Ui uiOrNull() {
        Ui cached = ui;
        if (cached == null) return null;
        // Fragment swaps can invalidate cached view references; guard by attachment.
        if (cached.bar.getWindowToken() == null) {
            ui = null;
            return null;
        }
        return cached;
    }

    private void bindUi(@NonNull Ui ui) {
        ui.close.setOnClickListener(v -> close());
        ui.clear.setOnClickListener(v -> clearQueryAndResults());
        ui.prev.setOnClickListener(v -> navigateWithinResults(-1));
        ui.next.setOnClickListener(v -> navigateWithinResults(1));
        ui.stop.setOnClickListener(v -> {
            cancelPendingDebouncedSearch();
            try { searchService.session().stop(); } catch (Throwable ignore) {}
            setSearchingUi(false);
        });

        ui.query.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            boolean ime = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO;
            if (!(enter || ime)) return false;

            String q = ui.query.getText() != null ? ui.query.getText().toString() : "";
            if (!TextUtils.isEmpty(q)) {
                cancelPendingDebouncedSearch();
                try { keyboardHostAdapter.hideKeyboard(); } catch (Throwable ignore) {}
                startSearch(q, SearchDirection.FORWARD, true);
            }
            return true;
        });

        ui.query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (ignoreTextChanges) return;
                String q = s != null ? s.toString() : "";
                SearchSession session = searchService.session();
                try { session.setLatestQuery(q); } catch (Throwable ignore) {}
                updateClearButtonVisibility();

                if (TextUtils.isEmpty(q)) {
                    clearQueryAndResults();
                    return;
                }

                // Debounced search-as-you-type (Acrobat-like). Does not jump until explicit submit.
                startSearchDebounced(q);
            }
        });
    }

    private void showKeyboard(@NonNull View view) {
        try {
            Context ctx = activity.getSystemService(Context.INPUT_METHOD_SERVICE) != null
                    ? activity
                    : activity.getApplicationContext();
            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        } catch (Throwable ignore) {
        }
    }

    private static final class Ui {
        final View bar;
        final ImageButton close;
        final EditText query;
        final ImageButton clear;
        final TextView counter;
        final ImageButton prev;
        final ImageButton next;
        final ProgressBar progress;
        final ImageButton stop;

        Ui(View bar,
           ImageButton close,
           EditText query,
           ImageButton clear,
           TextView counter,
           ImageButton prev,
           ImageButton next,
           ProgressBar progress,
           ImageButton stop) {
            this.bar = bar;
            this.close = close;
            this.query = query;
            this.clear = clear;
            this.counter = counter;
            this.prev = prev;
            this.next = next;
            this.progress = progress;
            this.stop = stop;
        }
    }
}

