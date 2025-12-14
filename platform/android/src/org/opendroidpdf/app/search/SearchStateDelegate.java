package org.opendroidpdf.app.search;

import androidx.annotation.NonNull;
import android.text.TextUtils;

/**
 * Holds transient search UI state and executes search actions so the activity stays slimmer.
 */
public final class SearchStateDelegate {
    private final SearchActions searchActions = new SearchActions();
    private String latestTextInSearchBox = "";
    private String textOfLastSearch = "";

    public void setLatestSearchQuery(@NonNull CharSequence query) {
        latestTextInSearchBox = query != null ? query.toString() : "";
    }

    @NonNull
    public CharSequence getLatestSearchQuery() {
        return latestTextInSearchBox != null ? latestTextInSearchBox : "";
    }

    public void setTextOfLastSearch(@NonNull CharSequence query) {
        textOfLastSearch = query != null ? query.toString() : "";
    }

    @NonNull
    public CharSequence getTextOfLastSearch() {
        return textOfLastSearch != null ? textOfLastSearch : "";
    }

    public void search(@NonNull SearchActions.Host host, int direction) {
        if (TextUtils.isEmpty(latestTextInSearchBox)) return;
        searchActions.search(host, direction);
    }
}
