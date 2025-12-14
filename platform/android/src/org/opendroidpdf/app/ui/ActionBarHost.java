package org.opendroidpdf.app.ui;

import androidx.annotation.NonNull;

/** Narrow interface so DocViewFactory doesn't need the whole activity. */
public interface ActionBarHost {
    void setMode(@NonNull ActionBarMode mode);
    void setModeMainIfHidden();
    boolean isEdit();
    boolean isAddingTextAnnot();
    boolean isSearchOrHidden();
    void invalidateOptionsMenu();
    void invalidateOptionsMenuSafely();
}
