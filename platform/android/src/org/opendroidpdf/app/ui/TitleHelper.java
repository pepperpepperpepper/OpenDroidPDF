package org.opendroidpdf.app.ui;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.document.DocumentState;

import java.util.Locale;

/**
 * Small helper to set the ActionBar title/subtitle based on doc state.
 */
public final class TitleHelper {
    private TitleHelper() {}

    public static void setTitle(AppCompatActivity activity, MuPDFReaderView docView, DocumentState docState) {
        if (docState == null || docView == null) return;
        int pageNumber = docView.getSelectedItemPosition();
        int totalPages = docState.pageCount();
        String title = "";
        if (totalPages > 0) {
            title = String.format(Locale.getDefault(), "%d / %d", pageNumber + 1, totalPages);
        }
        String subtitle = docState.displayName();
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
            actionBar.setSubtitle(subtitle);
        }

        try {
            android.widget.TextView indicator = activity.findViewById(R.id.page_indicator);
            if (indicator != null) {
                if (totalPages > 0) {
                    indicator.setText(title);
                    indicator.setVisibility(android.view.View.VISIBLE);
                } else {
                    indicator.setVisibility(android.view.View.GONE);
                }
            }
        } catch (Throwable ignore) {
        }
    }
}
