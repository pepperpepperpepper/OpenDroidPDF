package org.opendroidpdf.app.document;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.Adapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.R;
import org.opendroidpdf.MuPDFPageAdapter;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.ui.UiUtils;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Centralizes the wiring for the primary document toolbar so that the activity simply delegates
 * menu inflation/state and action handling per feature.
 */
public class DocumentToolbarController {

    public interface Host {
        boolean hasDocumentLoaded();
        boolean hasDocumentView();
        boolean isViewingNoteDocument();
        boolean isLinkBackAvailable();
        boolean areCommentsVisible();
        @NonNull androidx.appcompat.app.AppCompatActivity getActivity();
        @NonNull AlertDialog.Builder alertBuilder();
        @NonNull MuPDFReaderView getDocView();
        void requestAddBlankPage();
        void requestOrganizePages();
        void requestFullscreen();
        void requestSettings();
        void requestReadingSettings();
        void requestTableOfContents();
        void requestPrint();
        void requestShare();
        void requestSaveCopy();
        void requestShareLinearized();
        void requestShareEncrypted();
        void requestShareFlattened();
        void requestSaveLinearized();
        void requestSaveEncrypted();
        void requestImportAnnotations();
        void requestExportAnnotations();
        void requestSearchMode();
        void requestDashboard();
        void requestCommentsList();
        void requestSetCommentsVisible(boolean visible);
        void requestNavigateComment(int direction);
        void requestDeleteNote();
        void requestSaveDialog();
        void requestFillSign();
        void requestLinkBackNavigation();
    }

    private final Host host;

    public DocumentToolbarController(@NonNull Host host) {
        this.host = host;
    }

