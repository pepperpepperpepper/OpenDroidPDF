package org.opendroidpdf.app.toolbar;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.app.annotation.TextAnnotationClipboard;
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
        boolean canRedo();
        boolean hasUnsavedChanges();
        boolean hasLinkTarget();
        /** Whether the currently open document is a PDF document. */
        boolean isPdfDocument();
        /** Whether the currently open document is an EPUB document. */
        boolean isEpubDocument();
        /** Whether the current document can be saved back to its current URI. */
        boolean canSaveToCurrentUri();
        boolean isViewingNoteDocument();
        boolean isDrawingModeActive();
        boolean isErasingModeActive();
        boolean isFormFieldHighlightEnabled();
        boolean areCommentsVisible();
        /** Whether sidecar notes are displayed as marker-only “sticky notes”. */
        boolean areSidecarNotesStickyModeEnabled();
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
                host.canRedo(),
                host.hasUnsavedChanges(),
                host.hasLinkTarget(),
                host.isPdfDocument(),
                host.isEpubDocument(),
                host.canSaveToCurrentUri());
        MenuState state = MenuStateEvaluator.compute(inputs);

        final boolean hasDoc = host.hasOpenDocument();
        final boolean isPdf = host.isPdfDocument();
        final boolean isEpub = host.isEpubDocument();
        final boolean editorDoc = isPdf || isEpub;
        final boolean hasDocView = host.hasDocumentView();
        final boolean canUndo = host.canUndo();
        final boolean canRedo = host.canRedo();
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
        MenuItem redo = menu.findItem(org.opendroidpdf.R.id.menu_redo);
        if (redo != null) {
            if (inAnnotMenu) {
                redo.setVisible(canRedo);
                redo.setEnabled(canRedo);
            } else {
                redo.setVisible(state.redoVisible);
                redo.setEnabled(state.redoEnabled);
            }
            if (redo.getIcon() != null) {
                redo.getIcon().mutate().setAlpha(redo.isEnabled() ? 255 : 100);
            }
        }
        MenuItem save = menu.findItem(org.opendroidpdf.R.id.menu_save);
        if (save != null) {
            save.setEnabled(state.saveEnabled);
            save.setVisible(state.saveEnabled);
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
            if (!editorDoc || !hasDocView) {
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
            draw.setVisible(state.drawVisible && editorDoc);
            draw.setEnabled(state.drawEnabled && hasDocView && editorDoc);
        } else if (erase != null) {
            // Should not happen, but keep safe behavior.
            erase.setVisible(hasDocView && editorDoc);
            erase.setEnabled(hasDocView && editorDoc);
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
            addText.setVisible(state.addTextVisible && editorDoc);
            addText.setEnabled(state.addTextEnabled && editorDoc);
        }

        MenuItem pasteTextAnnot = menu.findItem(org.opendroidpdf.R.id.menu_paste_text_annot);
        if (pasteTextAnnot != null) {
            boolean visible = hasDocView && editorDoc;
            pasteTextAnnot.setVisible(visible);
            pasteTextAnnot.setEnabled(visible && TextAnnotationClipboard.hasPayload());
        }

        MenuItem fillSign = menu.findItem(org.opendroidpdf.R.id.menu_fill_sign);
        if (fillSign != null) {
            boolean visible = hasDocView && editorDoc && isPdf;
            fillSign.setVisible(visible);
            fillSign.setEnabled(visible);
        }

        MenuItem forms = menu.findItem(org.opendroidpdf.R.id.menu_forms);
        if (forms != null) {
            boolean visible = hasDocView && isPdf;
            forms.setVisible(visible);
            forms.setEnabled(visible);
            boolean enabled = host.isFormFieldHighlightEnabled();
            forms.setChecked(enabled);
            if (forms.getIcon() != null) {
                forms.getIcon().mutate().setAlpha(enabled ? 255 : 120);
            }
        }
        boolean formsNavVisible = hasDocView && isPdf && host.isFormFieldHighlightEnabled();
        MenuItem formPrev = menu.findItem(org.opendroidpdf.R.id.menu_form_previous);
        if (formPrev != null) {
            formPrev.setVisible(formsNavVisible);
            formPrev.setEnabled(formsNavVisible);
        }
        MenuItem formNext = menu.findItem(org.opendroidpdf.R.id.menu_form_next);
        if (formNext != null) {
            formNext.setVisible(formsNavVisible);
            formNext.setEnabled(formsNavVisible);
        }
        MenuItem print = menu.findItem(org.opendroidpdf.R.id.menu_print);
        if (print != null) {
            boolean visible = host.hasOpenDocument() && (isPdf || isEpub);
            print.setVisible(visible);
            print.setEnabled(state.printEnabled && visible);
        }
        MenuItem share = menu.findItem(org.opendroidpdf.R.id.menu_share);
        if (share != null) {
            boolean visible = host.hasOpenDocument() && (isPdf || isEpub);
            share.setVisible(visible);
            share.setEnabled(state.shareEnabled && visible);
        }
        MenuItem shareLinear = menu.findItem(org.opendroidpdf.R.id.menu_share_linearized);
        if (shareLinear != null) {
            boolean visible = host.hasOpenDocument() && isPdf && BuildConfig.ENABLE_QPDF_OPS;
            shareLinear.setVisible(visible);
            shareLinear.setEnabled(state.shareEnabled && visible);
        }
        MenuItem shareEncrypted = menu.findItem(org.opendroidpdf.R.id.menu_share_encrypted);
        if (shareEncrypted != null) {
            boolean visible = host.hasOpenDocument() && isPdf && BuildConfig.ENABLE_QPDF_OPS;
            shareEncrypted.setVisible(visible);
            shareEncrypted.setEnabled(state.shareEnabled && visible);
        }
        MenuItem shareFlattened = menu.findItem(org.opendroidpdf.R.id.menu_share_flattened);
        if (shareFlattened != null) {
            boolean visible = host.hasOpenDocument() && isPdf;
            shareFlattened.setVisible(visible);
            shareFlattened.setEnabled(state.shareEnabled && visible);
        }
        MenuItem saveLinear = menu.findItem(org.opendroidpdf.R.id.menu_save_linearized);
        if (saveLinear != null) {
            boolean visible = host.hasOpenDocument() && isPdf && BuildConfig.ENABLE_QPDF_OPS;
            saveLinear.setVisible(visible);
            saveLinear.setEnabled(state.saveEnabled && visible);
        }
        MenuItem saveEncrypted = menu.findItem(org.opendroidpdf.R.id.menu_save_encrypted);
        if (saveEncrypted != null) {
            boolean visible = host.hasOpenDocument() && isPdf && BuildConfig.ENABLE_QPDF_OPS;
            saveEncrypted.setVisible(visible);
            saveEncrypted.setEnabled(state.saveEnabled && visible);
        }
        MenuItem exportAnnotations = menu.findItem(org.opendroidpdf.R.id.menu_export_annotations);
        if (exportAnnotations != null) {
            // Sidecar docs (EPUB + read-only PDFs) can export a backup/sync bundle of annotations.
            boolean visible = host.hasOpenDocument() && (isEpub || (isPdf && !host.canSaveToCurrentUri()));
            exportAnnotations.setVisible(visible);
            exportAnnotations.setEnabled(visible);
        }
        MenuItem importAnnotations = menu.findItem(org.opendroidpdf.R.id.menu_import_annotations);
        if (importAnnotations != null) {
            // Sidecar docs (EPUB + read-only PDFs) can import a backup/sync bundle of annotations.
            boolean visible = host.hasOpenDocument() && (isEpub || (isPdf && !host.canSaveToCurrentUri()));
            importAnnotations.setVisible(visible);
            importAnnotations.setEnabled(visible);
        }
        MenuItem addPage = menu.findItem(org.opendroidpdf.R.id.menu_addpage);
        if (addPage != null) {
            boolean visible = host.hasOpenDocument() && isPdf;
            addPage.setVisible(visible);
            addPage.setEnabled(visible);
        }
        MenuItem goTo = menu.findItem(org.opendroidpdf.R.id.menu_gotopage);
        if (goTo != null) {
            boolean visible = host.hasOpenDocument();
            goTo.setVisible(visible);
            goTo.setEnabled(visible);
        }
        MenuItem comments = menu.findItem(org.opendroidpdf.R.id.menu_comments);
        if (comments != null) {
            boolean visible = host.hasOpenDocument();
            comments.setVisible(visible);
            comments.setEnabled(visible);
        }
        MenuItem showComments = menu.findItem(org.opendroidpdf.R.id.menu_show_comments);
        if (showComments != null) {
            boolean visible = host.hasOpenDocument();
            showComments.setVisible(visible);
            showComments.setEnabled(visible);
            boolean enabled = host.areCommentsVisible();
            showComments.setChecked(enabled);
        }
        MenuItem stickyNotes = menu.findItem(org.opendroidpdf.R.id.menu_sticky_notes);
        if (stickyNotes != null) {
            // Sidecar docs (EPUB + read-only PDFs) can optionally render notes as marker-only.
            boolean visible = host.hasOpenDocument() && (isEpub || (isPdf && !host.canSaveToCurrentUri()));
            stickyNotes.setVisible(visible);
            stickyNotes.setEnabled(visible);
            stickyNotes.setChecked(host.areSidecarNotesStickyModeEnabled());
        }
        MenuItem commentPrev = menu.findItem(org.opendroidpdf.R.id.menu_comment_previous);
        if (commentPrev != null) {
            boolean visible = host.hasDocumentView() && host.isSelectedAnnotationEditable();
            commentPrev.setVisible(visible);
            commentPrev.setEnabled(visible);
        }
        MenuItem commentNext = menu.findItem(org.opendroidpdf.R.id.menu_comment_next);
        if (commentNext != null) {
            boolean visible = host.hasDocumentView() && host.isSelectedAnnotationEditable();
            commentNext.setVisible(visible);
            commentNext.setEnabled(visible);
        }
        MenuItem fullscreen = menu.findItem(org.opendroidpdf.R.id.menu_fullscreen);
        if (fullscreen != null) {
            boolean visible = host.hasDocumentView();
            fullscreen.setVisible(visible);
            fullscreen.setEnabled(visible);
        }
        MenuItem readingSettings = menu.findItem(org.opendroidpdf.R.id.menu_reading_settings);
        if (readingSettings != null) {
            readingSettings.setVisible(state.readingSettingsVisible && isEpub);
            readingSettings.setEnabled(state.readingSettingsEnabled && isEpub);
        }
        MenuItem deleteNote = menu.findItem(org.opendroidpdf.R.id.menu_delete_note);
        if (deleteNote != null) {
            boolean visible = host.isViewingNoteDocument();
            deleteNote.setVisible(visible);
            deleteNote.setEnabled(visible);
        }
        if (org.opendroidpdf.BuildConfig.DEBUG) {
            MenuItem qpdfSmoke = menu.findItem(org.opendroidpdf.R.id.menu_debug_qpdf_smoke);
            if (qpdfSmoke != null) qpdfSmoke.setVisible(hasDoc && isPdf);
            MenuItem pdfboxFlatten = menu.findItem(org.opendroidpdf.R.id.menu_debug_pdfbox_flatten);
            if (pdfboxFlatten != null) {
                boolean available = org.opendroidpdf.core.PdfBoxFacade.isAvailable();
                pdfboxFlatten.setVisible(hasDoc && isPdf && available);
                pdfboxFlatten.setEnabled(available);
            }
        }
        return true;
    }

    public void notifyStateChanged() {
        host.invalidateOptionsMenu();
    }
}
