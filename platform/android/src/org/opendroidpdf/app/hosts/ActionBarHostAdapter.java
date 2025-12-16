package org.opendroidpdf.app.hosts;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.app.ui.ActionBarHost;
import org.opendroidpdf.app.ui.ActionBarMode;

/** Adapter so action bar mode calls stay out of DocViewFactory/OpenDroidPDFActivity surface. */
public final class ActionBarHostAdapter implements ActionBarHost {
    private final OpenDroidPDFActivity activity;
    private boolean applying;

    public ActionBarHostAdapter(@NonNull OpenDroidPDFActivity activity) {
        this.activity = activity;
    }

    @Override public void setMode(@NonNull ActionBarMode mode) {
        if (applying) return; // prevent recursive feedback loops between store->docView->store
        applying = true;
        org.opendroidpdf.app.annotation.AnnotationModeStore store = activity.getAnnotationModeStore();
        switch (mode) {
            case Annot:
                if (store != null) store.enterDrawingMode();
                applying = false;
                return;
            case AddingTextAnnot:
                if (store != null) store.enterAddingTextMode();
                applying = false;
                return;
            default:
                if (store != null) store.enterViewingMode();
                activity.getActionBarModeDelegate().set(mode);
        }
        applying = false;
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
