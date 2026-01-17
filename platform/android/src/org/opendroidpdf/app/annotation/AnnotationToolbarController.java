package org.opendroidpdf.app.annotation;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuItemCompat;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.PageView;
import org.opendroidpdf.R;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Centralizes toolbar/menu wiring for annotation-related interactions so the activity can
 * delegate per-feature configuration instead of hosting all bindings itself.
 */
public class AnnotationToolbarController {

    public interface Host {
        @NonNull Context getContext();
        void showAnnotationInfo(@NonNull String message);
        void showPenSizeDialog();
        void showInkColorDialog();
        void showTextStyleDialog();
        void requestSaveDialog();
        void requestCommentsList();
        void requestTextSelectionMode();
        void requestFillSign();
        boolean isPdfDocument();
        boolean isSelectedAnnotationEditable();
        @Nullable PageView getActivePageView();
        boolean hasDocumentView();
        void notifyStrokeCountChanged(int strokeCount);
        boolean finalizePendingInk();
        void cancelAnnotationMode();
        void confirmAnnotationChanges();
    }

    private final Host host;
    private final AnnotationModeStore modeStore;

    public AnnotationToolbarController(@NonNull Host host,
                                       @NonNull AnnotationModeStore modeStore) {
        this.host = host;
        this.modeStore = modeStore;
    }

