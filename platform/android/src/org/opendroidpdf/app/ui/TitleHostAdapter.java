package org.opendroidpdf.app.ui;

import org.opendroidpdf.OpenDroidPDFActivity;

/** Adapter to set title without keeping helper methods on the activity. */
public final class TitleHostAdapter {
    private final OpenDroidPDFActivity activity;

    public TitleHostAdapter(OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    public void setTitle() {
        org.opendroidpdf.app.ui.UiStateDelegate ui = activity.getUiStateDelegate();
        if (ui != null) ui.setTitle();
    }
}
