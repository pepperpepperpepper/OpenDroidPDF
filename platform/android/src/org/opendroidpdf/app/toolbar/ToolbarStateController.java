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
        boolean isDrawingModeActive();
        boolean isErasingModeActive();
        boolean isSelectedAnnotationEditable();
        boolean isPreparingOptionsMenu();
        void invalidateOptionsMenu();
    }

    private final Host host;

    public ToolbarStateController(Host host) {
        this.host = host;
    }

    /**
     * Inflate the appropriate menu for the given mode. When feature controllers are provided,
     * let them configure the menu; otherwise inflate the legacy static resources.
     */
    public boolean onCreateOptionsMenu(@NonNull ActionBarMode mode,
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

        final boolean hasDoc = host.hasOpenDocument();
        final boolean hasDocView = host.hasDocumentView();
        final boolean canUndo = host.canUndo();
        final boolean drawing = host.isDrawingModeActive();
        final boolean erasing = host.isErasingModeActive();
        final boolean inAnnotMenu = menu.findItem(org.opendroidpdf.R.id.menu_erase) != null
                || menu.findItem(org.opendroidpdf.R.id.menu_pen_size) != null
                || menu.findItem(org.opendroidpdf.R.id.menu_ink_color) != null;

        menu.setGroupEnabled(org.opendroidpdf.R.id.menu_group_document_actions, state.groupDocumentActionsEnabled);
        menu.setGroupEnabled(org.opendroidpdf.R.id.menu_group_editor_tools, state.groupEditorToolsEnabled);
        menu.setGroupVisible(org.opendroidpdf.R.id.menu_group_editor_tools, state.groupEditorToolsVisible);

        MenuItem undo = menu.findItem(org.opendroidpdf.R.id.menu_undo);
        if (undo != null) {
            if (inAnnotMenu) {
                undo.setVisible(canUndo);
                undo.setEnabled(canUndo);
            } else {
                undo.setVisible(state.undoVisible);
                undo.setEnabled(state.undoEnabled);
            }
            if (undo.getIcon() != null) {
                undo.getIcon().mutate().setAlpha(undo.isEnabled() ? 255 : 100);
            }
        }
        MenuItem save = menu.findItem(org.opendroidpdf.R.id.menu_save);
        if (save != null) {
            save.setEnabled(state.saveEnabled);
            save.setVisible(hasDoc);
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
        MenuItem erase = menu.findItem(org.opendroidpdf.R.id.menu_erase);
        if (draw != null && erase != null) {
            // Annotation menu: draw/erase act as mutually exclusive toggles.
            if (!hasDocView) {
                draw.setVisible(false);
                draw.setEnabled(false);
                erase.setVisible(false);
                erase.setEnabled(false);
            } else if (drawing) {
                draw.setVisible(false);
                draw.setEnabled(false);
                erase.setVisible(true);
                erase.setEnabled(true);
            } else if (erasing) {
                erase.setVisible(false);
                erase.setEnabled(false);
                draw.setVisible(true);
                draw.setEnabled(true);
            } else {
                // Fallback: if the mode is unclear, keep both visible.
                draw.setVisible(true);
                draw.setEnabled(true);
                erase.setVisible(true);
                erase.setEnabled(true);
            }
        } else if (draw != null) {
            // Main menu: "draw" shortcut is available only when a document view exists.
            draw.setVisible(state.drawVisible);
            draw.setEnabled(state.drawEnabled && hasDocView);
        } else if (erase != null) {
            // Should not happen, but keep safe behavior.
            erase.setVisible(hasDocView);
            erase.setEnabled(hasDocView);
        }

        MenuItem penSize = menu.findItem(org.opendroidpdf.R.id.menu_pen_size);
        if (penSize != null) {
            boolean visible = hasDocView && drawing;
            penSize.setVisible(visible);
            penSize.setEnabled(visible);
        }
        MenuItem inkColor = menu.findItem(org.opendroidpdf.R.id.menu_ink_color);
        if (inkColor != null) {
            boolean visible = hasDocView && drawing;
            inkColor.setVisible(visible);
            inkColor.setEnabled(visible);
        }

        MenuItem edit = menu.findItem(org.opendroidpdf.R.id.menu_edit);
        if (edit != null) {
            boolean editable = host.isSelectedAnnotationEditable();
            edit.setVisible(editable);
            edit.setEnabled(editable);
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
