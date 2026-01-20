package org.opendroidpdf.app.annotation;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.R;

import java.util.ArrayList;

final class TextAnnotationStyleSwatches {

    interface Listener {
        void onClick(int colorIndex);
    }

    static final class Config {
        final int marginPx;
        final int selectedStrokePx;
        final int unselectedStrokePx;
        final int selectedStrokeColor;
        final int unselectedStrokeColor;

        private Config(int marginPx,
                       int selectedStrokePx,
                       int unselectedStrokePx,
                       int selectedStrokeColor,
                       int unselectedStrokeColor) {
            this.marginPx = marginPx;
            this.selectedStrokePx = selectedStrokePx;
            this.unselectedStrokePx = unselectedStrokePx;
            this.selectedStrokeColor = selectedStrokeColor;
            this.unselectedStrokeColor = unselectedStrokeColor;
        }

        static Config from(@NonNull Context context) {
            final int margin = context.getResources().getDimensionPixelSize(R.dimen.pen_color_swatch_margin);
            final int selectedStrokePx = context.getResources().getDimensionPixelSize(R.dimen.pen_color_swatch_stroke_selected);
            final int unselectedStrokePx = context.getResources().getDimensionPixelSize(R.dimen.pen_color_swatch_stroke_unselected);
            final int selectedStrokeColor = ContextCompat.getColor(context, R.color.pen_color_swatch_stroke_selected);
            final int unselectedStrokeColor = ContextCompat.getColor(context, R.color.pen_color_swatch_stroke_unselected);
            return new Config(margin, selectedStrokePx, unselectedStrokePx, selectedStrokeColor, unselectedStrokeColor);
        }
    }

    private TextAnnotationStyleSwatches() {
    }

    static void buildGrid(@NonNull Context context,
                          @NonNull GridLayout grid,
                          @NonNull ArrayList<View> outViews,
                          @NonNull CharSequence[] colorNames,
                          @NonNull Config config,
                          int selectedIndex,
                          int contentDescriptionResId,
                          @NonNull Listener listener) {
        outViews.clear();
        grid.removeAllViews();

        LayoutInflater swatchInflater = LayoutInflater.from(context);
        for (int i = 0; i < colorNames.length; i++) {
            View swatch = swatchInflater.inflate(R.layout.item_pen_color_swatch, grid, false);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.setMargins(config.marginPx, config.marginPx, config.marginPx, config.marginPx);
            params.width = GridLayout.LayoutParams.WRAP_CONTENT;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setGravity(Gravity.CENTER);
            swatch.setLayoutParams(params);
            swatch.setClickable(true);
            swatch.setTag(Integer.valueOf(i));
            swatch.setContentDescription(context.getString(contentDescriptionResId, colorNames[i]));

            final int colorIndex = i;
            swatch.setOnClickListener(v -> listener.onClick(colorIndex));

            outViews.add(swatch);
            grid.addView(swatch);
        }

        refreshSwatches(outViews, selectedIndex, config);
    }

    static void refreshSwatches(@Nullable ArrayList<View> swatchViews, int selectedIndex, @NonNull Config config) {
        if (swatchViews == null) return;
        for (int i = 0; i < swatchViews.size(); i++) {
            View swatch = swatchViews.get(i);
            if (swatch == null) continue;
            View circle = swatch.findViewById(R.id.pen_color_circle);
            if (circle != null) {
                circle.setBackground(createColorDrawable(ColorPalette.getHex(i), i == selectedIndex, config));
            }
            swatch.setSelected(i == selectedIndex);
        }
    }

    private static GradientDrawable createColorDrawable(int color, boolean selected, @NonNull Config config) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        int strokeWidth = selected ? config.selectedStrokePx : config.unselectedStrokePx;
        int strokeColor = selected ? config.selectedStrokeColor : config.unselectedStrokeColor;
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }
}

