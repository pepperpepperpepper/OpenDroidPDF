package org.opendroidpdf.app.annotation;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.PenStrokePreviewView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.preferences.PenPreferences;

import java.util.ArrayList;

/**
 * Handles the pen size/color dialog UI and persistence.
 * Host keeps control over pending ink finalization and preference-change callbacks.
 */
public class PenSettingsController {

    public interface Host {
        void finalizePendingInkBeforePenSettingChange();
        void onPenPreferenceChanged(String key);
        Context getContext();
        LayoutInflater getLayoutInflater();
    }

    private final PenPreferences penPreferences;
    private final Host host;

    public PenSettingsController(PenPreferences penPreferences, Host host) {
        this.penPreferences = penPreferences;
        this.host = host;
    }

    public void show() {
        final Context context = host.getContext();
        final float min = penPreferences.getMinThickness();
        final float max = penPreferences.getMaxThickness();
        final float step = penPreferences.getStepThickness();
        float currentThickness = clamp(penPreferences.getThickness(), min, max);

        LayoutInflater inflater = host.getLayoutInflater();
        View content = inflater.inflate(R.layout.dialog_pen_size, null, false);
        final TextView valueView = content.findViewById(R.id.pen_size_value);
        final TextView colorValueView = content.findViewById(R.id.pen_color_value);
        final PenStrokePreviewView previewView = content.findViewById(R.id.pen_size_preview);
        final SeekBar seekBar = content.findViewById(R.id.pen_size_seekbar);
        final GridLayout colorGrid = content.findViewById(R.id.pen_color_grid);
        final CharSequence[] colorNames = context.getResources().getTextArray(R.array.pen_color_names);
        final ArrayList<View> swatchViews = new ArrayList<>(colorNames.length);

        final int colorCount = colorNames.length;
        final int[] selectedColorIndex = {colorCount > 0 ? Math.max(0, Math.min(colorCount - 1, penPreferences.getColorIndex())) : 0};

        if (previewView != null) {
            previewView.setStrokeColor(ColorPalette.getHex(selectedColorIndex[0]));
        }
        updatePenColorDisplay(colorValueView, previewView, colorNames, selectedColorIndex[0]);

        final float[] lastPersisted = {currentThickness};
        final float epsilon = 1e-3f;

        if (seekBar != null) {
            int maxProgress = Math.round((max - min) / step);
            seekBar.setMax(Math.max(1, maxProgress));
            int progress = Math.round((currentThickness - min) / step);
            seekBar.setProgress(Math.max(0, Math.min(maxProgress, progress)));
            updatePenSizeDisplay(valueView, previewView, currentThickness, context);
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float value = clamp(min + (progress * step), min, max);
                    updatePenSizeDisplay(valueView, previewView, value, context);
                    if (fromUser && Math.abs(value - lastPersisted[0]) >= epsilon) {
                        host.finalizePendingInkBeforePenSettingChange();
                        persistPenSize(value);
                        lastPersisted[0] = value;
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float value = clamp(min + (seekBar.getProgress() * step), min, max);
                    if (Math.abs(value - lastPersisted[0]) < epsilon) {
                        return;
                    }
                    host.finalizePendingInkBeforePenSettingChange();
                    persistPenSize(value);
                    lastPersisted[0] = value;
                }
            });
        } else {
            updatePenSizeDisplay(valueView, previewView, currentThickness, context);
        }

        if (colorGrid != null && colorCount > 0) {
            colorGrid.removeAllViews();
            final int margin = context.getResources().getDimensionPixelSize(R.dimen.pen_color_swatch_margin);
            final int selectedStrokePx = context.getResources().getDimensionPixelSize(R.dimen.pen_color_swatch_stroke_selected);
            final int unselectedStrokePx = context.getResources().getDimensionPixelSize(R.dimen.pen_color_swatch_stroke_unselected);
            final int selectedStrokeColor = ContextCompat.getColor(context, R.color.pen_color_swatch_stroke_selected);
            final int unselectedStrokeColor = ContextCompat.getColor(context, R.color.pen_color_swatch_stroke_unselected);
            LayoutInflater swatchInflater = LayoutInflater.from(context);
            for (int i = 0; i < colorCount; i++) {
                View swatch = swatchInflater.inflate(R.layout.item_pen_color_swatch, colorGrid, false);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.setMargins(margin, margin, margin, margin);
                params.width = GridLayout.LayoutParams.WRAP_CONTENT;
                params.height = GridLayout.LayoutParams.WRAP_CONTENT;
                params.setGravity(Gravity.CENTER);
                swatch.setLayoutParams(params);
                swatch.setFocusable(true);
                swatch.setFocusableInTouchMode(true);
                swatch.setClickable(true);
                swatch.setTag(Integer.valueOf(i));
                swatch.setContentDescription(context.getString(R.string.pen_color_dialog_swatch_description, colorNames[i]));
                final int colorIndex = i;
                swatch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (selectedColorIndex[0] == colorIndex) {
                            return;
                        }
                        host.finalizePendingInkBeforePenSettingChange();
                        persistInkColor(colorIndex);
                        selectedColorIndex[0] = colorIndex;
                        updatePenColorDisplay(colorValueView, previewView, colorNames, colorIndex);
                        refreshPenColorSwatches(swatchViews, selectedColorIndex[0], selectedStrokePx, selectedStrokeColor, unselectedStrokePx, unselectedStrokeColor);
                    }
                });
                swatchViews.add(swatch);
                colorGrid.addView(swatch);
            }
            refreshPenColorSwatches(swatchViews, selectedColorIndex[0], selectedStrokePx, selectedStrokeColor, unselectedStrokePx, unselectedStrokeColor);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.pen_size_dialog_title);
        builder.setView(content);
        builder.show();
    }

    private void persistPenSize(float value) {
        penPreferences.setThickness(value);
        host.onPenPreferenceChanged(org.opendroidpdf.SettingsActivity.PREF_INK_THICKNESS);
    }

    private void persistInkColor(int index) {
        penPreferences.setColorIndex(index);
        host.onPenPreferenceChanged(org.opendroidpdf.SettingsActivity.PREF_INK_COLOR);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updatePenSizeDisplay(TextView valueView, PenStrokePreviewView previewView, float value, Context context) {
        if (valueView != null) {
            valueView.setText(context.getString(R.string.pen_size_dialog_value, value));
        }
        if (previewView != null) {
            previewView.setStrokeWidthDocUnits(value);
        }
    }

    private void updatePenColorDisplay(TextView colorValueView, PenStrokePreviewView previewView, CharSequence[] colorNames, int index) {
        if (colorValueView != null && colorNames != null && index >= 0 && index < colorNames.length) {
            colorValueView.setText(colorNames[index]);
        }
        if (previewView != null) {
            previewView.setStrokeColor(ColorPalette.getHex(index));
        }
    }

    private void refreshPenColorSwatches(ArrayList<View> swatchViews, int selectedIndex, int selectedStrokePx, int selectedStrokeColor, int unselectedStrokePx, int unselectedStrokeColor) {
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
                circle.setBackground(createPenColorDrawable(
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

    private GradientDrawable createPenColorDrawable(int color, boolean selected, int selectedStrokePx, int selectedStrokeColor, int unselectedStrokePx, int unselectedStrokeColor) {
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
}
