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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.ColorPalette;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.PenStrokePreviewView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.preferences.TextStylePrefsSnapshot;
import org.opendroidpdf.app.services.TextStylePreferencesService;

import java.util.ArrayList;

/**
 * Controls the "Text style" dialog and applies color/size changes to the currently selected FreeText annotation.
 *
 * <p>This controller is strictly for embedded PDF FreeText; sidecar parity is handled separately.</p>
 */
public class TextAnnotationStyleController {

    public interface Host {
        @NonNull Context getContext();
        @NonNull LayoutInflater getLayoutInflater();
        @Nullable MuPDFPageView activePageViewOrNull();
        void showAnnotationInfo(@NonNull String message);
    }

    private final TextStylePreferencesService prefs;
    private final Host host;

    public TextAnnotationStyleController(@NonNull TextStylePreferencesService prefs,
                                         @NonNull Host host) {
        this.prefs = prefs;
        this.host = host;
    }

    public void show() {
        final Context context = host.getContext();
        final MuPDFPageView pageView = host.activePageViewOrNull();
        if (pageView == null) {
            host.showAnnotationInfo(context.getString(R.string.select_text_annot_to_style));
            return;
        }
        Annotation.Type selectedType = null;
        try { selectedType = pageView.selectedAnnotationType(); } catch (Throwable ignore) { selectedType = null; }
        if (selectedType != Annotation.Type.FREETEXT) {
            host.showAnnotationInfo(context.getString(R.string.select_text_annot_to_style));
            return;
        }

        TextStylePrefsSnapshot snap = prefs.get();
        final float min = snap.minFontSize;
        final float max = snap.maxFontSize;
        final float step = snap.stepFontSize;
        float currentSize = clamp(snap.fontSize, min, max);

        LayoutInflater inflater = host.getLayoutInflater();
        View content = inflater.inflate(R.layout.dialog_pen_size, null, false);

        final TextView summaryView = content.findViewById(R.id.pen_size_summary);
        if (summaryView != null) {
            summaryView.setText(R.string.text_style_dialog_summary);
        }
        final TextView valueView = content.findViewById(R.id.pen_size_value);
        final TextView colorValueView = content.findViewById(R.id.pen_color_value);
        final PenStrokePreviewView previewView = content.findViewById(R.id.pen_size_preview);
        final SeekBar seekBar = content.findViewById(R.id.pen_size_seekbar);
        final GridLayout colorGrid = content.findViewById(R.id.pen_color_grid);
        final CharSequence[] colorNames = context.getResources().getTextArray(R.array.pen_color_names);
        final ArrayList<View> swatchViews = new ArrayList<>(colorNames.length);

        final int colorCount = colorNames.length;
        final int[] selectedColorIndex = {colorCount > 0 ? Math.max(0, Math.min(colorCount - 1, snap.colorIndex)) : 0};

        if (previewView != null) {
            previewView.setStrokeColor(ColorPalette.getHex(selectedColorIndex[0]));
        }
        updateColorDisplay(colorValueView, previewView, colorNames, selectedColorIndex[0]);

        final float[] lastPersisted = {currentSize};
        final float epsilon = 1e-3f;

        if (seekBar != null) {
            int maxProgress = Math.round((max - min) / step);
            seekBar.setMax(Math.max(1, maxProgress));
            int progress = Math.round((currentSize - min) / step);
            seekBar.setProgress(Math.max(0, Math.min(maxProgress, progress)));
            updateSizeDisplay(valueView, previewView, currentSize, context);
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float value = clamp(min + (progress * step), min, max);
                    updateSizeDisplay(valueView, previewView, value, context);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float value = clamp(min + (seekBar.getProgress() * step), min, max);
                    if (Math.abs(value - lastPersisted[0]) < epsilon) {
                        return;
                    }
                    prefs.setFontSize(value);
                    lastPersisted[0] = value;
                    boolean ok = false;
                    try { ok = pageView.applyTextStyleToSelectedTextAnnotation(value, selectedColorIndex[0]); } catch (Throwable ignore) { ok = false; }
                    if (!ok) {
                        Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                    }
                }
            });
        } else {
            updateSizeDisplay(valueView, previewView, currentSize, context);
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
                        prefs.setColorIndex(colorIndex);
                        selectedColorIndex[0] = colorIndex;
                        updateColorDisplay(colorValueView, previewView, colorNames, colorIndex);
                        refreshSwatches(swatchViews, selectedColorIndex[0], selectedStrokePx, selectedStrokeColor, unselectedStrokePx, unselectedStrokeColor);
                        boolean ok = false;
                        try { ok = pageView.applyTextStyleToSelectedTextAnnotation(lastPersisted[0], colorIndex); } catch (Throwable ignore) { ok = false; }
                        if (!ok) {
                            Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                        }
                    }
                });
                swatchViews.add(swatch);
                colorGrid.addView(swatch);
            }
            refreshSwatches(swatchViews, selectedColorIndex[0], selectedStrokePx, selectedStrokeColor, unselectedStrokePx, unselectedStrokeColor);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.text_style_dialog_title);
        builder.setView(content);
        builder.show();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void updateSizeDisplay(TextView valueView, PenStrokePreviewView previewView, float value, Context context) {
        if (valueView != null) {
            valueView.setText(context.getString(R.string.pen_size_dialog_value, value));
        }
        if (previewView != null) {
            previewView.setStrokeWidthDocUnits(value);
        }
    }

    private void updateColorDisplay(TextView colorValueView, PenStrokePreviewView previewView, CharSequence[] colorNames, int index) {
        if (colorValueView != null && colorNames != null && index >= 0 && index < colorNames.length) {
            colorValueView.setText(colorNames[index]);
        }
        if (previewView != null) {
            previewView.setStrokeColor(ColorPalette.getHex(index));
        }
    }

    private void refreshSwatches(ArrayList<View> swatchViews, int selectedIndex, int selectedStrokePx, int selectedStrokeColor, int unselectedStrokePx, int unselectedStrokeColor) {
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
                circle.setBackground(createColorDrawable(
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

    private GradientDrawable createColorDrawable(int color, boolean selected, int selectedStrokePx, int selectedStrokeColor, int unselectedStrokePx, int unselectedStrokeColor) {
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

