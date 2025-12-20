package org.opendroidpdf.app.reflow;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.app.preferences.PreferencesNames;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** SharedPreferences-backed store for per-document EPUB/HTML layout prefs. */
public final class SharedPreferencesReflowPrefsStore implements ReflowPrefsStore {
    private static final String FONT_DP = "fontDp";
    private static final String MARGIN_SCALE = "marginScale";
    private static final String LINE_SPACING = "lineSpacing";
    private static final String THEME = "theme";

    private static final String ANNOTATED_FONT_DP = "annotatedFontDp";
    private static final String ANNOTATED_MARGIN_SCALE = "annotatedMarginScale";
    private static final String ANNOTATED_LINE_SPACING = "annotatedLineSpacing";
    private static final String ANNOTATED_THEME = "annotatedTheme";

    private final SharedPreferences prefs;

    public SharedPreferencesReflowPrefsStore(@NonNull Context context) {
        Context app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(PreferencesNames.REFLOW, Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public ReflowPrefsSnapshot load(@NonNull String docId) {
        String prefix = keyPrefix(docId);
        float fontDp = prefs.getFloat(prefix + FONT_DP, ReflowPrefsSnapshot.DEFAULT_FONT_DP);
        float marginScale = prefs.getFloat(prefix + MARGIN_SCALE, ReflowPrefsSnapshot.DEFAULT_MARGIN_SCALE);
        float lineSpacing = prefs.getFloat(prefix + LINE_SPACING, ReflowPrefsSnapshot.DEFAULT_LINE_SPACING);
        String themeName = prefs.getString(prefix + THEME, ReflowTheme.LIGHT.name());

        ReflowTheme theme;
        try {
            theme = ReflowTheme.valueOf(themeName != null ? themeName : ReflowTheme.LIGHT.name());
        } catch (Throwable ignore) {
            theme = ReflowTheme.LIGHT;
        }

        return new ReflowPrefsSnapshot(fontDp, marginScale, lineSpacing, theme);
    }

    @Override
    public void save(@NonNull String docId, @NonNull ReflowPrefsSnapshot snapshot) {
        String prefix = keyPrefix(docId);
        prefs.edit()
                .putFloat(prefix + FONT_DP, snapshot.fontDp)
                .putFloat(prefix + MARGIN_SCALE, snapshot.marginScale)
                .putFloat(prefix + LINE_SPACING, snapshot.lineSpacing)
                .putString(prefix + THEME, snapshot.theme.name())
                .apply();
    }

    @Nullable
    @Override
    public ReflowPrefsSnapshot loadAnnotatedLayoutOrNull(@NonNull String docId) {
        String prefix = keyPrefix(docId);
        if (!prefs.contains(prefix + ANNOTATED_FONT_DP)) {
            return null;
        }

        float fontDp = prefs.getFloat(prefix + ANNOTATED_FONT_DP, ReflowPrefsSnapshot.DEFAULT_FONT_DP);
        float marginScale = prefs.getFloat(prefix + ANNOTATED_MARGIN_SCALE, ReflowPrefsSnapshot.DEFAULT_MARGIN_SCALE);
        float lineSpacing = prefs.getFloat(prefix + ANNOTATED_LINE_SPACING, ReflowPrefsSnapshot.DEFAULT_LINE_SPACING);
        String themeName = prefs.getString(prefix + ANNOTATED_THEME, ReflowTheme.LIGHT.name());

        ReflowTheme theme;
        try {
            theme = ReflowTheme.valueOf(themeName != null ? themeName : ReflowTheme.LIGHT.name());
        } catch (Throwable ignore) {
            theme = ReflowTheme.LIGHT;
        }

        return new ReflowPrefsSnapshot(fontDp, marginScale, lineSpacing, theme);
    }

    @Override
    public void saveAnnotatedLayout(@NonNull String docId, @NonNull ReflowPrefsSnapshot snapshot) {
        String prefix = keyPrefix(docId);
        prefs.edit()
                .putFloat(prefix + ANNOTATED_FONT_DP, snapshot.fontDp)
                .putFloat(prefix + ANNOTATED_MARGIN_SCALE, snapshot.marginScale)
                .putFloat(prefix + ANNOTATED_LINE_SPACING, snapshot.lineSpacing)
                .putString(prefix + ANNOTATED_THEME, snapshot.theme.name())
                .apply();
    }

    private static String keyPrefix(@NonNull String docId) {
        return "doc." + stableHash(docId) + ".";
    }

    private static String stableHash(@NonNull String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Throwable t) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
