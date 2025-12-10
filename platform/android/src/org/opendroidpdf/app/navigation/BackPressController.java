package org.opendroidpdf.app.navigation;

import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.R;

import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Centralizes Activity back-press behavior so OpenDroidPDFActivity shrinks.
 */
public final class BackPressController {

    public interface Host {
        boolean isActionBarHidden();
        void exitFullScreen();
        boolean dashboardIsShown();
        void hideDashboard();
        Mode getMode();
        void setMode(Mode mode);
        void hideKeyboard();
        void clearSearchQuery();
        void clearSearchResults();
        void resetupChildren();
        void setViewingMode();
        void deselectTextOnCurrentPage();
        boolean hasUnsavedChanges();
        boolean canSaveToCurrentUri();
        void saveInBackground(Callable<?> success, Callable<?> failure);
        void showSaveAsActivity();
        void finish();
        AlertDialog.Builder alertBuilder();
        String t(int resId);
    }

    public enum Mode { Main, Annot, Edit, Search, Selection, Hidden, AddingTextAnnot, Empty }

    private final Host host;

    public BackPressController(Host host) {
        this.host = host;
    }

    public boolean onBackPressed() {
        if (host.isActionBarHidden()) { host.exitFullScreen(); return true; }
        if (host.dashboardIsShown()) { host.hideDashboard(); return true; }

        switch (host.getMode()) {
            case Annot:
                return true;
            case Search:
                host.hideKeyboard();
                host.clearSearchQuery();
                host.setViewingMode();
                host.clearSearchResults();
                host.resetupChildren();
                host.setMode(Mode.Main);
                return true;
            case Selection:
                host.setViewingMode();
                host.deselectTextOnCurrentPage();
                return true;
        }

        if (!host.hasUnsavedChanges()) {
            return false; // allow system default
        }

        AlertDialog.Builder b = host.alertBuilder();
        AlertDialog alert = b.create();
        alert.setTitle(host.t(R.string.save_question));
        alert.setMessage(host.t(R.string.document_has_changes_save_them));
        java.util.concurrent.Callable<Void> ok = new java.util.concurrent.Callable<Void>() {
            @Override public Void call() { host.finish(); return null; }
        };
        java.util.concurrent.Callable<Void> err = new java.util.concurrent.Callable<Void>() {
            @Override public Void call() { return null; }
        };

        if (host.canSaveToCurrentUri()) {
            alert.setButton(AlertDialog.BUTTON_POSITIVE, host.t(R.string.save), (d,w) -> host.saveInBackground(ok, err));
        } else {
            alert.setButton(AlertDialog.BUTTON_POSITIVE, host.t(R.string.saveas), (d,w) -> host.showSaveAsActivity());
        }
        alert.setButton(AlertDialog.BUTTON_NEUTRAL, host.t(R.string.cancel), (d,w) -> {});
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, host.t(R.string.no), (d,w) -> host.finish());
        alert.show();
        return true;
    }
}