    public void inflateAnnotationMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.annot_menu, menu);
        configurePenShortcut(menu.findItem(R.id.menu_draw));
    }

    public void inflateSelectionMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.selection_menu, menu);
    }

    public void inflateAddTextAnnotationMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.add_text_annot_menu, menu);
    }

    public void inflateEditMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.edit_menu, menu);
        final PageView pageView = host.getActivePageView();
        MenuItem resize = menu.findItem(R.id.menu_resize);
        if (resize != null && pageView instanceof MuPDFPageView) {
            boolean enabled = false;
            try { enabled = ((MuPDFPageView) pageView).textResizeHandlesEnabled(); } catch (Throwable ignore) { enabled = false; }
            resize.setChecked(enabled);
            if (resize.getIcon() != null) {
                resize.getIcon().mutate().setAlpha(enabled ? 255 : 120);
            }
        }
        MenuItem duplicate = menu.findItem(R.id.menu_duplicate_text);
        if (duplicate != null) {
            boolean visible = false;
            if (pageView instanceof MuPDFPageView) {
                MuPDFPageView pv = (MuPDFPageView) pageView;
                Annotation.Type t = null;
                try { t = pv.selectedAnnotationType(); } catch (Throwable ignore) { t = null; }
                visible = (t == Annotation.Type.FREETEXT);
                if (!visible) {
                    try {
                        org.opendroidpdf.app.selection.SidecarSelectionController.Selection sel = pv.selectedSidecarSelectionOrNull();
                        visible = sel != null && sel.kind == org.opendroidpdf.app.selection.SidecarSelectionController.Kind.NOTE;
                    } catch (Throwable ignore) {
                        visible = false;
                    }
                }
            }
            duplicate.setVisible(visible);
            duplicate.setEnabled(visible);
            if (duplicate.getIcon() != null) {
                duplicate.getIcon().mutate().setAlpha(visible ? 255 : 100);
            }
        }

        MenuItem copyAnnot = menu.findItem(R.id.menu_copy_text_annot);
        if (copyAnnot != null) {
            boolean visible = false;
            if (pageView instanceof MuPDFPageView) {
                MuPDFPageView pv = (MuPDFPageView) pageView;
                Annotation.Type t = null;
                try { t = pv.selectedAnnotationType(); } catch (Throwable ignore) { t = null; }
                visible = (t == Annotation.Type.FREETEXT);
                if (!visible) {
                    try {
                        org.opendroidpdf.app.selection.SidecarSelectionController.Selection sel = pv.selectedSidecarSelectionOrNull();
                        visible = sel != null && sel.kind == org.opendroidpdf.app.selection.SidecarSelectionController.Kind.NOTE;
                    } catch (Throwable ignore) {
                        visible = false;
                    }
                }
            }
            copyAnnot.setVisible(visible);
            copyAnnot.setEnabled(visible);
        }
    }

    public void prepareMainMenuShortcuts(@NonNull Menu menu) {
        configurePenShortcut(menu.findItem(R.id.menu_draw));
    }

    /**
     * Handles annotation-related toolbar/menu actions. Returns true if the event was consumed.
     */
    public boolean handleOptionsItem(@NonNull MenuItem item) {
        final PageView pageView = host.getActivePageView();
        switch (item.getItemId()) {
            case R.id.menu_undo:
                if (pageView != null) {
                    pageView.undoDraw();
                    host.notifyStrokeCountChanged(pageView.getDrawingSize());
                }
                return true;
            case R.id.menu_redo:
                if (pageView != null) {
                    try { pageView.redoDraw(); } catch (Throwable ignore) {}
                    host.notifyStrokeCountChanged(pageView.getDrawingSize());
                }
                return true;
            case R.id.menu_edit:
                if (pageView instanceof MuPDFPageView) {
                    MuPDFPageView muPageView = (MuPDFPageView) pageView;
                    muPageView.editSelectedAnnotation();
                    // Editing embedded ink transitions into drawing mode (so the stroke can be adjusted).
                    // Editing sidecar notes (EPUB / read-only PDFs) should keep the current mode.
                    Annotation.Type selectedType = muPageView.selectedAnnotationType();
                    if (selectedType == Annotation.Type.INK) {
                        modeStore.enterDrawingMode();
                    }
                }
                return true;
            case R.id.menu_move:
                if (pageView instanceof MuPDFPageView) {
                    MuPDFPageView muPageView = (MuPDFPageView) pageView;
                    Annotation.Type selectedType = null;
                    try { selectedType = muPageView.selectedAnnotationType(); } catch (Throwable ignore) {}
                    boolean movable = (selectedType == Annotation.Type.FREETEXT || selectedType == Annotation.Type.TEXT);
                    host.showAnnotationInfo(host.getContext().getString(movable
                            ? R.string.tap_to_move_annotation
                            : R.string.select_text_annot_to_move));
                }
                return true;
            case R.id.menu_resize:
                if (pageView instanceof MuPDFPageView) {
                    MuPDFPageView muPageView = (MuPDFPageView) pageView;
                    boolean wasEnabled = false;
                    try { wasEnabled = muPageView.textResizeHandlesEnabled(); } catch (Throwable ignore) { wasEnabled = false; }
                    boolean ok = false;
                    try { ok = muPageView.toggleTextResizeHandlesEnabled(); } catch (Throwable ignore) { ok = false; }
                    if (!ok) {
                        host.showAnnotationInfo(host.getContext().getString(R.string.select_text_annot_to_resize));
                        return true;
                    }
                    boolean nowEnabled = false;
                    try { nowEnabled = muPageView.textResizeHandlesEnabled(); } catch (Throwable ignore) { nowEnabled = false; }
                    try { item.setChecked(nowEnabled); } catch (Throwable ignore) {}
                    if (item.getIcon() != null) {
                        try { item.getIcon().mutate().setAlpha(nowEnabled ? 255 : 120); } catch (Throwable ignore) {}
                    }
                    if (nowEnabled && !wasEnabled) {
                        host.showAnnotationInfo(host.getContext().getString(R.string.tap_to_resize_annotation));
                    }
                }
                return true;
            case R.id.menu_text_style:
                host.showTextStyleDialog();
                return true;
            case R.id.menu_duplicate_text:
                if (pageView instanceof MuPDFPageView) {
                    MuPDFPageView muPageView = (MuPDFPageView) pageView;
                    boolean ok = false;
                    try { ok = muPageView.textAnnotationDelegate().duplicateSelectedTextAnnotation(); } catch (Throwable ignore) { ok = false; }
                    if (!ok) {
                        host.showAnnotationInfo(host.getContext().getString(R.string.select_text_annot_to_move));
                    }
                    return true;
                }
                host.showAnnotationInfo(host.getContext().getString(R.string.select_text_annot_to_move));
                return true;
            case R.id.menu_copy_text_annot:
                if (pageView instanceof MuPDFPageView) {
                    MuPDFPageView muPageView = (MuPDFPageView) pageView;
                    boolean ok = false;
                    try { ok = muPageView.textAnnotationDelegate().copySelectedTextAnnotationToClipboard(); } catch (Throwable ignore) { ok = false; }
                    host.showAnnotationInfo(ok
                            ? host.getContext().getString(R.string.copied_to_clipboard)
                            : host.getContext().getString(R.string.select_text_annot_to_style));
                    try {
                        if (host.getContext() instanceof android.app.Activity) {
                            ((android.app.Activity) host.getContext()).invalidateOptionsMenu();
                        }
                    } catch (Throwable ignore) {}
                    return true;
                }
                host.showAnnotationInfo(host.getContext().getString(R.string.select_text_annot_to_style));
                return true;
            case R.id.menu_paste_text_annot:
                return pasteTextAnnotationFromClipboard(pageView);
            case R.id.menu_add_text_annot:
                return enterAddTextMode(pageView);
            case R.id.menu_erase:
                // The drawing service owns the pending-ink lifecycle; commit any in-progress ink
                // before switching to eraser so all strokes are persisted and erasable.
                if (!host.finalizePendingInk()) {
                    host.showAnnotationInfo(host.getContext().getString(R.string.cannot_commit_ink));
                    return true;
                }
                modeStore.enterErasingMode();
                return true;
            case R.id.menu_draw:
                modeStore.enterDrawingMode();
                return true;
            case R.id.menu_annotate:
                showAnnotateSheet();
                return true;
            case R.id.menu_pen_size:
                host.showPenSizeDialog();
                return true;
            case R.id.menu_ink_color:
                host.showInkColorDialog();
                return true;
            case R.id.menu_save:
                host.requestSaveDialog();
                return true;
            case R.id.menu_highlight:
                return markupSelection(pageView, Annotation.Type.HIGHLIGHT);
            case R.id.menu_underline:
                return markupSelection(pageView, Annotation.Type.UNDERLINE);
            case R.id.menu_squiggly:
                return markupSelection(pageView, Annotation.Type.SQUIGGLY);
            case R.id.menu_replace:
                return replaceSelection(pageView);
            case R.id.menu_delete_text:
                return markupSelection(pageView, Annotation.Type.STRIKEOUT);
            case R.id.menu_strikeout:
                return markupSelection(pageView, Annotation.Type.STRIKEOUT);
            case R.id.menu_caret:
                return markupSelection(pageView, Annotation.Type.CARET);
            case R.id.menu_copytext:
                if (pageView != null) {
                    if (pageView.hasSelection()) {
                        boolean success = pageView.copySelection();
                        host.showAnnotationInfo(success
                                ? host.getContext().getString(R.string.copied_to_clipboard)
                                : host.getContext().getString(R.string.no_text_selected));
                    } else {
                        host.showAnnotationInfo(host.getContext().getString(R.string.select_text));
                    }
                }
                return true;
            case R.id.menu_delete_annotation:
                if (pageView != null) {
                    confirmDeleteAnnotation(pageView);
                    return true;
                }
                host.showAnnotationInfo(host.getContext().getString(R.string.not_supported));
                return true;
            case R.id.menu_cancel:
                if (pageView != null && (modeStore.isDrawingModeActive() || modeStore.isErasingModeActive())
                        && pageView.getDrawingSize() > 0) {
                    confirmDiscardInk(pageView);
                    return true;
                }
                host.cancelAnnotationMode();
                return true;
            case R.id.menu_accept:
                host.confirmAnnotationChanges();
                return true;
            default:
                return false;
        }
    }

    public void showAnnotateSheet() {
        Context ctx = host.getContext();
        if (!(ctx instanceof AppCompatActivity)) return;
        AppCompatActivity activity = (AppCompatActivity) ctx;
        if (!host.hasDocumentView()) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_annotate_sheet, null);
        dialog.setContentView(root);

        View draw = root.findViewById(R.id.annotate_action_draw);
        if (draw != null) {
            draw.setOnClickListener(v -> {
                dialog.dismiss();
                modeStore.enterDrawingMode();
            });
        }

        View erase = root.findViewById(R.id.annotate_action_erase);
        if (erase != null) {
            erase.setOnClickListener(v -> {
                dialog.dismiss();
                if (!host.finalizePendingInk()) {
                    host.showAnnotationInfo(activity.getString(R.string.cannot_commit_ink));
                    return;
                }
                modeStore.enterErasingMode();
            });
        }

        View markUpText = root.findViewById(R.id.annotate_action_mark_up_text);
        if (markUpText != null) {
            markUpText.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestTextSelectionMode();
                host.showAnnotationInfo(activity.getString(R.string.tap_text_to_select));
            });
        }

        View addText = root.findViewById(R.id.annotate_action_add_text);
        if (addText != null) {
            addText.setOnClickListener(v -> {
                dialog.dismiss();
                enterAddTextMode(host.getActivePageView());
            });
        }

        View pasteText = root.findViewById(R.id.annotate_action_paste_text);
        if (pasteText != null) {
            boolean enabled = host.hasDocumentView() && TextAnnotationClipboard.hasPayload();
            pasteText.setEnabled(enabled);
            pasteText.setAlpha(enabled ? 1f : 0.5f);
            pasteText.setOnClickListener(v -> {
                dialog.dismiss();
                pasteTextAnnotationFromClipboard(host.getActivePageView());
            });
        }

        View fillSign = root.findViewById(R.id.annotate_action_fill_sign);
        if (fillSign != null) {
            boolean visible = host.isPdfDocument();
            fillSign.setVisibility(visible ? View.VISIBLE : View.GONE);
            fillSign.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestFillSign();
            });
        }

        View annotations = root.findViewById(R.id.annotate_action_annotations);
        if (annotations != null) {
            annotations.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestCommentsList();
            });
        }

        dialog.show();
    }

    public void configurePenShortcut(@Nullable MenuItem drawItem) {
        if (drawItem == null) {
            return;
        }
        final View actionView = MenuItemCompat.getActionView(drawItem);
        if (actionView == null) {
            return;
        }
        ImageButton drawButton = actionView.findViewById(R.id.draw_image_button);
        if (drawButton == null) {
            return;
        }
        drawButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                modeStore.enterDrawingMode();
            }
        });
        drawButton.setOnLongClickListener(v -> true); // long-press should not trigger commands
    }

    private boolean pasteTextAnnotationFromClipboard(@Nullable PageView pageView) {
        if (pageView instanceof MuPDFPageView) {
            MuPDFPageView muPageView = (MuPDFPageView) pageView;
            boolean ok = false;
            try { ok = muPageView.textAnnotationDelegate().pasteTextAnnotationFromClipboard(); } catch (Throwable ignore) { ok = false; }
            if (!ok) host.showAnnotationInfo(host.getContext().getString(R.string.not_supported));
            try {
                if (host.getContext() instanceof android.app.Activity) {
                    ((android.app.Activity) host.getContext()).invalidateOptionsMenu();
                }
            } catch (Throwable ignore) {}
            return true;
        }
        host.showAnnotationInfo(host.getContext().getString(R.string.not_supported));
        return true;
    }

    private boolean enterAddTextMode(@Nullable PageView pageView) {
        // Ensure "add text" does not accidentally replace an existing selection when the
        // text editor commits (it may replace a selected FreeText when no stable object id exists).
        if (pageView != null) {
            pageView.deselectText();
            if (pageView instanceof MuPDFPageView) {
                try { ((MuPDFPageView) pageView).deselectAnnotation(); } catch (Throwable ignore) {}
            }
        }
        modeStore.enterAddingTextMode();
        host.showAnnotationInfo(host.getContext().getString(R.string.tap_to_add_annotation));
        return true;
    }

    private boolean markupSelection(@Nullable PageView pageView, @NonNull Annotation.Type type) {
        if (pageView == null) {
            return false;
        }
        if (pageView.hasSelection()) {
            pageView.markupSelection(type);
        } else {
            host.showAnnotationInfo(host.getContext().getString(R.string.select_text));
        }
        return true;
    }

    private boolean replaceSelection(@Nullable PageView pageView) {
        if (pageView == null) {
            return false;
        }
        if (pageView.hasSelection()) {
            if (pageView instanceof MuPDFPageView) {
                ((MuPDFPageView) pageView).replaceSelection();
            } else {
                pageView.markupSelection(Annotation.Type.STRIKEOUT);
            }
        } else {
            host.showAnnotationInfo(host.getContext().getString(R.string.select_text));
        }
        return true;
    }

    private void confirmDiscardInk(@NonNull PageView pageView) {
        final Context context = host.getContext();
        try {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.discard_ink_title)
                    .setMessage(R.string.discard_ink_message)
                    .setNegativeButton(R.string.menu_cancel, null)
                    .setPositiveButton(R.string.menu_discard, (d, w) -> {
                        pageView.deselectText();
                        pageView.cancelDraw();
                        modeStore.enterViewingMode();
                    })
                    .show();
        } catch (Throwable t) {
            pageView.deselectText();
            pageView.cancelDraw();
            modeStore.enterViewingMode();
        }
    }

    private void confirmDeleteAnnotation(@NonNull PageView pageView) {
        final Context context = host.getContext();
        try {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.delete_annotation_title)
                    .setMessage(R.string.delete_annotation_message)
                    .setNegativeButton(R.string.menu_cancel, null)
                    .setPositiveButton(R.string.menu_delete_annotation, (d, w) -> {
                        pageView.deleteSelectedAnnotation();
                        modeStore.enterViewingMode();
                    })
                    .show();
        } catch (Throwable t) {
            pageView.deleteSelectedAnnotation();
            modeStore.enterViewingMode();
        }
    }
}
