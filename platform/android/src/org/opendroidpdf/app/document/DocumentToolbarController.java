package org.opendroidpdf.app.document;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import org.opendroidpdf.R;

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
        void requestAddBlankPage();
        void requestFullscreen();
        void requestSettings();
        void requestPrint();
        void requestShare();
        void requestSearchMode();
        void requestDashboard();
        void requestDeleteNote();
        void requestSaveDialog();
        void requestGoToPageDialog();
        void requestLinkBackNavigation();
    }

    private final Host host;

    public DocumentToolbarController(@NonNull Host host) {
        this.host = host;
    }

    public void inflateMainMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.main_menu, menu);
        configureMenuState(menu);
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
            case R.id.menu_print:
                host.requestPrint();
                return true;
            case R.id.menu_share:
                host.requestShare();
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
            case R.id.menu_gotopage:
                host.requestGoToPageDialog();
                return true;
            case R.id.menu_linkback:
                host.requestLinkBackNavigation();
                return true;
            default:
                return false;
        }
    }

    private void configureMenuState(@NonNull Menu menu) {
        boolean hasDocument = host.hasDocumentLoaded();
        boolean hasView = host.hasDocumentView();

        configureVisibility(menu, R.id.menu_share, hasDocument);
        configureVisibility(menu, R.id.menu_print, hasDocument);
        configureVisibility(menu, R.id.menu_addpage, hasDocument);
        configureVisibility(menu, R.id.menu_save, hasDocument);
        configureVisibility(menu, R.id.menu_gotopage, hasDocument);
        configureVisibility(menu, R.id.menu_fullscreen, hasView);

        MenuItem deleteNote = menu.findItem(R.id.menu_delete_note);
        if (deleteNote != null) {
            boolean visible = host.isViewingNoteDocument();
            deleteNote.setVisible(visible);
            deleteNote.setEnabled(visible);
        }

        MenuItem linkBack = menu.findItem(R.id.menu_linkback);
        if (linkBack != null) {
            boolean available = host.isLinkBackAvailable();
            linkBack.setVisible(available);
            linkBack.setEnabled(available);
        }
    }

    private void configureVisibility(@NonNull Menu menu, int itemId, boolean visible) {
        MenuItem item = menu.findItem(itemId);
        if (item == null) {
            return;
        }
        item.setEnabled(visible);
        item.setVisible(visible);
    }
}
