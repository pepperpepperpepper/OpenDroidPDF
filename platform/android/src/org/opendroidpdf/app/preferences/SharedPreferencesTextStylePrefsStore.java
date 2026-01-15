package org.opendroidpdf.app.preferences;

import android.content.SharedPreferences;

/** Android-backed FreeText style prefs store; wraps SharedPreferences but keeps it out of the service API. */
public class SharedPreferencesTextStylePrefsStore implements TextStylePrefsStore {
    private static final String PREF_TEXT_FONT_FAMILY = "pref_text_font_family";
    private static final String PREF_TEXT_FONT_STYLE_FLAGS = "pref_text_font_style_flags";
    private static final String PREF_TEXT_FONT_SIZE = "pref_text_font_size";
    private static final String PREF_TEXT_LINE_HEIGHT = "pref_text_line_height";
    private static final String PREF_TEXT_TEXT_INDENT_PT = "pref_text_text_indent_pt";
    private static final String PREF_TEXT_COLOR_INDEX = "pref_text_color_index";
    private static final String PREF_TEXT_BACKGROUND_COLOR_INDEX = "pref_text_background_color_index";
    private static final String PREF_TEXT_BACKGROUND_OPACITY = "pref_text_background_opacity";
    private static final String PREF_TEXT_BORDER_COLOR_INDEX = "pref_text_border_color_index";
    private static final String PREF_TEXT_BORDER_WIDTH_PT = "pref_text_border_width_pt";
    private static final String PREF_TEXT_BORDER_STYLE = "pref_text_border_style";
    private static final String PREF_TEXT_BORDER_RADIUS_PT = "pref_text_border_radius_pt";

    private static final float BORDER_WIDTH_MIN_PT = 0.0f;
    private static final float BORDER_WIDTH_MAX_PT = 24.0f;
    private static final float BORDER_RADIUS_MIN_PT = 0.0f;
    private static final float BORDER_RADIUS_MAX_PT = 48.0f;
    private static final float LINE_HEIGHT_MIN = 0.5f;
    private static final float LINE_HEIGHT_MAX = 5.0f;
    private static final float LINE_HEIGHT_DEFAULT = 1.2f;
    private static final float TEXT_INDENT_MIN_PT = -144.0f;
    private static final float TEXT_INDENT_MAX_PT = 144.0f;
    private static final float TEXT_INDENT_DEFAULT_PT = 0.0f;

    private final SharedPreferences prefs;
    private final float min;
    private final float max;
    private final float step;
    private final float def;

    public SharedPreferencesTextStylePrefsStore(SharedPreferences prefs,
                                                float minFontSize,
                                                float maxFontSize,
                                                float stepFontSize,
                                                float defaultFontSize) {
        this.prefs = prefs;
        this.min = minFontSize;
        this.max = maxFontSize;
        this.step = stepFontSize;
        this.def = defaultFontSize;
    }

