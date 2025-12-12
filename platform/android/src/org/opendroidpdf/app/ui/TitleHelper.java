package org.opendroidpdf.app.ui;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFCore;

import java.util.Locale;

/**
 * Small helper to set the ActionBar title/subtitle based on doc state.
 */
public final class TitleHelper {
    private TitleHelper() {}

    public static void setTitle(AppCompatActivity activity, MuPDFReaderView docView, OpenDroidPDFCore core) {
        if (core == null || docView == null) return;
        int pageNumber = docView.getSelectedItemPosition();
        int totalPages = core.countPages();
        String title = "";
        if (totalPages > 0) {
            title = String.format(Locale.getDefault(), "%d/%d", pageNumber + 1, totalPages);
        }
        String subtitle = "";
        if (core.getFileName() != null) subtitle += core.getFileName();
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
            actionBar.setSubtitle(subtitle);
        }
    }
}

