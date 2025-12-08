package org.opendroidpdf.app.toolbar;

import android.view.Menu;
import android.view.MenuItem;

/**
 * Centralizes toolbar visibility/enablement state so the activity only forwards events.
 * Currently wraps existing menu invalidation logic; can be expanded with finer-grained control.
 */
public class ToolbarStateController {

    public interface Host {
        boolean hasOpenDocument();
        boolean canUndo();
        boolean hasUnsavedChanges();
        boolean hasLinkTarget();
        void invalidateOptionsMenu();
    }

    private final Host host;

    public ToolbarStateController(Host host) {
        this.host = host;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        if (menu == null) return false;

        boolean hasDoc = host.hasOpenDocument();

        menu.setGroupEnabled(org.opendroidpdf.R.id.menu_group_document_actions, hasDoc);
        menu.setGroupEnabled(org.opendroidpdf.R.id.menu_group_editor_tools, hasDoc);
        menu.setGroupVisible(org.opendroidpdf.R.id.menu_group_editor_tools, hasDoc);

        MenuItem undo = menu.findItem(org.opendroidpdf.R.id.menu_undo);
        if (undo != null) {
            undo.setEnabled(hasDoc && host.canUndo());
            undo.setVisible(hasDoc);
        }
        MenuItem save = menu.findItem(org.opendroidpdf.R.id.menu_save);
        if (save != null) {
            save.setEnabled(hasDoc && host.hasUnsavedChanges());
        }
        MenuItem linkBack = menu.findItem(org.opendroidpdf.R.id.menu_linkback);
        if (linkBack != null) {
            boolean show = hasDoc && host.hasLinkTarget();
            linkBack.setVisible(show);
            linkBack.setEnabled(show);
        }
        MenuItem search = menu.findItem(org.opendroidpdf.R.id.menu_search);
        if (search != null) {
            search.setVisible(hasDoc);
            search.setEnabled(hasDoc);
        }
        MenuItem draw = menu.findItem(org.opendroidpdf.R.id.menu_draw);
        if (draw != null) {
            draw.setVisible(hasDoc);
            draw.setEnabled(hasDoc);
        }
        MenuItem addText = menu.findItem(org.opendroidpdf.R.id.menu_add_text_annot);
        if (addText != null) {
            addText.setVisible(hasDoc);
            addText.setEnabled(hasDoc);
        }
        MenuItem print = menu.findItem(org.opendroidpdf.R.id.menu_print);
        if (print != null) {
            print.setEnabled(hasDoc);
        }
        MenuItem share = menu.findItem(org.opendroidpdf.R.id.menu_share);
        if (share != null) {
            share.setEnabled(hasDoc);
        }
        return true;
    }

    public void notifyStateChanged() {
        host.invalidateOptionsMenu();
    }
}
