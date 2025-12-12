package org.opendroidpdf.app.toolbar;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.annotation.AnnotationToolbarController;
import org.opendroidpdf.app.document.DocumentToolbarController;
import org.opendroidpdf.app.search.SearchToolbarController;
import org.opendroidpdf.app.ui.ActionBarMode;

/**
 * Centralizes toolbar visibility/enablement state so the activity only forwards events.
 * Owns menu inflation for each top-level mode and per-item visibility/enablement.
 */
public class ToolbarStateController {

    public interface Host {
        boolean hasOpenDocument();
        boolean hasDocumentView();
        boolean canUndo();
        boolean hasUnsavedChanges();
        boolean hasLinkTarget();
        boolean isViewingNoteDocument();
        boolean isPreparingOptionsMenu();
        void invalidateOptionsMenu();
    }

    private final Host host;

    public ToolbarStateController(Host host) {
        this.host = host;
    }

    /**
     * Mirror of the activity's action bar modes. Kept local to avoid tight coupling.
     */
    public enum Mode { Main, Annot, Edit, Search, Selection, Hidden, AddingTextAnnot, Empty }

    /** Convenience: map from the activity's ActionBarMode and inflate accordingly. */
    public boolean onCreateOptionsMenuFromActionBarMode(@NonNull ActionBarMode abMode,
                                                        @NonNull Menu menu,
                                                        @NonNull MenuInflater inflater,
                                                        @Nullable DocumentToolbarController documentToolbarController,
                                                        @Nullable AnnotationToolbarController annotationToolbarController,
                                                        @Nullable SearchToolbarController searchToolbarController) {
        Mode mode;
        switch (abMode) {
            case Main: mode = Mode.Main; break;
            case Annot: mode = Mode.Annot; break;
            case Edit: mode = Mode.Edit; break;
            case Search: mode = Mode.Search; break;
            case Selection: mode = Mode.Selection; break;
            case Hidden: mode = Mode.Hidden; break;
            case AddingTextAnnot: mode = Mode.AddingTextAnnot; break;
            case Empty:
            default: mode = Mode.Empty; break;
        }
        return onCreateOptionsMenu(mode, menu, inflater, documentToolbarController, annotationToolbarController, searchToolbarController);
    }

