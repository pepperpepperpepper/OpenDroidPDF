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

    @Override public void setMode(@NonNull ActionBarMode mode) {
        switch (mode) {
            case Annot:
                // Annotation modes are owned by AnnotationModeStore (backed by DrawingService/MuPDFReaderView.Mode).
                // ActionBarModeDelegate.current() derives Annot from the store, so we do not force a mode transition here.
                return;
            case AddingTextAnnot:
                // Same as Annot: rendered from store state, not driven from ActionBarMode.
                return;
            default:
                activity.getActionBarModeDelegate().set(mode);
        }
    }
    @Override public void setModeMainIfHidden() { activity.getActionBarModeDelegate().setMainIfHidden(); }
    @Override public boolean isEdit() { return activity.getActionBarModeDelegate().isEdit(); }
    @Override public boolean isAddingTextAnnot() {
        org.opendroidpdf.app.annotation.AnnotationModeStore store = activity.getAnnotationModeStore();
        return store != null && store.isAddingTextModeActive();
    }
    @Override public boolean isSearchOrHidden() { return activity.getActionBarModeDelegate().isSearchOrHidden(); }
    @Override public void invalidateOptionsMenu() { activity.invalidateOptionsMenu(); }
    @Override public void invalidateOptionsMenuSafely() { activity.invalidateOptionsMenuSafely(); }
}