    public void showNavigateViewSheet() {
        AppCompatActivity activity = host != null ? host.getActivity() : null;
        if (activity == null) return;
        MuPDFReaderView docView = host.hasDocumentView() ? host.getDocView() : null;
        if (docView == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_navigate_view_sheet, null);
        dialog.setContentView(root);

        // Navigate
        View toc = root.findViewById(R.id.navigate_view_action_toc);
        if (toc != null) {
            toc.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestTableOfContents();
            });
        }
        View gotoPage = root.findViewById(R.id.navigate_view_action_goto_page);
        if (gotoPage != null) {
            gotoPage.setOnClickListener(v -> {
                dialog.dismiss();
                org.opendroidpdf.app.dialog.Dialogs.showGoToPage(
                        activity,
                        host.alertBuilder(),
                        docView);
            });
        }

        // View
        View fullscreen = root.findViewById(R.id.navigate_view_action_fullscreen);
        if (fullscreen != null) {
            fullscreen.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestFullscreen();
            });
        }

        SwitchCompat showAnnotationsSwitch = root.findViewById(R.id.navigate_view_switch_show_annotations);
        View showAnnotationsRow = root.findViewById(R.id.navigate_view_row_show_annotations);
        if (showAnnotationsRow != null && showAnnotationsSwitch != null) {
            showAnnotationsRow.setOnClickListener(v -> showAnnotationsSwitch.toggle());
            try { showAnnotationsSwitch.setChecked(host.areCommentsVisible()); } catch (Throwable ignore) { showAnnotationsSwitch.setChecked(true); }
            showAnnotationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> host.requestSetCommentsVisible(isChecked));
        }

        View annotations = root.findViewById(R.id.navigate_view_action_annotations);
        if (annotations != null) {
            annotations.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestCommentsList();
            });
        }

        // Sidecar-only note marker toggle (EPUB + read-only PDFs).
        View noteMarkersRow = root.findViewById(R.id.navigate_view_row_note_markers);
        SwitchCompat noteMarkersSwitch = root.findViewById(R.id.navigate_view_switch_note_markers);
        boolean sidecarAvailable = hasSidecarSession(docView);
        if (noteMarkersRow != null) noteMarkersRow.setVisibility(sidecarAvailable ? View.VISIBLE : View.GONE);
        if (noteMarkersRow != null && noteMarkersSwitch != null) {
            noteMarkersRow.setOnClickListener(v -> noteMarkersSwitch.toggle());
            noteMarkersSwitch.setChecked(docView.areSidecarNotesStickyModeEnabled());
            noteMarkersSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try { docView.setSidecarNotesStickyModeEnabled(isChecked); } catch (Throwable ignore) {}
                try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
            });
        }

        // PDF-only forms highlight toggle.
        View formsRow = root.findViewById(R.id.navigate_view_row_forms_highlight);
        SwitchCompat formsSwitch = root.findViewById(R.id.navigate_view_switch_forms_highlight);
        boolean isPdf = currentDocumentType(activity) == DocumentType.PDF;
        if (formsRow != null) formsRow.setVisibility(isPdf ? View.VISIBLE : View.GONE);
        if (formsRow != null && formsSwitch != null) {
            formsRow.setOnClickListener(v -> formsSwitch.toggle());
            formsSwitch.setChecked(docView.isFormFieldHighlightEnabled());
        }

        // Forms navigation (contextual when highlight is enabled).
        View formPrev = root.findViewById(R.id.navigate_view_action_form_previous);
        View formNext = root.findViewById(R.id.navigate_view_action_form_next);
        boolean formsNavVisible = isPdf && docView.isFormFieldHighlightEnabled();
        if (formPrev != null) {
            formPrev.setVisibility(formsNavVisible ? View.VISIBLE : View.GONE);
            formPrev.setOnClickListener(v -> {
                dialog.dismiss();
                try { docView.navigateFormField(-1); } catch (Throwable ignore) {}
            });
        }
        if (formNext != null) {
            formNext.setVisibility(formsNavVisible ? View.VISIBLE : View.GONE);
            formNext.setOnClickListener(v -> {
                dialog.dismiss();
                try { docView.navigateFormField(1); } catch (Throwable ignore) {}
            });
        }
        if (formsSwitch != null) {
            formsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try { docView.setFormFieldHighlightEnabled(isChecked); } catch (Throwable ignore) {}
                if (formPrev != null) formPrev.setVisibility((isPdf && isChecked) ? View.VISIBLE : View.GONE);
                if (formNext != null) formNext.setVisibility((isPdf && isChecked) ? View.VISIBLE : View.GONE);
                try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
            });
        }

        View readingSettings = root.findViewById(R.id.navigate_view_action_reading_settings);
        boolean isEpub = currentDocumentType(activity) == DocumentType.EPUB;
        if (readingSettings != null) {
            readingSettings.setVisibility(isEpub ? View.VISIBLE : View.GONE);
            readingSettings.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestReadingSettings();
            });
        }

        // Document
        View save = root.findViewById(R.id.navigate_view_action_save);
        if (save != null) {
            boolean canSaveToCurrentUri = false;
            try {
                if (activity instanceof OpenDroidPDFActivity) {
                    OpenDroidPDFActivity oda = (OpenDroidPDFActivity) activity;
                    canSaveToCurrentUri = oda.canSaveToCurrentUri();
                }
            } catch (Throwable ignore) {}
            boolean canSaveChanges = isPdf && canSaveToCurrentUri;
            save.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            save.setAlpha(canSaveChanges ? 1f : 0.5f);
            save.setOnClickListener(v -> {
                if (canSaveChanges) {
                    dialog.dismiss();
                    host.requestSaveDialog();
                    return;
                }
                try {
                    UiUtils.showInfo(activity, activity.getString(R.string.save_changes_unavailable_use_export));
                } catch (Throwable ignore) {}
                dialog.dismiss();
                showExportSheet();
            });
        }

        View addBlankPage = root.findViewById(R.id.navigate_view_action_add_blank_page);
        if (addBlankPage != null) {
            addBlankPage.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            addBlankPage.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestAddBlankPage();
            });
        }

        View organizePages = root.findViewById(R.id.navigate_view_action_organize_pages);
        if (organizePages != null) {
            organizePages.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            organizePages.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestOrganizePages();
            });
        }

        View deleteNote = root.findViewById(R.id.navigate_view_action_delete_note);
        if (deleteNote != null) {
            boolean visible = false;
            try { visible = host.isViewingNoteDocument(); } catch (Throwable ignore) { visible = false; }
            deleteNote.setVisibility(visible ? View.VISIBLE : View.GONE);
            deleteNote.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestDeleteNote();
            });
        }

        dialog.show();
    }

    public void showExportSheet() {
        AppCompatActivity activity = host != null ? host.getActivity() : null;
        if (activity == null) return;
        if (!host.hasDocumentLoaded()) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_export_sheet, null);
        dialog.setContentView(root);

        DocumentType docType = currentDocumentType(activity);
        boolean isPdf = docType == DocumentType.PDF;
        boolean isEpub = docType == DocumentType.EPUB;
        boolean canExport = isPdf || isEpub;

        boolean canSaveToCurrentUri = false;
        try {
            if (activity instanceof OpenDroidPDFActivity) {
                canSaveToCurrentUri = ((OpenDroidPDFActivity) activity).canSaveToCurrentUri();
            }
        } catch (Throwable ignore) {}
        boolean sidecarAvailable = isEpub || (isPdf && !canSaveToCurrentUri);

        View shareCopy = root.findViewById(R.id.export_action_share_copy);
        if (shareCopy != null) {
            shareCopy.setAlpha(canExport ? 1f : 0.5f);
            shareCopy.setOnClickListener(v -> {
                if (!canExport) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_not_available)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestShare();
            });
        }

        View saveCopy = root.findViewById(R.id.export_action_save_copy);
        if (saveCopy != null) {
            saveCopy.setAlpha(canExport ? 1f : 0.5f);
            saveCopy.setOnClickListener(v -> {
                if (!canExport) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_not_available)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestSaveCopy();
            });
        }

        View print = root.findViewById(R.id.export_action_print);
        if (print != null) {
            print.setAlpha(canExport ? 1f : 0.5f);
            print.setOnClickListener(v -> {
                if (!canExport) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_not_available)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestPrint();
            });
        }

        View advancedContainer = root.findViewById(R.id.export_sheet_advanced_container);
        TextView advancedToggle = root.findViewById(R.id.export_action_advanced_toggle);
        boolean showAdvanced = canExport && (isPdf || isEpub);
        if (advancedToggle != null) advancedToggle.setVisibility(showAdvanced ? View.VISIBLE : View.GONE);
        if (!showAdvanced && advancedContainer != null) advancedContainer.setVisibility(View.GONE);
        if (advancedContainer != null && advancedToggle != null) {
            advancedToggle.setOnClickListener(v -> {
                boolean showing = advancedContainer.getVisibility() == View.VISIBLE;
                advancedContainer.setVisibility(showing ? View.GONE : View.VISIBLE);
                advancedToggle.setText(showing ? R.string.export_sheet_action_advanced_options
                        : R.string.export_sheet_action_hide_advanced_options);
            });
        }

        View shareLinear = root.findViewById(R.id.export_action_share_linearized);
        if (shareLinear != null) {
            boolean enabled = isPdf && BuildConfig.ENABLE_QPDF_OPS;
            shareLinear.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            shareLinear.setAlpha(enabled ? 1f : 0.5f);
            shareLinear.setOnClickListener(v -> {
                if (!enabled) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_option_requires_qpdf)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestShareLinearized();
            });
        }

        View shareEncrypted = root.findViewById(R.id.export_action_share_encrypted);
        if (shareEncrypted != null) {
            boolean enabled = isPdf && BuildConfig.ENABLE_QPDF_OPS;
            shareEncrypted.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            shareEncrypted.setAlpha(enabled ? 1f : 0.5f);
            shareEncrypted.setOnClickListener(v -> {
                if (!enabled) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_option_requires_qpdf)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestShareEncrypted();
            });
        }

        View shareFlattened = root.findViewById(R.id.export_action_share_flattened);
        if (shareFlattened != null) {
            shareFlattened.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            shareFlattened.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestShareFlattened();
            });
        }

        View saveLinear = root.findViewById(R.id.export_action_save_linearized);
        if (saveLinear != null) {
            boolean enabled = isPdf && BuildConfig.ENABLE_QPDF_OPS;
            saveLinear.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            saveLinear.setAlpha(enabled ? 1f : 0.5f);
            saveLinear.setOnClickListener(v -> {
                if (!enabled) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_option_requires_qpdf)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestSaveLinearized();
            });
        }

        View saveEncrypted = root.findViewById(R.id.export_action_save_encrypted);
        if (saveEncrypted != null) {
            boolean enabled = isPdf && BuildConfig.ENABLE_QPDF_OPS;
            saveEncrypted.setVisibility(isPdf ? View.VISIBLE : View.GONE);
            saveEncrypted.setAlpha(enabled ? 1f : 0.5f);
            saveEncrypted.setOnClickListener(v -> {
                if (!enabled) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_option_requires_qpdf)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestSaveEncrypted();
            });
        }

        View exportAnnotations = root.findViewById(R.id.export_action_export_annotations);
        if (exportAnnotations != null) {
            exportAnnotations.setVisibility(canExport ? View.VISIBLE : View.GONE);
            exportAnnotations.setAlpha(sidecarAvailable ? 1f : 0.5f);
            exportAnnotations.setOnClickListener(v -> {
                if (!sidecarAvailable) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_sidecar_only)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestExportAnnotations();
            });
        }

        View importAnnotations = root.findViewById(R.id.export_action_import_annotations);
        if (importAnnotations != null) {
            importAnnotations.setVisibility(canExport ? View.VISIBLE : View.GONE);
            importAnnotations.setAlpha(sidecarAvailable ? 1f : 0.5f);
            importAnnotations.setOnClickListener(v -> {
                if (!sidecarAvailable) {
                    try { UiUtils.showInfo(activity, activity.getString(R.string.export_sidecar_only)); } catch (Throwable ignore) {}
                    return;
                }
                dialog.dismiss();
                host.requestImportAnnotations();
            });
        }

        dialog.show();
    }

    public void inflateMainMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate only; state is now managed centrally by ToolbarStateController.
        inflater.inflate(R.menu.main_menu, menu);
    }

    public boolean handleMenuItem(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_addpage:
                host.requestAddBlankPage();
                return true;
            case R.id.menu_fullscreen:
                host.requestFullscreen();
                return true;
            case R.id.menu_settings:
                host.requestSettings();
                return true;
            case R.id.menu_reading_settings:
                host.requestReadingSettings();
                return true;
            case R.id.menu_print:
                host.requestPrint();
                return true;
            case R.id.menu_share:
                showExportSheet();
                return true;
            case R.id.menu_share_linearized:
                host.requestShareLinearized();
                return true;
            case R.id.menu_share_encrypted:
                host.requestShareEncrypted();
                return true;
            case R.id.menu_share_flattened:
                host.requestShareFlattened();
                return true;
            case R.id.menu_save_linearized:
                host.requestSaveLinearized();
                return true;
            case R.id.menu_save_encrypted:
                host.requestSaveEncrypted();
                return true;
            case R.id.menu_import_annotations:
                host.requestImportAnnotations();
                return true;
            case R.id.menu_export_annotations:
                host.requestExportAnnotations();
                return true;
            case R.id.menu_search:
                host.requestSearchMode();
                return true;
            case R.id.menu_open:
                host.requestDashboard();
                return true;
            case R.id.menu_delete_note:
                host.requestDeleteNote();
                return true;
            case R.id.menu_save:
                host.requestSaveDialog();
                return true;
            case R.id.menu_fill_sign:
                host.requestFillSign();
                return true;
            case R.id.menu_forms:
                if (!host.hasDocumentView()) return true;
                org.opendroidpdf.MuPDFReaderView docView = host.getDocView();
                if (docView != null) {
                    boolean enabled = !docView.isFormFieldHighlightEnabled();
                    docView.setFormFieldHighlightEnabled(enabled);
                    item.setChecked(enabled);
                    try { host.getActivity().invalidateOptionsMenu(); } catch (Throwable ignore) {}
                }
                return true;
            case R.id.menu_form_previous:
                if (!host.hasDocumentView()) return true;
                org.opendroidpdf.MuPDFReaderView docPrev = host.getDocView();
                if (docPrev != null) docPrev.navigateFormField(-1);
                return true;
            case R.id.menu_form_next:
                if (!host.hasDocumentView()) return true;
                org.opendroidpdf.MuPDFReaderView docNext = host.getDocView();
                if (docNext != null) docNext.navigateFormField(1);
                return true;
            case R.id.menu_gotopage:
                org.opendroidpdf.app.dialog.Dialogs.showGoToPage(
                        host.getActivity(),
                        host.alertBuilder(),
                        host.getDocView());
                return true;
            case R.id.menu_toc:
                host.requestTableOfContents();
                return true;
            case R.id.menu_comments:
                host.requestCommentsList();
                return true;
            case R.id.menu_show_comments: {
                boolean next = true;
                try { next = !host.areCommentsVisible(); } catch (Throwable ignore) { next = true; }
                host.requestSetCommentsVisible(next);
                item.setChecked(next);
                try { host.getActivity().invalidateOptionsMenu(); } catch (Throwable ignore) {}
                return true;
            }
            case R.id.menu_sticky_notes: {
                if (!host.hasDocumentView()) return true;
                org.opendroidpdf.MuPDFReaderView stickyDocView = host.getDocView();
                if (stickyDocView != null) {
                    boolean enabled = !stickyDocView.areSidecarNotesStickyModeEnabled();
                    stickyDocView.setSidecarNotesStickyModeEnabled(enabled);
                    item.setChecked(enabled);
                    try { host.getActivity().invalidateOptionsMenu(); } catch (Throwable ignore) {}
                }
                return true;
            }
            case R.id.menu_comment_previous:
                host.requestNavigateComment(-1);
                return true;
            case R.id.menu_comment_next:
                host.requestNavigateComment(1);
                return true;
            case R.id.menu_linkback:
                host.requestLinkBackNavigation();
                return true;
            default:
                return false;
        }
    }

    // Visibility/enablement is handled by ToolbarStateController.onPrepareOptionsMenu

    private static DocumentType currentDocumentType(@NonNull AppCompatActivity activity) {
        try {
            if (activity instanceof OpenDroidPDFActivity) {
                OpenDroidPDFCore core = ((OpenDroidPDFActivity) activity).getCore();
                return core != null ? DocumentType.fromFileFormat(core.fileFormat()) : DocumentType.OTHER;
            }
        } catch (Throwable ignore) {
        }
        return DocumentType.OTHER;
    }

    private static boolean hasSidecarSession(@NonNull MuPDFReaderView docView) {
        try {
            Adapter adapter = docView.getAdapter();
            if (adapter instanceof MuPDFPageAdapter) {
                return ((MuPDFPageAdapter) adapter).sidecarSessionOrNull() != null;
            }
        } catch (Throwable ignore) {
        }
        return false;
    }
}
