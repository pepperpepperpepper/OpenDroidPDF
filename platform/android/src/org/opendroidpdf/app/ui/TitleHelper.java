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
            android.view.View scrubberContainer = activity.findViewById(R.id.page_scrubber_container);
            android.widget.TextView indicator = activity.findViewById(R.id.page_indicator);
            android.widget.SeekBar scrubber = activity.findViewById(R.id.page_scrubber);
            if (indicator != null) {
                if (totalPages > 1) {
                    if (scrubberContainer != null) scrubberContainer.setVisibility(android.view.View.VISIBLE);

                    // Small affordance: the page indicator is tappable (opens Navigate & View sheet).
                    String indicatorTitle = title + "  ▾";
                    indicator.setText(indicatorTitle);
                    indicator.setVisibility(android.view.View.VISIBLE);

                    if (scrubber != null) {
                        scrubber.setMax(Math.max(0, totalPages - 1));
                        if (scrubber.getProgress() != pageNumber) scrubber.setProgress(pageNumber);
                    }
                } else {
                    if (scrubberContainer != null) scrubberContainer.setVisibility(android.view.View.GONE);
                    indicator.setVisibility(android.view.View.GONE);
                }
            }
        } catch (Throwable ignore) {
        }
    }
}
