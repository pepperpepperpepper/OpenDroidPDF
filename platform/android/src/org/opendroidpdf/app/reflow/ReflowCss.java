package org.opendroidpdf.app.reflow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Builds user CSS for reflow documents, combining layout-affecting and paint-only rules. */
public final class ReflowCss {
    private ReflowCss() {}

    @Nullable
    public static String compose(@NonNull ReflowPrefsSnapshot prefs, float emPoints) {
        String layoutCss = layoutCss(prefs, emPoints);
        String themeCss = ReflowUserCss.forTheme(prefs.theme);
        if ((layoutCss == null || layoutCss.isEmpty()) && (themeCss == null || themeCss.isEmpty())) {
            return null;
        }
        if (themeCss == null || themeCss.isEmpty()) return layoutCss;
        if (layoutCss == null || layoutCss.isEmpty()) return themeCss;
        return layoutCss + themeCss;
    }

    @NonNull
    private static String layoutCss(@NonNull ReflowPrefsSnapshot prefs, float emPoints) {
        float marginPt = Math.max(0f, emPoints * Math.max(0f, prefs.marginScale));
        float lineHeight = Math.max(0.5f, prefs.lineSpacing);
        // Keep this strictly layout-related; theme colors live in ReflowUserCss.
        return "html,body{margin:" + marginPt + "pt !important;line-height:" + lineHeight + " !important;}";
    }
}

