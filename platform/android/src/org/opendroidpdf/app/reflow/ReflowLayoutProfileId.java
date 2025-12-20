package org.opendroidpdf.app.reflow;

import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Stable identifier for the layout-affecting parameters of a reflowable document.
 *
 * <p>Theme is intentionally excluded: theme is paint-only and must not affect layout
 * identity or visibility of annotations/recents.</p>
 */
public final class ReflowLayoutProfileId {
    private ReflowLayoutProfileId() {}

    @NonNull
    public static String from(@NonNull ReflowPrefsSnapshot prefs,
                              @Nullable PointF pageSize,
                              float em) {
        float w = pageSize != null ? pageSize.x : -1f;
        float h = pageSize != null ? pageSize.y : -1f;
        // Round floats to keep IDs stable across minor float noise.
        int w10 = Math.round(w * 10f);
        int h10 = Math.round(h * 10f);
        int em100 = Math.round(em * 100f);
        int font10 = Math.round(prefs.fontDp * 10f);
        int margin100 = Math.round(prefs.marginScale * 100f);
        int line100 = Math.round(prefs.lineSpacing * 100f);

        return String.format(
                Locale.US,
                "w%d_h%d_em%d_f%d_m%d_l%d",
                w10,
                h10,
                em100,
                font10,
                margin100,
                line100
        );
    }
}

