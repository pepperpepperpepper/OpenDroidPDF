package org.opendroidpdf.app.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.TextView;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.document.DocumentState;

import java.util.Locale;

/**
 * Small helper to set the ActionBar title/subtitle based on doc state.
 */
public final class TitleHelper {
    private TitleHelper() {}

    private static void cancelPageScrubberTabPositioner(@NonNull TextView tab) {
        try {
            Object tag = tab.getTag(R.id.page_scrubber_tab_positioner_tag);
            if (tag instanceof Runnable) {
                tab.removeCallbacks((Runnable) tag);
            }
            tab.setTag(R.id.page_scrubber_tab_positioner_tag, null);
        } catch (Throwable ignore) {
        }
    }

    private static void applyPageScrubberTabTranslation(@NonNull View host, @NonNull TextView tab, float frac) {
        try {
            int hostH = host.getHeight();
            int tabH = tab.getHeight();
            if (hostH <= 0 || tabH <= 0) return;

            int[] hostLoc = new int[2];
            host.getLocationOnScreen(hostLoc);
            float hostTopOnScreen = (float) hostLoc[1];

            int[] tabLoc = new int[2];
            tab.getLocationOnScreen(tabLoc);
            float baseTabTopOnScreen = (float) tabLoc[1] - tab.getTranslationY();

            float travel = (float) Math.max(0, hostH - tabH);
            float desiredTopOnScreen = hostTopOnScreen + (Math.max(0f, Math.min(1f, frac)) * travel);
            float desiredTranslation = desiredTopOnScreen - baseTabTopOnScreen;
            tab.setTranslationY(desiredTranslation);
        } catch (Throwable ignore) {
        }
    }

    private static void positionPageScrubberTab(AppCompatActivity activity,
                                                MuPDFReaderView docView,
                                                TextView tab,
                                                int pageIndex,
                                                int totalPages,
                                                boolean chromeVisible) {
        if (activity == null || tab == null) return;
        if (!chromeVisible) {
            cancelPageScrubberTabPositioner(tab);
            try { tab.setTranslationY(0f); } catch (Throwable ignore) {}
            return;
        }
        if (docView != null) {
            try {
                if (docView.isScrubbing()) {
                    cancelPageScrubberTabPositioner(tab);
                    return;
                }
            } catch (Throwable ignore) {
            }
        }
        if (totalPages <= 1) {
            cancelPageScrubberTabPositioner(tab);
            try { tab.setTranslationY(0f); } catch (Throwable ignore) {}
            return;
        }

        final View host = activity.findViewById(R.id.document_host_container);
        if (host == null) return;

        final int clamped = Math.max(0, Math.min(totalPages - 1, pageIndex));
        final float frac = (totalPages <= 1) ? 0f : ((float) clamped / (float) (totalPages - 1));

        if (host.getHeight() > 0 && tab.getHeight() > 0) {
            cancelPageScrubberTabPositioner(tab);
            applyPageScrubberTabTranslation(host, tab, frac);
            return;
        }

        // Ensure layout has happened so height/location are stable; coalesce multiple calls.
        cancelPageScrubberTabPositioner(tab);
        Runnable r = new Runnable() {
            @Override public void run() {
                applyPageScrubberTabTranslation(host, tab, frac);
            }
        };
        tab.setTag(R.id.page_scrubber_tab_positioner_tag, r);
        tab.post(r);
    }

    public static void setTitle(AppCompatActivity activity, MuPDFReaderView docView, DocumentState docState) {
        if (docState == null || docView == null) return;
        int pageNumber = docView.getSelectedItemPosition();
        int totalPages = docState.pageCount();
        String pageTitle = "";
        if (totalPages > 0) {
            pageTitle = String.format(Locale.getDefault(), "%d / %d", pageNumber + 1, totalPages);
        }
        String title = docState.displayName();
        if (title == null) title = "";
        String subtitle = "";
        ActionBar actionBar = activity.getSupportActionBar();
        boolean chromeVisible = false;
        if (actionBar != null) {
            actionBar.setTitle(title);
            actionBar.setSubtitle(subtitle);
            try {
                chromeVisible = actionBar.isShowing();
            } catch (Throwable ignore) {
                chromeVisible = false;
            }
        }

        try {
            View scrubberContainer = activity.findViewById(R.id.page_scrubber_container);
            TextView indicator = activity.findViewById(R.id.page_indicator);
            android.widget.SeekBar scrubber = activity.findViewById(R.id.page_scrubber);
            TextView tab = activity.findViewById(R.id.page_scrubber_tab);
            if (indicator != null) {
                if (totalPages > 1) {
                    if (scrubberContainer != null) {
                        scrubberContainer.setVisibility(chromeVisible ? android.view.View.VISIBLE : android.view.View.GONE);
                    }
                    if (tab != null) {
                        tab.setVisibility(chromeVisible ? android.view.View.VISIBLE : android.view.View.GONE);
                        tab.setText(String.format(Locale.getDefault(), "%d", pageNumber + 1));
                        tab.setContentDescription(
                                String.format(Locale.getDefault(), "%s: %d / %d",
                                        activity.getString(R.string.page_scrubber_tab),
                                        pageNumber + 1,
                                        totalPages));
                        positionPageScrubberTab(activity, docView, tab, pageNumber, totalPages, chromeVisible);
                    }

                    // Small affordance: the page indicator is tappable (opens Navigate & View sheet).
                    String indicatorTitle = pageTitle + "  ▾";
                    indicator.setText(indicatorTitle);
                    indicator.setVisibility(chromeVisible ? android.view.View.VISIBLE : android.view.View.GONE);

                    if (scrubber != null) {
                        scrubber.setMax(Math.max(0, totalPages - 1));
                        if (scrubber.getProgress() != pageNumber) scrubber.setProgress(pageNumber);
                    }
                } else {
                    if (scrubberContainer != null) scrubberContainer.setVisibility(android.view.View.GONE);
                    indicator.setVisibility(android.view.View.GONE);
                    if (tab != null) tab.setVisibility(android.view.View.GONE);
                }
            }
        } catch (Throwable ignore) {
        }
    }
}
