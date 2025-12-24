package org.opendroidpdf.app.ui;

/** Adapter to set title without keeping helper methods on the activity. */
public final class TitleHostAdapter {
    private final UiStateDelegate uiStateDelegate;

    public TitleHostAdapter(UiStateDelegate uiStateDelegate) {
        this.uiStateDelegate = uiStateDelegate;
    }

    public void setTitle() {
        if (uiStateDelegate != null) uiStateDelegate.setTitle();
    }
}
