package org.opendroidpdf.app.ui;

import android.util.TypedValue;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFCore;

/**
 * Encapsulates fullscreen enter/exit behavior so the Activity can delegate.
 */
public final class FullscreenController {

    public interface Host {
        AppCompatActivity getActivity();
        MuPDFReaderView getDocView();
        OpenDroidPDFCore getCore();
        void saveViewport(android.net.Uri uri);
        void setupDocView();
        void setActionBarModeHidden();
        void setActionBarModeMainIfHidden();
        void invalidateOptionsMenu();
    }

    public void enterFullscreen(Host host) {
        MuPDFReaderView doc = host.getDocView();
        if (doc == null) return;
        AppCompatActivity activity = host.getActivity();
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (activity.getSupportActionBar() != null) activity.getSupportActionBar().hide();
        host.setActionBarModeHidden();
        host.invalidateOptionsMenu();
        doc.setScale(1.0f);
        doc.setLinksEnabled(false);
        doc.setPadding(0, 0, 0, 0);
    }

    public void exitFullscreen(Host host) {
        MuPDFReaderView doc = host.getDocView();
        if (doc == null) return;
        AppCompatActivity activity = host.getActivity();
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (activity.getSupportActionBar() != null) activity.getSupportActionBar().show();
        host.setActionBarModeMainIfHidden();
        host.invalidateOptionsMenu();
        doc.setScale(1.0f);
        doc.setLinksEnabled(true);
        // Pad for the action bar if showing
        TypedValue tv = new TypedValue();
        if (activity.getSupportActionBar() != null && activity.getSupportActionBar().isShowing()
                && activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
            int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,
                    activity.getResources().getDisplayMetrics());
            doc.setPadding(0, actionBarHeight, 0, 0);
            doc.setClipToPadding(false);
        }
    }
}

