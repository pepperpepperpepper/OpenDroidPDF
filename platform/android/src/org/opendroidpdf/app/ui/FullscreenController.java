package org.opendroidpdf.app.ui;

import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
        void refreshReaderChrome();
    }

    public void enterFullscreen(Host host) {
        MuPDFReaderView doc = host.getDocView();
        if (doc == null) return;
        AppCompatActivity activity = host.getActivity();
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setSystemBarsHidden(activity, true);
        if (activity.getSupportActionBar() != null) activity.getSupportActionBar().hide();
        host.setActionBarModeHidden();
        host.invalidateOptionsMenu();
        doc.setPadding(0, 0, 0, 0);
        doc.setClipToPadding(false);
        host.refreshReaderChrome();
    }

    public void exitFullscreen(Host host) {
        MuPDFReaderView doc = host.getDocView();
        if (doc == null) return;
        AppCompatActivity activity = host.getActivity();
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setSystemBarsHidden(activity, false);
        if (activity.getSupportActionBar() != null) activity.getSupportActionBar().show();
        host.setActionBarModeMainIfHidden();
        host.invalidateOptionsMenu();
        // Pad for the action bar if showing.
        int topPadding = 0;
        try {
            TypedValue tv = new TypedValue();
            if (activity.getSupportActionBar() != null && activity.getSupportActionBar().isShowing()
                    && activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
                topPadding = TypedValue.complexToDimensionPixelSize(tv.data,
                        activity.getResources().getDisplayMetrics());
            }
        } catch (Throwable ignore) {
            topPadding = 0;
        }
        doc.setPadding(0, Math.max(0, topPadding), 0, 0);
        doc.setClipToPadding(false);
        host.refreshReaderChrome();
    }

    private static void setSystemBarsHidden(AppCompatActivity activity, boolean hidden) {
        if (activity == null) return;
        Window w = activity.getWindow();
        if (w == null) return;
        View decor = w.getDecorView();
        if (decor == null) return;

        try {
            WindowInsetsControllerCompat c = ViewCompat.getWindowInsetsController(decor);
            if (c != null) {
                c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (hidden) {
                    c.hide(WindowInsetsCompat.Type.systemBars());
                } else {
                    c.show(WindowInsetsCompat.Type.systemBars());
                }
                return;
            }
        } catch (Throwable ignore) {
        }

        // Fallback for very old/odd devices.
        try {
            if (hidden) {
                int flags =
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                decor.setSystemUiVisibility(flags);
            } else {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        } catch (Throwable ignore) {
        }
    }
}
