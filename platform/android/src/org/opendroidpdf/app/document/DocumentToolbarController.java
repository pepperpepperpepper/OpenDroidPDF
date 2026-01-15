package org.opendroidpdf.app.document;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.R;
import org.opendroidpdf.MuPDFReaderView;

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
        void requestFullscreen();
        void requestSettings();
        void requestReadingSettings();
        void requestTableOfContents();
        void requestPrint();
        void requestShare();
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
                host.requestShare();
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
}
