package org.opendroidpdf;

import org.opendroidpdf.app.navigation.BackPressController;
import org.opendroidpdf.app.ui.ActionBarMode;

/**
 * Maps between ActionBarMode and BackPressController.Mode.
 */
public final class ActionBarBackPressModeMapper {
    private ActionBarBackPressModeMapper() {}

    public static BackPressController.Mode toBack(ActionBarMode m) {
        if (m == null) return BackPressController.Mode.Main;
        switch (m) {
            case Annot: return BackPressController.Mode.Annot;
            case Edit: return BackPressController.Mode.Edit;
            case Search: return BackPressController.Mode.Search;
            case Selection: return BackPressController.Mode.Selection;
            case Hidden: return BackPressController.Mode.Hidden;
            case AddingTextAnnot: return BackPressController.Mode.AddingTextAnnot;
            case Empty: return BackPressController.Mode.Empty;
            default: return BackPressController.Mode.Main;
        }
    }

    public static ActionBarMode toActionBar(BackPressController.Mode m) {
        if (m == null) return ActionBarMode.Main;
        switch (m) {
            case Annot: return ActionBarMode.Annot;
            case Edit: return ActionBarMode.Edit;
            case Search: return ActionBarMode.Search;
            case Selection: return ActionBarMode.Selection;
            case Hidden: return ActionBarMode.Hidden;
            case AddingTextAnnot: return ActionBarMode.AddingTextAnnot;
            case Empty: return ActionBarMode.Empty;
            default: return ActionBarMode.Main;
        }
    }
}