    /**
     * Inflate the appropriate menu for the given mode. When feature controllers are provided,
     * let them configure the menu; otherwise inflate the legacy static resources.
     */
    public boolean onCreateOptionsMenu(@NonNull Mode mode,
                                       @NonNull Menu menu,
                                       @NonNull MenuInflater inflater,
                                       @Nullable DocumentToolbarController documentToolbarController,
                                       @Nullable AnnotationToolbarController annotationToolbarController,
                                       @Nullable SearchToolbarController searchToolbarController) {
        switch (mode) {
            case Main:
                if (documentToolbarController != null) {
                    documentToolbarController.inflateMainMenu(menu, inflater);
                } else {
                    inflater.inflate(org.opendroidpdf.R.menu.main_menu, menu);
                }
                if (annotationToolbarController != null) {
                    annotationToolbarController.prepareMainMenuShortcuts(menu);
                }
                break;
            case Selection:
                if (annotationToolbarController != null) {
                    annotationToolbarController.inflateSelectionMenu(menu, inflater);
                } else {
                    inflater.inflate(org.opendroidpdf.R.menu.selection_menu, menu);
                }
                break;
            case Annot:
                if (annotationToolbarController != null) {
                    annotationToolbarController.inflateAnnotationMenu(menu, inflater);
                } else {
                    inflater.inflate(org.opendroidpdf.R.menu.annot_menu, menu);
                }
                break;
            case Edit:
                if (annotationToolbarController != null) {
                    annotationToolbarController.inflateEditMenu(menu, inflater);
                } else {
                    inflater.inflate(org.opendroidpdf.R.menu.edit_menu, menu);
                }
                break;
            case Search:
                if (searchToolbarController != null) {
                    searchToolbarController.inflateSearchMenu(menu, inflater);
                } else {
                    inflater.inflate(org.opendroidpdf.R.menu.search_menu, menu);
                }
                // fallthrough to Hidden: always overlay with empty container to avoid stale items
            case Hidden:
                inflater.inflate(org.opendroidpdf.R.menu.empty_menu, menu);
                break;
            case AddingTextAnnot:
                if (annotationToolbarController != null) {
                    annotationToolbarController.inflateAddTextAnnotationMenu(menu, inflater);
                } else {
                    inflater.inflate(org.opendroidpdf.R.menu.add_text_annot_menu, menu);
                }
                break;
            case Empty:
            default:
                inflater.inflate(org.opendroidpdf.R.menu.empty_menu, menu);
                break;
        }
        // Overlay debug-only actions
        if (org.opendroidpdf.BuildConfig.DEBUG) {
            inflater.inflate(org.opendroidpdf.R.menu.debug_menu, menu);
        }
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        if (menu == null) return false;

        MenuStateEvaluator.Inputs inputs = new MenuStateEvaluator.Inputs(
                host.hasOpenDocument(),
                host.canUndo(),
                host.hasUnsavedChanges(),
                host.hasLinkTarget());
        MenuState state = MenuStateEvaluator.compute(inputs);

        menu.setGroupEnabled(org.opendroidpdf.R.id.menu_group_document_actions, state.groupDocumentActionsEnabled);
        menu.setGroupEnabled(org.opendroidpdf.R.id.menu_group_editor_tools, state.groupEditorToolsEnabled);
        menu.setGroupVisible(org.opendroidpdf.R.id.menu_group_editor_tools, state.groupEditorToolsVisible);

        MenuItem undo = menu.findItem(org.opendroidpdf.R.id.menu_undo);
        if (undo != null) {
            undo.setVisible(state.undoVisible);
            undo.setEnabled(state.undoEnabled);
        }
        MenuItem save = menu.findItem(org.opendroidpdf.R.id.menu_save);
        if (save != null) {
            save.setEnabled(state.saveEnabled);
            save.setVisible(host.hasOpenDocument());
        }
        MenuItem linkBack = menu.findItem(org.opendroidpdf.R.id.menu_linkback);
        if (linkBack != null) {
            linkBack.setVisible(state.linkBackVisible);
            linkBack.setEnabled(state.linkBackEnabled);
        }
        MenuItem search = menu.findItem(org.opendroidpdf.R.id.menu_search);
        if (search != null) {
            search.setVisible(state.searchVisible);
            search.setEnabled(state.searchEnabled);
        }
        MenuItem draw = menu.findItem(org.opendroidpdf.R.id.menu_draw);
        if (draw != null) {
            draw.setVisible(state.drawVisible);
            draw.setEnabled(state.drawEnabled);
        }
        MenuItem addText = menu.findItem(org.opendroidpdf.R.id.menu_add_text_annot);
        if (addText != null) {
            addText.setVisible(state.addTextVisible);
            addText.setEnabled(state.addTextEnabled);
        }
        MenuItem print = menu.findItem(org.opendroidpdf.R.id.menu_print);
        if (print != null) {
            boolean visible = host.hasOpenDocument();
            print.setVisible(visible);
            print.setEnabled(state.printEnabled && visible);
        }
        MenuItem share = menu.findItem(org.opendroidpdf.R.id.menu_share);
        if (share != null) {
            boolean visible = host.hasOpenDocument();
            share.setVisible(visible);
            share.setEnabled(state.shareEnabled && visible);
        }
        MenuItem addPage = menu.findItem(org.opendroidpdf.R.id.menu_addpage);
        if (addPage != null) {
            boolean visible = host.hasOpenDocument();
            addPage.setVisible(visible);
            addPage.setEnabled(visible);
        }
        MenuItem goTo = menu.findItem(org.opendroidpdf.R.id.menu_gotopage);
        if (goTo != null) {
            boolean visible = host.hasOpenDocument();
            goTo.setVisible(visible);
            goTo.setEnabled(visible);
        }
        MenuItem fullscreen = menu.findItem(org.opendroidpdf.R.id.menu_fullscreen);
        if (fullscreen != null) {
            boolean visible = host.hasDocumentView();
            fullscreen.setVisible(visible);
            fullscreen.setEnabled(visible);
        }
        MenuItem deleteNote = menu.findItem(org.opendroidpdf.R.id.menu_delete_note);
        if (deleteNote != null) {
            boolean visible = host.isViewingNoteDocument();
            deleteNote.setVisible(visible);
            deleteNote.setEnabled(visible);
        }
        return true;
    }

    public void notifyStateChanged() {
        host.invalidateOptionsMenu();
    }
}
