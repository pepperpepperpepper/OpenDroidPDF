package org.opendroidpdf.app.reflow;

import androidx.annotation.Nullable;

/** Generates paint-only user CSS for reflowable docs (EPUB/HTML). */
public final class ReflowUserCss {
    private ReflowUserCss() {}

    @Nullable
    public static String forTheme(@Nullable ReflowTheme theme) {
        if (theme == null) return null;
        switch (theme) {
            case DARK:
                return "html,body{background:#000 !important;color:#fff !important;}"
                        + "a,a:link,a:visited{color:#8ab4f8 !important;}"
                        + "hr{border-color:#333 !important;}"
                        + "::selection{background:#444 !important;color:#fff !important;}";
            case SEPIA:
                return "html,body{background:#f4ecd8 !important;color:#5b4636 !important;}"
                        + "a,a:link,a:visited{color:#2b5b73 !important;}"
                        + "hr{border-color:#d7c7a7 !important;}"
                        + "::selection{background:#d7c7a7 !important;color:#5b4636 !important;}";
            case LIGHT:
            default:
                return null;
        }
    }
}

