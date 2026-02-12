package org.opendroidpdf.app.document;

import android.content.Context;
import android.graphics.Rect;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.opendroidpdf.BuildConfig;
import org.opendroidpdf.R;
import org.opendroidpdf.MuPDFPageAdapter;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.app.ui.UiUtils;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        boolean isReadingModeEnabled();
        @NonNull androidx.appcompat.app.AppCompatActivity getActivity();
        @NonNull AlertDialog.Builder alertBuilder();
        @NonNull MuPDFReaderView getDocView();
        void requestAddBlankPage();
        void requestOrganizePages();
        void requestFullscreen();
        void requestSetReadingModeEnabled(boolean enabled);
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
        void requestAssistant();
        void requestReadAloud();
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

        bindPageSwitcherControls(root, docView);

        // Navigate
        View toc = root.findViewById(R.id.navigate_view_action_toc);
        if (toc != null) {
            toc.setOnClickListener(v -> {
                dialog.dismiss();
                if (activity instanceof OpenDroidPDFActivity) {
                    OpenDroidPDFCore core = ((OpenDroidPDFActivity) activity).getCore();
                    DocumentIdentity ident = ((OpenDroidPDFActivity) activity).currentDocumentIdentityOrNull();
                    if (core != null) {
                        BookmarksTocUi.show(activity, docView, core, ident, BookmarksTocUi.TAB_TOC);
                        return;
                    }
                }
                host.requestTableOfContents(); // fallback
            });
        }

        final org.opendroidpdf.core.MuPdfRepository repo =
                (activity instanceof OpenDroidPDFActivity) ? ((OpenDroidPDFActivity) activity).getRepository() : null;
        View thumbnails = root.findViewById(R.id.navigate_view_action_thumbnails);
        if (thumbnails != null) {
            boolean visible = repo != null;
            try { visible = visible && repo.getPageCount() > 1; } catch (Throwable ignore) { visible = false; }
            thumbnails.setVisibility(visible ? View.VISIBLE : View.GONE);
            thumbnails.setOnClickListener(v -> {
                dialog.dismiss();
                if (repo == null) return;
                ThumbnailsUi.show(activity, docView, repo, pageIndex -> {
                    try { docView.setDisplayedViewIndex(pageIndex, true); } catch (Throwable ignore) {}
                });
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

        View assistant = root.findViewById(R.id.navigate_view_action_assistant);
        if (assistant != null) {
            assistant.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestAssistant();
            });
        }

        View findInDocument = root.findViewById(R.id.navigate_view_action_find_in_document);
        if (findInDocument != null) {
            findInDocument.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestSearchMode();
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

        SwitchCompat readingModeSwitch = root.findViewById(R.id.navigate_view_switch_reading_mode);
        View readingModeRow = root.findViewById(R.id.navigate_view_row_reading_mode);
        if (readingModeRow != null && readingModeSwitch != null) {
            readingModeRow.setOnClickListener(v -> readingModeSwitch.toggle());
            try { readingModeSwitch.setChecked(host.isReadingModeEnabled()); } catch (Throwable ignore) { readingModeSwitch.setChecked(false); }
            readingModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> host.requestSetReadingModeEnabled(isChecked));
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

    public void showNavigationMenuSheet() {
        AppCompatActivity activity = host != null ? host.getActivity() : null;
        if (activity == null) return;
        MuPDFReaderView docView = host.hasDocumentView() ? host.getDocView() : null;
        if (docView == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_navigation_menu_sheet, null);
        dialog.setContentView(root);

        final org.opendroidpdf.core.MuPdfRepository repo =
                (activity instanceof OpenDroidPDFActivity) ? ((OpenDroidPDFActivity) activity).getRepository() : null;
        final boolean isPdf = currentDocumentType(activity) == DocumentType.PDF;

        View comments = root.findViewById(R.id.navigation_menu_action_comments);
        if (comments != null) {
            comments.setOnClickListener(v -> {
                dialog.dismiss();
                host.requestCommentsList();
            });
        }

        View bookmarks = root.findViewById(R.id.navigation_menu_action_bookmarks);
        if (bookmarks != null) {
            bookmarks.setOnClickListener(v -> {
                dialog.dismiss();
                if (activity instanceof OpenDroidPDFActivity) {
                    OpenDroidPDFCore core = ((OpenDroidPDFActivity) activity).getCore();
                    DocumentIdentity ident = ((OpenDroidPDFActivity) activity).currentDocumentIdentityOrNull();
                    if (core != null) {
                        BookmarksTocUi.show(activity, docView, core, ident, BookmarksTocUi.TAB_BOOKMARKS);
                        return;
                    }
                }
                try { UiUtils.showInfo(activity, activity.getString(R.string.not_supported)); } catch (Throwable ignore) {}
            });
        }

        View contents = root.findViewById(R.id.navigation_menu_action_contents);
        if (contents != null) {
            contents.setOnClickListener(v -> {
                dialog.dismiss();
                if (activity instanceof OpenDroidPDFActivity) {
                    OpenDroidPDFCore core = ((OpenDroidPDFActivity) activity).getCore();
                    DocumentIdentity ident = ((OpenDroidPDFActivity) activity).currentDocumentIdentityOrNull();
                    if (core != null) {
                        BookmarksTocUi.show(activity, docView, core, ident, BookmarksTocUi.TAB_TOC);
                        return;
                    }
                }
                host.requestTableOfContents(); // fallback
            });
        }

        View thumbnails = root.findViewById(R.id.navigation_menu_action_thumbnails);
        if (thumbnails != null) {
            boolean visible = repo != null;
            try { visible = visible && repo.getPageCount() > 1; } catch (Throwable ignore) { visible = false; }
            thumbnails.setVisibility(visible ? View.VISIBLE : View.GONE);
            thumbnails.setOnClickListener(v -> {
                dialog.dismiss();
                if (repo == null) return;
                ThumbnailsUi.show(activity, docView, repo, pageIndex -> {
                    try { docView.setDisplayedViewIndex(pageIndex, true); } catch (Throwable ignore) {}
                });
            });
        }

        View attachments = root.findViewById(R.id.navigation_menu_action_attachments);
        if (attachments != null) {
            attachments.setAlpha(isPdf ? 1f : 0.5f);
            attachments.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    UiUtils.showInfo(activity, activity.getString(R.string.navigation_menu_attachments_coming_soon));
                } catch (Throwable ignore) {}
            });
        }

        dialog.show();
    }

    private static void bindPageSwitcherControls(@NonNull View root, @NonNull MuPDFReaderView docView) {
        final View row = root.findViewById(R.id.navigate_view_row_page_switcher);
        if (row == null) return;

        int pageCount = 0;
        try {
            Adapter adapter = docView.getAdapter();
            pageCount = adapter != null ? adapter.getCount() : 0;
        } catch (Throwable ignore) {
            pageCount = 0;
        }
        if (pageCount <= 1) {
            row.setVisibility(View.GONE);
            return;
        }

        final int totalPages = pageCount;
        final ImageButton prev = row.findViewById(R.id.navigate_view_page_prev);
        final ImageButton next = row.findViewById(R.id.navigate_view_page_next);
        final TextView label = row.findViewById(R.id.navigate_view_page_label);
        final SeekBar seek = row.findViewById(R.id.navigate_view_page_seek);

        int initialPage = 0;
        try { initialPage = docView.getSelectedItemPosition(); } catch (Throwable ignore) { initialPage = 0; }
        initialPage = clampPage(initialPage, totalPages);

        if (seek != null) {
            seek.setMax(Math.max(0, totalPages - 1));
            seek.setProgress(initialPage);
        }
        updatePageSwitcherUi(prev, next, label, seek, initialPage, totalPages);

        if (prev != null) {
            prev.setOnClickListener(v -> {
                int target = 0;
                try { target = (seek != null ? seek.getProgress() : docView.getSelectedItemPosition()) - 1; } catch (Throwable ignore) { target = 0; }
                target = clampPage(target, totalPages);
                if (seek != null) seek.setProgress(target);
                updatePageSwitcherUi(prev, next, label, seek, target, totalPages);
                navigateToPage(docView, target, totalPages);
            });
        }
        if (next != null) {
            next.setOnClickListener(v -> {
                int target = 0;
                try { target = (seek != null ? seek.getProgress() : docView.getSelectedItemPosition()) + 1; } catch (Throwable ignore) { target = 0; }
                target = clampPage(target, totalPages);
                if (seek != null) seek.setProgress(target);
                updatePageSwitcherUi(prev, next, label, seek, target, totalPages);
                navigateToPage(docView, target, totalPages);
            });
        }

        if (seek != null) {
            final android.widget.ImageView preview = row.findViewById(R.id.navigate_view_page_preview);
            org.opendroidpdf.core.MuPdfController controller = null;
            try {
                android.content.Context ctx = row.getContext();
                if (ctx instanceof OpenDroidPDFActivity) {
                    controller = ((OpenDroidPDFActivity) ctx).getMuPdfController();
                } else if (docView.getContext() instanceof OpenDroidPDFActivity) {
                    controller = ((OpenDroidPDFActivity) docView.getContext()).getMuPdfController();
                }
            } catch (Throwable ignore) {
                controller = null;
            }

            org.opendroidpdf.app.navigation.PageScrubberBinder.bind(
                    seek,
                    docView,
                    totalPages,
                    initialPage,
                    preview,
                    controller,
                    (pageIndex, pages, fromUser) -> updatePageSwitcherUi(prev, next, label, seek, pageIndex, pages),
                    null);
        }
    }

    private static void updatePageSwitcherUi(ImageButton prev,
                                            ImageButton next,
                                            TextView label,
                                            SeekBar seek,
                                            int pageIndex,
                                            int totalPages) {
        if (totalPages <= 0) return;
        int clamped = clampPage(pageIndex, totalPages);
        if (label != null) {
            label.setText(String.format(Locale.getDefault(), "%d / %d", clamped + 1, totalPages));
        }
        if (prev != null) {
            boolean enabled = clamped > 0;
            prev.setEnabled(enabled);
            prev.setAlpha(enabled ? 1f : 0.35f);
        }
        if (next != null) {
            boolean enabled = clamped < totalPages - 1;
            next.setEnabled(enabled);
            next.setAlpha(enabled ? 1f : 0.35f);
        }
        if (seek != null && seek.getProgress() != clamped) {
            seek.setProgress(clamped);
        }
    }

    private static void navigateToPage(@NonNull MuPDFReaderView docView, int pageIndex, int totalPages) {
        int clamped = clampPage(pageIndex, totalPages);
        try { docView.setDisplayedViewIndex(clamped, true); } catch (Throwable ignore) {}
        try { docView.setNormalizedScroll(0.0f, 0.0f); } catch (Throwable ignore) {}
    }

    private static int clampPage(int pageIndex, int totalPages) {
        if (totalPages <= 0) return 0;
        if (pageIndex < 0) return 0;
        if (pageIndex > totalPages - 1) return totalPages - 1;
        return pageIndex;
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

    public void showMoreToolsHubSheet() {
        AppCompatActivity activity = host != null ? host.getActivity() : null;
        if (activity == null) return;
        if (!host.hasDocumentLoaded()) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_more_tools_hub, null);
        dialog.setContentView(root);

        RecyclerView recycler = root.findViewById(R.id.more_tools_hub_recycler);
        if (recycler == null) {
            dialog.dismiss();
            return;
        }

        DocumentType docType = currentDocumentType(activity);
        boolean isPdf = docType == DocumentType.PDF;
        boolean isEpub = docType == DocumentType.EPUB;
        boolean canExport = isPdf || isEpub;
        boolean canOrganize = isPdf;
        boolean canSetPassword = isPdf && BuildConfig.ENABLE_QPDF_OPS;
        boolean canSearch = host.hasDocumentView();
        boolean canViewSettings = host.hasDocumentView();

        final List<MoreToolsHubItem> items = new ArrayList<>();
        items.add(new MoreToolsHubItem(
                R.drawable.ic_action_group_white_24dp,
                R.string.organize_pages_sheet_title,
                canOrganize,
                R.string.not_supported,
                () -> {
                    try { dialog.dismiss(); } catch (Throwable ignore) {}
                    host.requestOrganizePages();
                }));
        items.add(new MoreToolsHubItem(
                R.drawable.ic_share_white_24dp,
                R.string.export_sheet_title,
                canExport,
                R.string.export_not_available,
                () -> {
                    try { dialog.dismiss(); } catch (Throwable ignore) {}
                    showExportSheet();
                }));
        items.add(new MoreToolsHubItem(
                R.drawable.ic_print_white_24dp,
                R.string.menu_print,
                canExport,
                R.string.export_not_available,
                () -> {
                    try { dialog.dismiss(); } catch (Throwable ignore) {}
                    host.requestPrint();
                }));
        items.add(new MoreToolsHubItem(
                R.drawable.ic_lock_white_24dp,
                R.string.more_tools_hub_action_set_password,
                canSetPassword,
                isPdf ? R.string.export_option_requires_qpdf : R.string.not_supported,
                () -> {
                    try { dialog.dismiss(); } catch (Throwable ignore) {}
                    host.requestSaveEncrypted();
                }));
        items.add(new MoreToolsHubItem(
                R.drawable.ic_settings,
                R.string.menu_view_settings,
                canViewSettings,
                R.string.not_supported,
                () -> {
                    try { dialog.dismiss(); } catch (Throwable ignore) {}
                    if (!host.hasDocumentView()) return;
                    try {
                        MuPDFReaderView dv = host.getDocView();
                        if (dv != null) ViewSettingsUi.show(activity, dv);
                    } catch (Throwable ignore) {
                    }
                }));
        items.add(new MoreToolsHubItem(
                R.drawable.ic_action_search,
                R.string.menu_search,
                canSearch,
                R.string.not_supported,
                () -> {
                    try { dialog.dismiss(); } catch (Throwable ignore) {}
                    host.requestSearchMode();
                }));

        final Context ctx = activity;
        int spacingPx = dpToPx(ctx, 8);
        int padPx = 0;
        try { padPx = ctx.getResources().getDimensionPixelSize(R.dimen.dialog_padding_horizontal); } catch (Throwable ignore) { padPx = dpToPx(ctx, 16); }
        int widthPx = 0;
        try { widthPx = ctx.getResources().getDisplayMetrics().widthPixels; } catch (Throwable ignore) { widthPx = 0; }
        int availablePx = Math.max(1, widthPx - padPx * 2);
        int desiredCellPx = dpToPx(ctx, 96);
        int spanCount = Math.max(2, Math.min(4, availablePx / Math.max(1, desiredCellPx)));

        GridLayoutManager lm = new GridLayoutManager(ctx, spanCount);
        recycler.setLayoutManager(lm);
        recycler.setHasFixedSize(false);
        recycler.addItemDecoration(new GridSpacingItemDecoration(spanCount, spacingPx));
        recycler.setAdapter(new MoreToolsHubAdapter(ctx, items));

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
            case R.id.menu_view_settings:
                if (!host.hasDocumentView()) return true;
                try {
                    AppCompatActivity activity = host.getActivity();
                    MuPDFReaderView dv = host.getDocView();
                    if (activity != null && dv != null) ViewSettingsUi.show(activity, dv);
                } catch (Throwable ignore) {
                }
                return true;
            case R.id.menu_settings:
                host.requestSettings();
                return true;
            case R.id.menu_reading_settings:
                host.requestReadingSettings();
                return true;
            case R.id.menu_assistant:
                host.requestAssistant();
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
                if (host != null) {
                    AppCompatActivity activity = host.getActivity();
                    MuPDFReaderView tocDocView = host.hasDocumentView() ? host.getDocView() : null;
                    if (activity instanceof OpenDroidPDFActivity && tocDocView != null) {
                        OpenDroidPDFCore core = ((OpenDroidPDFActivity) activity).getCore();
                        DocumentIdentity ident = ((OpenDroidPDFActivity) activity).currentDocumentIdentityOrNull();
                        if (core != null) {
                            BookmarksTocUi.show(activity, tocDocView, core, ident, BookmarksTocUi.TAB_TOC);
                            return true;
                        }
                    }
                }
                host.requestTableOfContents(); // fallback
                return true;
            case R.id.menu_read_aloud:
                host.requestReadAloud();
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

    private static int dpToPx(@NonNull Context ctx, int dp) {
        float density = 1f;
        try { density = ctx.getResources().getDisplayMetrics().density; } catch (Throwable ignore) { density = 1f; }
        return Math.max(1, Math.round(dp * density));
    }

    private static final class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spanCount;
        private final int spacingPx;

        GridSpacingItemDecoration(int spanCount, int spacingPx) {
            this.spanCount = Math.max(1, spanCount);
            this.spacingPx = Math.max(0, spacingPx);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position < 0) return;
            int column = position % spanCount;
            outRect.left = spacingPx - column * spacingPx / spanCount;
            outRect.right = (column + 1) * spacingPx / spanCount;
            if (position < spanCount) outRect.top = spacingPx;
            outRect.bottom = spacingPx;
        }
    }

    private static final class MoreToolsHubItem {
        final int iconResId;
        final int labelResId;
        final boolean enabled;
        final int disabledMessageResId;
        @NonNull final Runnable action;

        MoreToolsHubItem(int iconResId,
                         int labelResId,
                         boolean enabled,
                         int disabledMessageResId,
                         @NonNull Runnable action) {
            this.iconResId = iconResId;
            this.labelResId = labelResId;
            this.enabled = enabled;
            this.disabledMessageResId = disabledMessageResId;
            this.action = action;
        }
    }

    private static final class MoreToolsHubAdapter extends RecyclerView.Adapter<MoreToolsHubAdapter.Holder> {
        private final Context ctx;
        private final List<MoreToolsHubItem> items;

        MoreToolsHubAdapter(@NonNull Context ctx, @NonNull List<MoreToolsHubItem> items) {
            this.ctx = ctx;
            this.items = items;
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_more_tools_tile, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MoreToolsHubItem item = items.get(position);
            holder.itemView.setAlpha(item.enabled ? 1f : 0.5f);
            if (holder.icon != null) holder.icon.setImageResource(item.iconResId);
            if (holder.label != null) holder.label.setText(item.labelResId);
            try { holder.itemView.setContentDescription(ctx.getString(item.labelResId)); } catch (Throwable ignore) {}

            holder.itemView.setOnClickListener(v -> {
                if (!item.enabled) {
                    int msgRes = item.disabledMessageResId != 0 ? item.disabledMessageResId : R.string.not_supported;
                    try { UiUtils.showInfo(ctx, ctx.getString(msgRes)); } catch (Throwable ignore) {}
                    return;
                }
                try { item.action.run(); } catch (Throwable ignore) {}
            });
        }

        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;

            Holder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.more_tools_tile_icon);
                label = itemView.findViewById(R.id.more_tools_tile_label);
            }
        }
    }

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
