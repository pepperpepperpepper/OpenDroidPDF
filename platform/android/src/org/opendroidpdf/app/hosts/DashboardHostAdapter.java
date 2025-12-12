package org.opendroidpdf.app.hosts;

import android.content.Intent;

import androidx.annotation.NonNull;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.RecentFile;
import org.opendroidpdf.app.DashboardFragment;
import org.opendroidpdf.app.document.DocumentNavigationController;

/**
 * Moves the DashboardFragment.DashboardHost behavior out of the activity. The
 * activity stays the host (fragment requires it) and simply delegates calls here.
 */
public final class DashboardHostAdapter implements DashboardFragment.DashboardHost {
    private final OpenDroidPDFActivity activity;
    private final DocumentNavigationController nav;

    public DashboardHostAdapter(@NonNull OpenDroidPDFActivity activity,
                                @NonNull DocumentNavigationController nav) {
        this.activity = activity;
        this.nav = nav;
    }

    @Override
    public void onOpenDocumentRequested() {
        nav.openDocument();
    }

    @Override
    public void onCreateNewDocumentRequested() {
        nav.showOpenNewDocumentDialog();
    }

    @Override
    public void onOpenSettingsRequested() {
        Intent intent = new Intent(activity, org.opendroidpdf.SettingsActivity.class);
        activity.startActivity(intent);
        activity.overridePendingTransition(org.opendroidpdf.R.animator.enter_from_left,
                org.opendroidpdf.R.animator.fade_out);
    }

    @Override
    public void onRecentFileRequested(final RecentFile recentFile) {
        activity.checkSaveThenCall(new java.util.concurrent.Callable<Void>() {
            @Override
            public Void call() {
                Intent intent = new Intent(Intent.ACTION_VIEW, recentFile.getUri(), activity.getApplicationContext(), OpenDroidPDFActivity.class);
                intent.putExtra(Intent.EXTRA_TITLE, recentFile.getDisplayName());
                activity.startActivity(intent);
                activity.overridePendingTransition(org.opendroidpdf.R.animator.fade_in, org.opendroidpdf.R.animator.fade_out);
                activity.hideDashboard();
                activity.finish();
                return null;
            }
        });
    }

    @Override
    public boolean isMemoryLow() {
        return org.opendroidpdf.app.ui.UiUtils.isMemoryLow(activity);
    }

    @Override
    public int maxRecentFiles() {
        return activity.maxRecentFiles();
    }
}

