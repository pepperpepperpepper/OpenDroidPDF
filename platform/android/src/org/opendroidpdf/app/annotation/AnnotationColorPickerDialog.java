package org.opendroidpdf.app.annotation;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.R;
import org.opendroidpdf.app.preferences.PreferencesNames;

import java.util.ArrayList;

/** Shared color palette dialog used for tool-specific annotation colors. */
public final class AnnotationColorPickerDialog {
    private AnnotationColorPickerDialog() {}

    public static void show(@NonNull Context context,
                            @StringRes int titleResId,
                            @StringRes int summaryResId,
                            @StringRes int toolLabelResId,
                            @NonNull String prefKey,
                            int defaultIndex) {
        final SharedPreferences prefs = context.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
        final CharSequence[] colorNames = context.getResources().getTextArray(R.array.pen_color_names);
        if (colorNames == null || colorNames.length == 0) {
            return;
        }

        final int[] selectedColorIndex = {clamp(readPrefIntString(prefs, prefKey, defaultIndex), 0, colorNames.length - 1)};

        LayoutInflater inflater = LayoutInflater.from(context);
        View content = inflater.inflate(R.layout.dialog_color_palette, null, false);

        TextView summaryView = content.findViewById(R.id.color_palette_summary);
        if (summaryView != null) {
            summaryView.setText(summaryResId);
        }

        final TextView valueView = content.findViewById(R.id.color_palette_value);
        updateSelectedColorLabel(valueView, colorNames, selectedColorIndex[0]);

        final GridLayout grid = content.findViewById(R.id.color_palette_grid);
        if (grid != null) {
            grid.removeAllViews();
            final int margin = context.getResources().getDimensionPixelSize(R.dimen.pen_color_swatch_margin);
            final int selectedStrokePx = context.getResources().getDimensionPixelSize(R.dimen.pen_color_swatch_stroke_selected);
            final int unselectedStrokePx = context.getResources().getDimensionPixelSize(R.dimen.pen_color_swatch_stroke_unselected);
            final int selectedStrokeColor = ContextCompat.getColor(context, R.color.pen_color_swatch_stroke_selected);
            final int unselectedStrokeColor = ContextCompat.getColor(context, R.color.pen_color_swatch_stroke_unselected);
            final ArrayList<View> swatchViews = new ArrayList<>(colorNames.length);

            for (int i = 0; i < colorNames.length; i++) {
                View swatch = inflater.inflate(R.layout.item_pen_color_swatch, grid, false);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.setMargins(margin, margin, margin, margin);
                params.width = GridLayout.LayoutParams.WRAP_CONTENT;
                params.height = GridLayout.LayoutParams.WRAP_CONTENT;
                params.setGravity(Gravity.CENTER);
                swatch.setLayoutParams(params);
                swatch.setClickable(true);
                swatch.setTag(Integer.valueOf(i));
                swatch.setContentDescription(context.getString(
                        R.string.annotation_color_dialog_swatch_description,
                        context.getString(toolLabelResId),
                        colorNames[i]));
                final int colorIndex = i;
                swatch.setOnClickListener(v -> {
                    if (selectedColorIndex[0] == colorIndex) {
                        return;
                    }
                    persistColorIndex(prefs, prefKey, colorIndex);
                    selectedColorIndex[0] = colorIndex;
                    updateSelectedColorLabel(valueView, colorNames, selectedColorIndex[0]);
                    refreshSwatches(swatchViews, selectedColorIndex[0], selectedStrokePx, selectedStrokeColor, unselectedStrokePx, unselectedStrokeColor);
                });
                swatchViews.add(swatch);
                grid.addView(swatch);
            }
            refreshSwatches(swatchViews, selectedColorIndex[0], selectedStrokePx, selectedStrokeColor, unselectedStrokePx, unselectedStrokeColor);
        }

        new AlertDialog.Builder(context)
                .setTitle(titleResId)
                .setView(content)
                .show();
    }

    private static void updateSelectedColorLabel(TextView valueView, CharSequence[] colorNames, int index) {
        if (valueView == null || colorNames == null) {
            return;
        }
        int safeIndex = clamp(index, 0, colorNames.length - 1);
        valueView.setText(colorNames[safeIndex]);
    }

    private static void persistColorIndex(SharedPreferences prefs, String prefKey, int index) {
        try {
            prefs.edit()
                    .putString(prefKey, Integer.toString(index))
                    .apply();
        } catch (Throwable ignore) {
        }
    }

    private static void refreshSwatches(ArrayList<View> swatchViews,
                                        int selectedIndex,
                                        int selectedStrokePx,
                                        int selectedStrokeColor,
                                        int unselectedStrokePx,
                                        int unselectedStrokeColor) {
        if (swatchViews == null) {
            return;
        }
        for (int i = 0; i < swatchViews.size(); i++) {
            View swatch = swatchViews.get(i);
            if (swatch == null) {
                continue;
            }
            View circle = swatch.findViewById(R.id.pen_color_circle);
            if (circle != null) {
                circle.setBackground(createSwatchDrawable(
                        ColorPalette.getHex(i),
                        i == selectedIndex,
                        selectedStrokePx,
                        selectedStrokeColor,
                        unselectedStrokePx,
                        unselectedStrokeColor));
            }
            swatch.setSelected(i == selectedIndex);
        }
    }

    private static GradientDrawable createSwatchDrawable(int color,
                                                         boolean selected,
                                                         int selectedStrokePx,
                                                         int selectedStrokeColor,
                                                         int unselectedStrokePx,
                                                         int unselectedStrokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        int strokeWidth = selected ? selectedStrokePx : unselectedStrokePx;
        int strokeColor = selected ? selectedStrokeColor : unselectedStrokeColor;
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private static int readPrefIntString(SharedPreferences prefs, String key, int def) {
        try {
            String raw = prefs.getString(key, Integer.toString(def));
            if (raw == null) return def;
            return Integer.parseInt(raw.replaceAll("[^0-9-]", ""));
        } catch (ClassCastException cce) {
            try {
                return prefs.getInt(key, def);
            } catch (Throwable ignore) {
                return def;
            }
        } catch (Throwable t) {
            return def;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

