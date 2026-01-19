package org.opendroidpdf.app.annotation;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.preferences.PreferencesCoordinator;
import org.opendroidpdf.app.preferences.PreferencesNames;

/**
 * Simple controller for adjusting eraser size while in erasing mode.
 *
 * <p>Persisted to the existing {@link SettingsActivity#PREF_ERASER_THICKNESS} preference so the
 * value stays consistent with Settings.</p>
 */
public final class EraserSettingsController {

    private final OpenDroidPDFActivity activity;
    private final PreferencesCoordinator preferencesCoordinator;
    private final SharedPreferences prefs;

    public EraserSettingsController(OpenDroidPDFActivity activity,
                                   PreferencesCoordinator preferencesCoordinator) {
        this.activity = activity;
        this.preferencesCoordinator = preferencesCoordinator;
        this.prefs = activity.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS);
    }

    public void show() {
        if (activity == null) return;

        float min = resFloat(R.dimen.eraser_size_min, 2.0f);
        float max = resFloat(R.dimen.eraser_size_max, 50.0f);
        float step = resFloat(R.dimen.eraser_size_step, 0.5f);
        if (step <= 0f) step = 0.5f;
        if (max < min) {
            float tmp = min;
            min = max;
            max = tmp;
        }

        float current = min;
        try {
            if (preferencesCoordinator != null) {
                current = preferencesCoordinator.editorPrefsSnapshot().eraserThickness;
            }
        } catch (Throwable ignore) {
            current = min;
        }
        current = clamp(current, min, max);

        LayoutInflater inflater = activity.getLayoutInflater();
        View content = inflater.inflate(R.layout.dialog_eraser_size, null, false);
        final TextView valueView = content.findViewById(R.id.eraser_size_value);
        final SeekBar seekBar = content.findViewById(R.id.eraser_size_seekbar);

        final int maxProgress = Math.round((max - min) / step);
        final float fMin = min;
        final float fMax = max;
        final float fStep = step;

        if (seekBar != null) {
            seekBar.setMax(Math.max(1, maxProgress));
            int progress = Math.round((current - fMin) / fStep);
            progress = Math.max(0, Math.min(maxProgress, progress));
            seekBar.setProgress(progress);
        }

        updateValue(valueView, fMin + (seekBar != null ? (seekBar.getProgress() * fStep) : 0f), activity);

        if (seekBar != null) {
            final float[] lastPersisted = {current};
            final float epsilon = 1e-3f;

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float value = clamp(fMin + (progress * fStep), fMin, fMax);
                    updateValue(valueView, value, activity);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float value = clamp(fMin + (seekBar.getProgress() * fStep), fMin, fMax);
                    if (Math.abs(value - lastPersisted[0]) < epsilon) {
                        return;
                    }
                    persistEraserSize(value);
                    lastPersisted[0] = value;
                }
            });
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.eraser_size_dialog_title)
                .setView(content)
                .show();
    }

    private void persistEraserSize(float value) {
        try {
            prefs.edit()
                    .putString(SettingsActivity.PREF_ERASER_THICKNESS, Float.toString(value))
                    .apply();
        } catch (Throwable ignore) {
        }
        try {
            if (preferencesCoordinator != null) {
                preferencesCoordinator.refreshAndApply();
            }
        } catch (Throwable ignore) {
        }
    }

    private float resFloat(int resId, float fallback) {
        try {
            TypedValue tv = new TypedValue();
            activity.getResources().getValue(resId, tv, true);
            return tv.getFloat();
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void updateValue(TextView valueView, float value, Context context) {
        if (valueView == null || context == null) return;
        valueView.setText(context.getString(R.string.eraser_size_dialog_value, value));
    }
}

