package org.opendroidpdf.app.ui;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;

/**
 * Keeps ActionBarMode transitions and state accessors out of the activity body.
 */
public final class ActionBarModeDelegate {
    private ActionBarMode mode = ActionBarMode.Empty;

    public ActionBarModeDelegate() {}

    public ActionBarMode current() { return mode; }

    public void set(@NonNull ActionBarMode newMode) { mode = newMode; }

    public void setMain() { mode = ActionBarMode.Main; }
    public void setAnnot() { mode = ActionBarMode.Annot; }
    public void setSelection() { mode = ActionBarMode.Selection; }
    public void setAddingTextAnnot() { mode = ActionBarMode.AddingTextAnnot; }
    public void setEdit() { mode = ActionBarMode.Edit; }
    public void setSearch() { mode = ActionBarMode.Search; }
    public void setHidden() { mode = ActionBarMode.Hidden; }
    public void setMainIfHidden() { if (mode == ActionBarMode.Hidden) mode = ActionBarMode.Main; }

    public boolean isEdit() { return mode == ActionBarMode.Edit; }
    public boolean isAddingTextAnnot() { return mode == ActionBarMode.AddingTextAnnot; }
    public boolean isSearchOrHidden() { return mode == ActionBarMode.Search || mode == ActionBarMode.Hidden; }
}