    @Override
    public TextStylePrefsSnapshot load() {
        int fontFamily = 0;
        try {
            fontFamily = prefs.getInt(PREF_TEXT_FONT_FAMILY, 0);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_FONT_FAMILY, null);
                if (raw != null) {
                    fontFamily = Integer.parseInt(raw);
                }
            } catch (Exception ignored) {
                fontFamily = 0;
            }
            prefs.edit().remove(PREF_TEXT_FONT_FAMILY).apply();
        }
        if (fontFamily < 0) fontFamily = 0;
        if (fontFamily > 2) fontFamily = 0;

        int fontStyleFlags = 0;
        try {
            fontStyleFlags = prefs.getInt(PREF_TEXT_FONT_STYLE_FLAGS, 0);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_FONT_STYLE_FLAGS, null);
                if (raw != null) {
                    fontStyleFlags = Integer.parseInt(raw);
                }
            } catch (Exception ignored) {
                fontStyleFlags = 0;
            }
            prefs.edit().remove(PREF_TEXT_FONT_STYLE_FLAGS).apply();
        }
        fontStyleFlags = fontStyleFlags & org.opendroidpdf.app.annotation.TextStyleFlags.MASK;

        float fontSize = def;
        try {
            fontSize = prefs.getFloat(PREF_TEXT_FONT_SIZE, def);
        } catch (ClassCastException cce) {
            // Older builds may have stored as a String; migrate in-place.
            try {
                String raw = prefs.getString(PREF_TEXT_FONT_SIZE, null);
                if (raw != null) {
                    fontSize = Float.parseFloat(raw);
                }
            } catch (Exception ignored) {
                fontSize = def;
            }
            prefs.edit().remove(PREF_TEXT_FONT_SIZE).apply();
        }
        fontSize = clamp(fontSize, min, max);

        float lineHeight = LINE_HEIGHT_DEFAULT;
        try {
            lineHeight = prefs.getFloat(PREF_TEXT_LINE_HEIGHT, LINE_HEIGHT_DEFAULT);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_LINE_HEIGHT, null);
                if (raw != null) {
                    lineHeight = Float.parseFloat(raw);
                }
            } catch (Exception ignored) {
                lineHeight = LINE_HEIGHT_DEFAULT;
            }
            prefs.edit().remove(PREF_TEXT_LINE_HEIGHT).apply();
        }
        lineHeight = clamp(lineHeight, LINE_HEIGHT_MIN, LINE_HEIGHT_MAX);

        float textIndentPt = TEXT_INDENT_DEFAULT_PT;
        try {
            textIndentPt = prefs.getFloat(PREF_TEXT_TEXT_INDENT_PT, TEXT_INDENT_DEFAULT_PT);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_TEXT_INDENT_PT, null);
                if (raw != null) {
                    textIndentPt = Float.parseFloat(raw);
                }
            } catch (Exception ignored) {
                textIndentPt = TEXT_INDENT_DEFAULT_PT;
            }
            prefs.edit().remove(PREF_TEXT_TEXT_INDENT_PT).apply();
        }
        textIndentPt = clamp(textIndentPt, TEXT_INDENT_MIN_PT, TEXT_INDENT_MAX_PT);

        int colorIdx = 0;
        try {
            colorIdx = prefs.getInt(PREF_TEXT_COLOR_INDEX, 0);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_COLOR_INDEX, null);
                if (raw != null) {
                    colorIdx = Integer.parseInt(raw);
                }
            } catch (Exception ignored) {
                colorIdx = 0;
            }
            prefs.edit().remove(PREF_TEXT_COLOR_INDEX).apply();
        }

        int bgColorIdx = 13; // Yellow; ignored when opacity=0.
        try {
            bgColorIdx = prefs.getInt(PREF_TEXT_BACKGROUND_COLOR_INDEX, 13);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_BACKGROUND_COLOR_INDEX, null);
                if (raw != null) {
                    bgColorIdx = Integer.parseInt(raw);
                }
            } catch (Exception ignored) {
                bgColorIdx = 13;
            }
            prefs.edit().remove(PREF_TEXT_BACKGROUND_COLOR_INDEX).apply();
        }

        float bgOpacity = 0.0f;
        try {
            bgOpacity = prefs.getFloat(PREF_TEXT_BACKGROUND_OPACITY, 0.0f);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_BACKGROUND_OPACITY, null);
                if (raw != null) {
                    bgOpacity = Float.parseFloat(raw);
                }
            } catch (Exception ignored) {
                bgOpacity = 0.0f;
            }
            prefs.edit().remove(PREF_TEXT_BACKGROUND_OPACITY).apply();
        }
        bgOpacity = clamp(bgOpacity, 0.0f, 1.0f);

        int borderColorIdx = colorIdx;
        try {
            borderColorIdx = prefs.getInt(PREF_TEXT_BORDER_COLOR_INDEX, colorIdx);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_BORDER_COLOR_INDEX, null);
                if (raw != null) {
                    borderColorIdx = Integer.parseInt(raw);
                }
            } catch (Exception ignored) {
                borderColorIdx = colorIdx;
            }
            prefs.edit().remove(PREF_TEXT_BORDER_COLOR_INDEX).apply();
        }

        float borderWidthPt = 0.0f;
        try {
            borderWidthPt = prefs.getFloat(PREF_TEXT_BORDER_WIDTH_PT, 0.0f);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_BORDER_WIDTH_PT, null);
                if (raw != null) {
                    borderWidthPt = Float.parseFloat(raw);
                }
            } catch (Exception ignored) {
                borderWidthPt = 0.0f;
            }
            prefs.edit().remove(PREF_TEXT_BORDER_WIDTH_PT).apply();
        }
        borderWidthPt = clamp(borderWidthPt, BORDER_WIDTH_MIN_PT, BORDER_WIDTH_MAX_PT);

        int borderStyle = 0;
        try {
            borderStyle = prefs.getInt(PREF_TEXT_BORDER_STYLE, 0);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_BORDER_STYLE, null);
                if (raw != null) {
                    borderStyle = Integer.parseInt(raw);
                }
            } catch (Exception ignored) {
                borderStyle = 0;
            }
            prefs.edit().remove(PREF_TEXT_BORDER_STYLE).apply();
        }
        borderStyle = borderStyle != 0 ? 1 : 0;

        float borderRadiusPt = 0.0f;
        try {
            borderRadiusPt = prefs.getFloat(PREF_TEXT_BORDER_RADIUS_PT, 0.0f);
        } catch (ClassCastException cce) {
            try {
                String raw = prefs.getString(PREF_TEXT_BORDER_RADIUS_PT, null);
                if (raw != null) {
                    borderRadiusPt = Float.parseFloat(raw);
                }
            } catch (Exception ignored) {
                borderRadiusPt = 0.0f;
            }
            prefs.edit().remove(PREF_TEXT_BORDER_RADIUS_PT).apply();
        }
        borderRadiusPt = clamp(borderRadiusPt, BORDER_RADIUS_MIN_PT, BORDER_RADIUS_MAX_PT);

        return new TextStylePrefsSnapshot(
                fontFamily,
                fontStyleFlags,
                fontSize,
                lineHeight,
                textIndentPt,
                colorIdx,
                bgColorIdx,
                bgOpacity,
                borderColorIdx,
                borderWidthPt,
                borderStyle,
                borderRadiusPt,
                min,
                max,
                step,
                def);
    }

    @Override
    public void save(TextStylePrefsSnapshot snapshot) {
        prefs.edit()
                .putInt(PREF_TEXT_FONT_FAMILY, snapshot.fontFamily)
                .putInt(PREF_TEXT_FONT_STYLE_FLAGS, snapshot.fontStyleFlags)
                .putFloat(PREF_TEXT_FONT_SIZE, snapshot.fontSize)
                .putFloat(PREF_TEXT_LINE_HEIGHT, snapshot.lineHeight)
                .putFloat(PREF_TEXT_TEXT_INDENT_PT, snapshot.textIndentPt)
                .putInt(PREF_TEXT_COLOR_INDEX, snapshot.colorIndex)
                .putInt(PREF_TEXT_BACKGROUND_COLOR_INDEX, snapshot.backgroundColorIndex)
                .putFloat(PREF_TEXT_BACKGROUND_OPACITY, snapshot.backgroundOpacity)
                .putInt(PREF_TEXT_BORDER_COLOR_INDEX, snapshot.borderColorIndex)
                .putFloat(PREF_TEXT_BORDER_WIDTH_PT, snapshot.borderWidthPt)
                .putInt(PREF_TEXT_BORDER_STYLE, snapshot.borderStyle)
                .putFloat(PREF_TEXT_BORDER_RADIUS_PT, snapshot.borderRadiusPt)
                .apply();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
