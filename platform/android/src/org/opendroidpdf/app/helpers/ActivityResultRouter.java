package org.opendroidpdf.app.helpers;

import android.content.Intent;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.R;

/**
 * Routes onActivityResult events away from the Activity.
 */
public final class ActivityResultRouter {

    public interface Host {
        int EDIT_REQUEST();
        int OUTLINE_REQUEST();
        int PRINT_REQUEST();
        int SAVEAS_REQUEST();
        int MANAGE_STORAGE_REQUEST();

        void overridePendingTransition(int enter, int exit);
        void hideDashboard();
        void setIntent(Intent intent);
        Intent getIntent();
        void openDocumentFromIntent(Intent intent);
        boolean canResumeAfterManageStorage();
        void showToast(int resId);
        void setDisplayedViewIndex(int pageIndex);
        void documentNavigation_onActivityResultSaveAs(int resultCode, Intent intent);
    }

    private final Host host;

    public ActivityResultRouter(Host host) { this.host = host; }

    public boolean handle(int requestCode, int resultCode, Intent intent) {
        if (requestCode == host.MANAGE_STORAGE_REQUEST()) {
            if (host.canResumeAfterManageStorage()) {
                Intent current = host.getIntent();
                if (current != null && Intent.ACTION_VIEW.equals(current.getAction())) {
                    host.openDocumentFromIntent(current);
                }
            } else {
                host.showToast(R.string.cannot_open_document);
            }
            return true;
        }

        switch (requestCode) {
            case 0: // guard fallthrough
                return false;
            case /*EDIT_REQUEST*/ -1:
                break;
        }
        if (requestCode == host.EDIT_REQUEST()) {
            host.overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
            if (resultCode == AppCompatActivity.RESULT_OK) {
                if (intent != null) {
                    host.getIntent().setAction(Intent.ACTION_VIEW);
                    host.getIntent().setData(intent.getData());
                    host.getIntent().setFlags((host.getIntent().getFlags() & ~Intent.FLAG_GRANT_WRITE_URI_PERMISSION & ~Intent.FLAG_GRANT_READ_URI_PERMISSION) | (intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) | (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION));
                    host.hideDashboard();
                }
            }
            return true;
        }
        if (requestCode == host.OUTLINE_REQUEST()) {
            if (resultCode >= 0) {
                host.setDisplayedViewIndex(resultCode);
            }
            return true;
        }
        if (requestCode == host.PRINT_REQUEST()) {
            return true;
        }
        if (requestCode == host.SAVEAS_REQUEST()) {
            host.overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
            host.documentNavigation_onActivityResultSaveAs(resultCode, intent);
            return true;
        }
        return false;
    }
}

