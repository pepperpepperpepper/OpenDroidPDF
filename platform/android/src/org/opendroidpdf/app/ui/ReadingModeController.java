package org.opendroidpdf.app.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.SettingsActivity;

/**
 * "Reading mode" hides the top toolbar while reading, without entering true fullscreen.
 * <p>
 * This intentionally avoids touching zoom/links/etc (unlike fullscreen).
 */
public final class ReadingModeController {
    private ReadingModeController() {}

    public static boolean isEnabled(@NonNull Context context) {
        try {
            return prefs(context).getBoolean(SettingsActivity.PREF_READING_MODE, false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static void setEnabled(@NonNull Context context, boolean enabled) {
        try {
            prefs(context).edit().putBoolean(SettingsActivity.PREF_READING_MODE, enabled).apply();
        } catch (Throwable ignore) {
        }
    }

    public static void showToolbarForDashboard(@NonNull AppCompatActivity activity) {
        if (activity == null) return;
        // If we're in real fullscreen, let the fullscreen controller own toolbar + flags.
        if (isFullscreen(activity)) return;
        showActionBar(activity);
    }

    public static void applyToDocumentView(@NonNull AppCompatActivity activity,
                                          @Nullable MuPDFReaderView docView) {
        applyToDocumentView(activity, docView, isEnabled(activity));
    }

    public static void applyToDocumentView(@NonNull AppCompatActivity activity,
                                          @Nullable MuPDFReaderView docView,
                                          boolean enabled) {
        if (activity == null) return;

        if (enabled) {
            hideActionBar(activity);
            setDocTopPadding(docView, 0);
            return;
        }

        // If we're in real fullscreen, keep the toolbar hidden regardless of reading mode.
        if (!isFullscreen(activity)) {
            showActionBar(activity);
        }
        int topPadding = 0;
        try {
            if (activity.getSupportActionBar() != null && activity.getSupportActionBar().isShowing()) {
                topPadding = actionBarHeightPx(activity);
            }
        } catch (Throwable ignore) {
            topPadding = 0;
        }
        setDocTopPadding(docView, topPadding);
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        Context app = context.getApplicationContext();
        return app.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
    }

    private static boolean isFullscreen(@NonNull AppCompatActivity activity) {
        try {
            return (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static void hideActionBar(@NonNull AppCompatActivity activity) {
        try {
            if (activity.getSupportActionBar() != null) activity.getSupportActionBar().hide();
        } catch (Throwable ignore) {
        }
    }

    private static void showActionBar(@NonNull AppCompatActivity activity) {
        try {
            if (activity.getSupportActionBar() != null) activity.getSupportActionBar().show();
        } catch (Throwable ignore) {
        }
    }

    private static int actionBarHeightPx(@NonNull AppCompatActivity activity) {
        try {
            TypedValue tv = new TypedValue();
            if (activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
                return TypedValue.complexToDimensionPixelSize(tv.data, activity.getResources().getDisplayMetrics());
            }
        } catch (Throwable ignore) {
        }
        return 0;
    }

    private static void setDocTopPadding(@Nullable MuPDFReaderView docView, int topPadding) {
        if (docView == null) return;
        int clamped = Math.max(0, topPadding);
        try { docView.setPadding(0, clamped, 0, 0); } catch (Throwable ignore) {}
        try { docView.setClipToPadding(false); } catch (Throwable ignore) {}
    }
}

