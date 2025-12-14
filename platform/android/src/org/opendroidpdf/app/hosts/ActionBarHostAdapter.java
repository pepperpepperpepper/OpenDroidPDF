package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.ui.ActionBarHost;
import org.opendroidpdf.app.ui.ActionBarMode;

/** Adapter so action bar mode calls stay out of DocViewFactory/OpenDroidPDFActivity surface. */
public final class ActionBarHostAdapter implements ActionBarHost {
    private final OpenDroidPDFActivity activity;

    public ActionBarHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override public void setMode(@NonNull ActionBarMode mode) { activity.getActionBarModeDelegate().set(mode); }
    @Override public void setModeMainIfHidden() { activity.getActionBarModeDelegate().setMainIfHidden(); }
    @Override public boolean isEdit() { return activity.getActionBarModeDelegate().isEdit(); }
    @Override public boolean isAddingTextAnnot() { return activity.getActionBarModeDelegate().isAddingTextAnnot(); }
    @Override public boolean isSearchOrHidden() { return activity.getActionBarModeDelegate().isSearchOrHidden(); }
    @Override public void invalidateOptionsMenu() { activity.invalidateOptionsMenu(); }
    @Override public void invalidateOptionsMenuSafely() { activity.invalidateOptionsMenuSafely(); }
}
