package org.opendroidpdf.app.annotation;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.preferences.TextStylePrefsSnapshot;
import org.opendroidpdf.app.services.TextStylePreferencesService;

import java.util.ArrayList;

final class TextAnnotationStyleDialogBinder {
    private TextAnnotationStyleDialogBinder() {
    }

    static void showDialog(TextStylePreferencesService prefs,
                           TextAnnotationStyleController.Host host,
                           MuPDFPageView pageView) {
        final Context context = host.getContext();

        TextStylePrefsSnapshot snap = prefs.get();
        final float min = snap.minFontSize;
        final float max = snap.maxFontSize;
        final float step = snap.stepFontSize;
        float currentSize = clamp(snap.fontSize, min, max);
        currentSize = clamp(TextAnnotationStyleApplier.selectedFontSizeOrDefault(pageView, currentSize), min, max);

        View content = host.getLayoutInflater().inflate(R.layout.dialog_text_style, null, false);

        final TextView summaryView = content.findViewById(R.id.text_style_summary);
        final TextView valueView = content.findViewById(R.id.text_style_size_value);
        final TextView colorValueView = content.findViewById(R.id.text_style_color_value);
        final SeekBar seekBar = content.findViewById(R.id.text_style_size_seekbar);
        final View fontFamilyLabel = content.findViewById(R.id.text_style_font_family_label);
        final android.widget.RadioGroup fontFamilyGroup = content.findViewById(R.id.text_style_font_family_group);
        final View fontStyleLabel = content.findViewById(R.id.text_style_font_style_label);
        final View fontStyleGroup = content.findViewById(R.id.text_style_font_style_group);
        final android.widget.CheckBox fontStyleBold = content.findViewById(R.id.text_style_font_style_bold);
        final android.widget.CheckBox fontStyleItalic = content.findViewById(R.id.text_style_font_style_italic);
        final android.widget.CheckBox fontStyleUnderline = content.findViewById(R.id.text_style_font_style_underline);
        final android.widget.CheckBox fontStyleStrike = content.findViewById(R.id.text_style_font_style_strike);
        final ViewGroup colorStrip = content.findViewById(R.id.text_style_color_strip);
        final TextView backgroundOpacityValueView = content.findViewById(R.id.text_style_background_opacity_value);
        final SeekBar backgroundOpacitySeekBar = content.findViewById(R.id.text_style_background_opacity_seekbar);
        final TextView backgroundColorValueView = content.findViewById(R.id.text_style_background_color_value);
        final ViewGroup backgroundColorStrip = content.findViewById(R.id.text_style_background_color_strip);
        final TextView borderWidthValueView = content.findViewById(R.id.text_style_border_width_value);
        final SeekBar borderWidthSeekBar = content.findViewById(R.id.text_style_border_width_seekbar);
        final TextView borderColorValueView = content.findViewById(R.id.text_style_border_color_value);
        final ViewGroup borderColorStrip = content.findViewById(R.id.text_style_border_color_strip);
        final android.widget.RadioGroup borderStyleGroup = content.findViewById(R.id.text_style_border_style_group);
        final TextView borderRadiusValueView = content.findViewById(R.id.text_style_border_radius_value);
        final SeekBar borderRadiusSeekBar = content.findViewById(R.id.text_style_border_radius_seekbar);
        final android.widget.CheckBox lockPositionSizeCheck = content.findViewById(R.id.text_style_lock_position_size);
        final android.widget.CheckBox lockContentsCheck = content.findViewById(R.id.text_style_lock_contents);
        final View alignmentLabel = content.findViewById(R.id.text_style_alignment_label);
        final android.widget.RadioGroup alignmentGroup = content.findViewById(R.id.text_style_alignment_group);
        final View fitToTextButton = content.findViewById(R.id.text_style_fit_to_text);
        final View rotationLabel = content.findViewById(R.id.text_style_rotation_label);
        final android.widget.RadioGroup rotationGroup = content.findViewById(R.id.text_style_rotation_group);
        final View lineHeightLabel = content.findViewById(R.id.text_style_line_height_label);
        final TextView lineHeightValueView = content.findViewById(R.id.text_style_line_height_value);
        final SeekBar lineHeightSeekBar = content.findViewById(R.id.text_style_line_height_seekbar);
        final View textIndentLabel = content.findViewById(R.id.text_style_text_indent_label);
        final TextView textIndentValueView = content.findViewById(R.id.text_style_text_indent_value);
        final SeekBar textIndentSeekBar = content.findViewById(R.id.text_style_text_indent_seekbar);

        final CharSequence[] colorNames = context.getResources().getTextArray(R.array.pen_color_names);
        final ArrayList<View> swatchViews = new ArrayList<>(colorNames.length);
        final ArrayList<View> backgroundSwatchViews = new ArrayList<>(colorNames.length);
        final ArrayList<View> borderSwatchViews = new ArrayList<>(colorNames.length);
        final TextAnnotationStyleSwatches.Config swatchConfig = TextAnnotationStyleSwatches.Config.from(context);

        final int colorCount = colorNames.length;
        final int[] selectedColorIndex = {colorCount > 0 ? Math.max(0, Math.min(colorCount - 1, snap.colorIndex)) : 0};
        final int[] lastFontFamily = {TextFontFamily.normalize(snap.fontFamily)};
        final int[] lastStyleFlags = {TextStyleFlags.normalize(snap.fontStyleFlags)};
        final int[] selectedBackgroundColorIndex = {colorCount > 0 ? Math.max(0, Math.min(colorCount - 1, snap.backgroundColorIndex)) : 0};
        final float[] lastBackgroundOpacity = {clamp(snap.backgroundOpacity, 0.0f, 1.0f)};
        final int[] selectedBorderColorIndex = {colorCount > 0 ? Math.max(0, Math.min(colorCount - 1, snap.borderColorIndex)) : 0};
        final float[] borderWidthPt = {clamp(snap.borderWidthPt, 0.0f, 24.0f)};
        final float[] persistedBorderWidthPt = {borderWidthPt[0]};
        final int[] lastBorderStyle = {snap.borderStyle != 0 ? 1 : 0};
        final float[] borderRadiusPt = {clamp(snap.borderRadiusPt, 0.0f, 48.0f)};
        final float[] persistedBorderRadiusPt = {borderRadiusPt[0]};

        final float lineHeightMin = 0.8f;
        final float lineHeightMax = 3.0f;
        final float lineHeightStep = 0.05f;
        final float[] lineHeight = {clamp(snap.lineHeight, lineHeightMin, lineHeightMax)};
        final float[] persistedLineHeight = {lineHeight[0]};

        final float indentMinPt = 0.0f;
        final float indentMaxPt = 72.0f;
        final float indentStepPt = 1.0f;
        final float[] textIndentPt = {clamp(snap.textIndentPt, indentMinPt, indentMaxPt)};
        final float[] persistedTextIndentPt = {textIndentPt[0]};

        final boolean[] lastLockPositionSize = {TextAnnotationStyleApplier.selectedLockPositionSizeOrDefault(pageView, false)};
        final boolean[] lastLockContents = {TextAnnotationStyleApplier.selectedLockContentsOrDefault(pageView, false)};
        final int[] lastRotationDegrees = {TextAnnotationStyleApplier.selectedRotationDegOrDefault(pageView, 0)};
        lastFontFamily[0] = TextFontFamily.normalize(TextAnnotationStyleApplier.selectedFontFamilyOrDefault(pageView, lastFontFamily[0]));
        lastStyleFlags[0] = TextStyleFlags.normalize(TextAnnotationStyleApplier.selectedStyleFlagsOrDefault(pageView, lastStyleFlags[0]));
        lineHeight[0] = clamp(TextAnnotationStyleApplier.selectedLineHeightOrDefault(pageView, lineHeight[0]), lineHeightMin, lineHeightMax);
        textIndentPt[0] = clamp(TextAnnotationStyleApplier.selectedTextIndentPtOrDefault(pageView, textIndentPt[0]), indentMinPt, indentMaxPt);
        persistedLineHeight[0] = lineHeight[0];
        persistedTextIndentPt[0] = textIndentPt[0];

        updateColorDisplay(colorValueView, colorNames, selectedColorIndex[0]);
        updateColorDisplay(backgroundColorValueView, colorNames, selectedBackgroundColorIndex[0]);
        updateOpacityDisplay(backgroundOpacityValueView, lastBackgroundOpacity[0], context);
        updateLineHeightDisplay(lineHeightValueView, lineHeight[0], context);
        updateSizeDisplay(textIndentValueView, textIndentPt[0], context);
        updateColorDisplay(borderColorValueView, colorNames, selectedBorderColorIndex[0]);
        updateSizeDisplay(borderWidthValueView, borderWidthPt[0], context);
        updateSizeDisplay(borderRadiusValueView, borderRadiusPt[0], context);

        final float[] lastPersisted = {currentSize};
        final float epsilon = 1e-3f;
        final float opacityEpsilon = 0.005f;

        final Runnable applyBorder = () -> {
            boolean ok = TextAnnotationStyleApplier.applyBorder(pageView, selectedBorderColorIndex[0], borderWidthPt[0], lastBorderStyle[0] != 0, borderRadiusPt[0]);
            if (!ok) Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
        };
        final int borderApplyDebounceMs = 180;
        final Runnable applyBorderDebounced = () -> applyBorder.run();

        final Runnable refreshLockUi = () -> {
            boolean lockPos = lockPositionSizeCheck != null && lockPositionSizeCheck.isChecked();
            boolean lockContents = lockContentsCheck != null && lockContentsCheck.isChecked();
            boolean enableStyle = !lockContents;

            setEnabledDeep(seekBar, enableStyle);
            setEnabledDeep(fontFamilyGroup, enableStyle);
            setEnabledDeep(fontFamilyLabel, enableStyle);
            setEnabledDeep(fontStyleGroup, enableStyle);
            setEnabledDeep(fontStyleLabel, enableStyle);
            setEnabledDeep(colorStrip, enableStyle);
            setEnabledDeep(backgroundOpacitySeekBar, enableStyle);
            setEnabledDeep(backgroundColorStrip, enableStyle);
            setEnabledDeep(borderWidthSeekBar, enableStyle);
            setEnabledDeep(borderColorStrip, enableStyle);
            setEnabledDeep(borderStyleGroup, enableStyle);
            setEnabledDeep(borderRadiusSeekBar, enableStyle);
            setEnabledDeep(alignmentGroup, enableStyle);
            setEnabledDeep(alignmentLabel, enableStyle);
            setEnabledDeep(lineHeightSeekBar, enableStyle);
            setEnabledDeep(lineHeightLabel, enableStyle);
            setEnabledDeep(lineHeightValueView, enableStyle);
            setEnabledDeep(textIndentSeekBar, enableStyle);
            setEnabledDeep(textIndentLabel, enableStyle);
            setEnabledDeep(textIndentValueView, enableStyle);
            setEnabledDeep(rotationGroup, enableStyle);
            setEnabledDeep(rotationLabel, enableStyle);

            // Resizing the box is a geometry change; respect the lock position/size toggle.
            if (fitToTextButton != null) setEnabledDeep(fitToTextButton, enableStyle && !lockPos);
        };

        if (seekBar != null) {
            int maxProgress = Math.round((max - min) / step);
            seekBar.setMax(Math.max(1, maxProgress));
            int progress = Math.round((currentSize - min) / step);
            seekBar.setProgress(Math.max(0, Math.min(maxProgress, progress)));
            updateSizeDisplay(valueView, currentSize, context);
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float value = clamp(min + (progress * step), min, max);
                    updateSizeDisplay(valueView, value, context);
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float value = clamp(min + (seekBar.getProgress() * step), min, max);
                    if (Math.abs(value - lastPersisted[0]) < epsilon) return;
                    prefs.setFontSize(value);
                    lastPersisted[0] = value;
                    boolean ok = TextAnnotationStyleApplier.applyTextStyle(pageView, value, selectedColorIndex[0]);
                    if (!ok) Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            updateSizeDisplay(valueView, currentSize, context);
        }

        final Runnable applyParagraph = () -> {
            boolean ok = TextAnnotationStyleApplier.applyParagraph(pageView, lineHeight[0], textIndentPt[0]);
            if (!ok) Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
        };

        if (lineHeightSeekBar != null) {
            int maxProgress = Math.round((lineHeightMax - lineHeightMin) / lineHeightStep);
            lineHeightSeekBar.setMax(Math.max(1, maxProgress));
            int progress = Math.round((lineHeight[0] - lineHeightMin) / lineHeightStep);
            lineHeightSeekBar.setProgress(Math.max(0, Math.min(maxProgress, progress)));
            updateLineHeightDisplay(lineHeightValueView, lineHeight[0], context);
            lineHeightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float value = clamp(lineHeightMin + (progress * lineHeightStep), lineHeightMin, lineHeightMax);
                    lineHeight[0] = value;
                    updateLineHeightDisplay(lineHeightValueView, value, context);
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float value = clamp(lineHeightMin + (seekBar.getProgress() * lineHeightStep), lineHeightMin, lineHeightMax);
                    if (Math.abs(value - persistedLineHeight[0]) < 0.001f) return;
                    prefs.setLineHeight(value);
                    persistedLineHeight[0] = value;
                    lineHeight[0] = value;
                    applyParagraph.run();
                }
            });
        } else {
            updateLineHeightDisplay(lineHeightValueView, lineHeight[0], context);
        }

        if (textIndentSeekBar != null) {
            int maxProgress = Math.round((indentMaxPt - indentMinPt) / indentStepPt);
            textIndentSeekBar.setMax(Math.max(1, maxProgress));
            int progress = Math.round((textIndentPt[0] - indentMinPt) / indentStepPt);
            textIndentSeekBar.setProgress(Math.max(0, Math.min(maxProgress, progress)));
            updateSizeDisplay(textIndentValueView, textIndentPt[0], context);
            textIndentSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float value = clamp(indentMinPt + (progress * indentStepPt), indentMinPt, indentMaxPt);
                    textIndentPt[0] = value;
                    updateSizeDisplay(textIndentValueView, value, context);
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float value = clamp(indentMinPt + (seekBar.getProgress() * indentStepPt), indentMinPt, indentMaxPt);
                    if (Math.abs(value - persistedTextIndentPt[0]) < 0.5f) return;
                    prefs.setTextIndentPt(value);
                    persistedTextIndentPt[0] = value;
                    textIndentPt[0] = value;
                    applyParagraph.run();
                }
            });
        } else {
            updateSizeDisplay(textIndentValueView, textIndentPt[0], context);
        }

        boolean embeddedFreeText = false;
        try { embeddedFreeText = pageView.selectedAnnotationType() == Annotation.Type.FREETEXT; } catch (Throwable ignore) { }
        if (!embeddedFreeText) {
            if (alignmentLabel != null) alignmentLabel.setVisibility(View.GONE);
            if (alignmentGroup != null) alignmentGroup.setVisibility(View.GONE);
            if (fitToTextButton != null) fitToTextButton.setVisibility(View.GONE);
        } else {
            if (alignmentGroup != null) {
                int q = TextAnnotationStyleApplier.selectedAlignmentOrDefault(pageView, 0);
                int checkedId = R.id.text_style_align_left;
                if (q == 1) checkedId = R.id.text_style_align_center;
                else if (q == 2) checkedId = R.id.text_style_align_right;
                try { alignmentGroup.check(checkedId); } catch (Throwable ignore) {}
                alignmentGroup.setOnCheckedChangeListener((group, checked) -> {
                    int nextQ = 0;
                    if (checked == R.id.text_style_align_center) nextQ = 1;
                    else if (checked == R.id.text_style_align_right) nextQ = 2;
                    boolean ok = TextAnnotationStyleApplier.applyAlignment(pageView, nextQ);
                    if (!ok) Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                });
            }
            if (fitToTextButton != null) {
                fitToTextButton.setOnClickListener(v -> {
                    boolean ok = TextAnnotationStyleApplier.fitToText(pageView);
                    if (!ok) Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                });
            }
        }

        if (fontFamilyGroup != null) {
            int checkedId = R.id.text_style_font_family_sans;
            if (lastFontFamily[0] == TextFontFamily.SERIF) checkedId = R.id.text_style_font_family_serif;
            else if (lastFontFamily[0] == TextFontFamily.MONO) checkedId = R.id.text_style_font_family_mono;
            try { fontFamilyGroup.check(checkedId); } catch (Throwable ignore) {}

            final boolean[] fontUiChanging = {false};
            fontFamilyGroup.setOnCheckedChangeListener((group, checked) -> {
                if (fontUiChanging[0]) return;
                int nextFamily = TextFontFamily.SANS;
                if (checked == R.id.text_style_font_family_serif) nextFamily = TextFontFamily.SERIF;
                else if (checked == R.id.text_style_font_family_mono) nextFamily = TextFontFamily.MONO;

                nextFamily = TextFontFamily.normalize(nextFamily);
                if (nextFamily == lastFontFamily[0]) return;

                prefs.setFontFamily(nextFamily);
                boolean ok = TextAnnotationStyleApplier.applyFontFamily(pageView, nextFamily);
                if (!ok) {
                    fontUiChanging[0] = true;
                    int priorId = R.id.text_style_font_family_sans;
                    if (lastFontFamily[0] == TextFontFamily.SERIF) priorId = R.id.text_style_font_family_serif;
                    else if (lastFontFamily[0] == TextFontFamily.MONO) priorId = R.id.text_style_font_family_mono;
                    try { fontFamilyGroup.check(priorId); } catch (Throwable ignore2) {}
                    fontUiChanging[0] = false;
                    Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                    return;
                }
                lastFontFamily[0] = nextFamily;
            });
        }

        // Font styles (bold/italic/underline/strike). Applies to both embedded FreeText and sidecar notes.
        if (fontStyleBold != null || fontStyleItalic != null || fontStyleUnderline != null || fontStyleStrike != null) {
            try {
                if (fontStyleBold != null) fontStyleBold.setChecked(TextStyleFlags.isBold(lastStyleFlags[0]));
                if (fontStyleItalic != null) fontStyleItalic.setChecked(TextStyleFlags.isItalic(lastStyleFlags[0]));
                if (fontStyleUnderline != null) fontStyleUnderline.setChecked(TextStyleFlags.isUnderline(lastStyleFlags[0]));
                if (fontStyleStrike != null) fontStyleStrike.setChecked(TextStyleFlags.isStrikethrough(lastStyleFlags[0]));
            } catch (Throwable ignore) {
            }

            final boolean[] styleUiChanging = {false};
            final Runnable applyStyleFlags = () -> {
                if (styleUiChanging[0]) return;
                int flags = 0;
                try {
                    if (fontStyleBold != null && fontStyleBold.isChecked()) flags |= TextStyleFlags.BOLD;
                    if (fontStyleItalic != null && fontStyleItalic.isChecked()) flags |= TextStyleFlags.ITALIC;
                    if (fontStyleUnderline != null && fontStyleUnderline.isChecked()) flags |= TextStyleFlags.UNDERLINE;
                    if (fontStyleStrike != null && fontStyleStrike.isChecked()) flags |= TextStyleFlags.STRIKETHROUGH;
                } catch (Throwable ignore) {
                }
                flags = TextStyleFlags.normalize(flags);
                if (flags == lastStyleFlags[0]) return;

                prefs.setFontStyleFlags(flags);
                boolean ok = TextAnnotationStyleApplier.applyStyleFlags(pageView, flags);
                if (!ok) {
                    styleUiChanging[0] = true;
                    try {
                        if (fontStyleBold != null) fontStyleBold.setChecked(TextStyleFlags.isBold(lastStyleFlags[0]));
                        if (fontStyleItalic != null) fontStyleItalic.setChecked(TextStyleFlags.isItalic(lastStyleFlags[0]));
                        if (fontStyleUnderline != null) fontStyleUnderline.setChecked(TextStyleFlags.isUnderline(lastStyleFlags[0]));
                        if (fontStyleStrike != null) fontStyleStrike.setChecked(TextStyleFlags.isStrikethrough(lastStyleFlags[0]));
                    } catch (Throwable ignore2) {
                    }
                    styleUiChanging[0] = false;
                    Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                    return;
                }
                lastStyleFlags[0] = flags;
            };

            final android.widget.CompoundButton.OnCheckedChangeListener styleListener = (buttonView, isChecked) -> applyStyleFlags.run();
            try {
                if (fontStyleBold != null) fontStyleBold.setOnCheckedChangeListener(styleListener);
                if (fontStyleItalic != null) fontStyleItalic.setOnCheckedChangeListener(styleListener);
                if (fontStyleUnderline != null) fontStyleUnderline.setOnCheckedChangeListener(styleListener);
                if (fontStyleStrike != null) fontStyleStrike.setOnCheckedChangeListener(styleListener);
            } catch (Throwable ignore) {
            }
        }

        if (rotationGroup != null) {
            int rot = lastRotationDegrees[0];
            if (rot < 0 || rot >= 360) {
                rot %= 360;
                if (rot < 0) rot += 360;
            }
            // Snap to 0/90/180/270 for UI.
            int snapped = ((rot + 45) / 90) * 90;
            if (snapped >= 360) snapped = 0;
            rot = snapped;
            lastRotationDegrees[0] = rot;

            int checkedId = R.id.text_style_rotate_0;
            if (rot == 90) checkedId = R.id.text_style_rotate_90;
            else if (rot == 180) checkedId = R.id.text_style_rotate_180;
            else if (rot == 270) checkedId = R.id.text_style_rotate_270;
            try { rotationGroup.check(checkedId); } catch (Throwable ignore) {}

            final boolean[] rotationUiChanging = {false};
            rotationGroup.setOnCheckedChangeListener((group, checked) -> {
                if (rotationUiChanging[0]) return;
                int nextRot = 0;
                if (checked == R.id.text_style_rotate_90) nextRot = 90;
                else if (checked == R.id.text_style_rotate_180) nextRot = 180;
                else if (checked == R.id.text_style_rotate_270) nextRot = 270;

                if (nextRot == lastRotationDegrees[0]) return;

                boolean ok = TextAnnotationStyleApplier.applyRotation(pageView, nextRot);
                if (!ok) {
                    rotationUiChanging[0] = true;
                    int priorId = R.id.text_style_rotate_0;
                    if (lastRotationDegrees[0] == 90) priorId = R.id.text_style_rotate_90;
                    else if (lastRotationDegrees[0] == 180) priorId = R.id.text_style_rotate_180;
                    else if (lastRotationDegrees[0] == 270) priorId = R.id.text_style_rotate_270;
                    try { rotationGroup.check(priorId); } catch (Throwable ignore2) {}
                    rotationUiChanging[0] = false;
                    Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                    return;
                }
                lastRotationDegrees[0] = nextRot;
            });
        }

        if (lockPositionSizeCheck != null) {
            try { lockPositionSizeCheck.setChecked(lastLockPositionSize[0]); } catch (Throwable ignore) {}
        }
        if (lockContentsCheck != null) {
            try { lockContentsCheck.setChecked(lastLockContents[0]); } catch (Throwable ignore) {}
        }
        refreshLockUi.run();

        final boolean[] lockUiChanging = {false};
        final Runnable applyLocks = () -> {
            if (lockUiChanging[0]) return;
            boolean nextLockPos = lockPositionSizeCheck != null && lockPositionSizeCheck.isChecked();
            boolean nextLockContents = lockContentsCheck != null && lockContentsCheck.isChecked();
            boolean ok = TextAnnotationStyleApplier.applyLocks(pageView, nextLockPos, nextLockContents);
            if (!ok) {
                lockUiChanging[0] = true;
                try {
                    if (lockPositionSizeCheck != null) lockPositionSizeCheck.setChecked(lastLockPositionSize[0]);
                    if (lockContentsCheck != null) lockContentsCheck.setChecked(lastLockContents[0]);
                } catch (Throwable ignore) {
                }
                lockUiChanging[0] = false;
                Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                refreshLockUi.run();
                return;
            }
            lastLockPositionSize[0] = nextLockPos;
            lastLockContents[0] = nextLockContents;
            refreshLockUi.run();
        };

        if (lockPositionSizeCheck != null) lockPositionSizeCheck.setOnCheckedChangeListener((buttonView, isChecked) -> applyLocks.run());
        if (lockContentsCheck != null) lockContentsCheck.setOnCheckedChangeListener((buttonView, isChecked) -> applyLocks.run());

        if (colorStrip != null && colorCount > 0) {
            TextAnnotationStyleSwatches.buildStrip(context, colorStrip, swatchViews, colorNames, swatchConfig,
                    selectedColorIndex[0], R.string.pen_color_dialog_swatch_description,
                    colorIndex -> {
                        if (selectedColorIndex[0] == colorIndex) return;
                        prefs.setColorIndex(colorIndex);
                        selectedColorIndex[0] = colorIndex;
                        updateColorDisplay(colorValueView, colorNames, colorIndex);
                        TextAnnotationStyleSwatches.refreshSwatches(swatchViews, selectedColorIndex[0], swatchConfig);
                        boolean ok = TextAnnotationStyleApplier.applyTextStyle(pageView, lastPersisted[0], colorIndex);
                        if (!ok) Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                    });
        }

        if (backgroundOpacitySeekBar != null) {
            backgroundOpacitySeekBar.setMax(100);
            backgroundOpacitySeekBar.setProgress(Math.max(0, Math.min(100, Math.round(lastBackgroundOpacity[0] * 100f))));
            backgroundOpacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float value = clamp(progress / 100f, 0.0f, 1.0f);
                    updateOpacityDisplay(backgroundOpacityValueView, value, context);
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float value = clamp(seekBar.getProgress() / 100f, 0.0f, 1.0f);
                    if (Math.abs(value - lastBackgroundOpacity[0]) < opacityEpsilon) return;
                    prefs.setBackgroundOpacity(value);
                    lastBackgroundOpacity[0] = value;
                    boolean ok = TextAnnotationStyleApplier.applyBackground(pageView, selectedBackgroundColorIndex[0], value);
                    if (!ok) Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                }
            });
        }

        if (backgroundColorStrip != null && colorCount > 0) {
            TextAnnotationStyleSwatches.buildStrip(context, backgroundColorStrip, backgroundSwatchViews, colorNames, swatchConfig,
                    selectedBackgroundColorIndex[0], R.string.pen_color_dialog_swatch_description,
                    colorIndex -> {
                        if (selectedBackgroundColorIndex[0] == colorIndex) return;
                        prefs.setBackgroundColorIndex(colorIndex);
                        selectedBackgroundColorIndex[0] = colorIndex;
                        updateColorDisplay(backgroundColorValueView, colorNames, colorIndex);
                        TextAnnotationStyleSwatches.refreshSwatches(backgroundSwatchViews, selectedBackgroundColorIndex[0], swatchConfig);
                        boolean ok = TextAnnotationStyleApplier.applyBackground(pageView, colorIndex, lastBackgroundOpacity[0]);
                        if (!ok) Toast.makeText(context, R.string.select_text_annot_to_style, Toast.LENGTH_LONG).show();
                    });
        }

        // Border controls (color/width/dash/rounding). Applied to both embedded FreeText and sidecar notes.
        if (borderWidthSeekBar != null) {
            final float borderMin = 0.0f;
            final float borderMax = 12.0f;
            final float borderStep = 0.5f;
            final int maxProgress = Math.round((borderMax - borderMin) / borderStep);
            borderWidthSeekBar.setMax(Math.max(1, maxProgress));
            float clamped = clamp(borderWidthPt[0], borderMin, borderMax);
            borderWidthSeekBar.setProgress(Math.max(0, Math.min(maxProgress, Math.round((clamped - borderMin) / borderStep))));
            updateSizeDisplay(borderWidthValueView, clamped, context);
            borderWidthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float value = clamp(borderMin + (progress * borderStep), borderMin, borderMax);
                    updateSizeDisplay(borderWidthValueView, value, context);
                    borderWidthPt[0] = value;
                    if (!seekBar.isEnabled()) return;
                    try { content.removeCallbacks(applyBorderDebounced); } catch (Throwable ignore) {}
                    try { content.postDelayed(applyBorderDebounced, borderApplyDebounceMs); } catch (Throwable ignore) {}
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float value = clamp(borderMin + (seekBar.getProgress() * borderStep), borderMin, borderMax);
                    try { content.removeCallbacks(applyBorderDebounced); } catch (Throwable ignore) {}
                    if (Math.abs(value - persistedBorderWidthPt[0]) < 0.001f) return;
                    prefs.setBorderWidthPt(value);
                    persistedBorderWidthPt[0] = value;
                    borderWidthPt[0] = value;
                    applyBorder.run();
                }
            });
        } else {
            updateSizeDisplay(borderWidthValueView, borderWidthPt[0], context);
        }

        if (borderRadiusSeekBar != null) {
            final float rMin = 0.0f;
            final float rMax = 24.0f;
            final float rStep = 1.0f;
            final int maxProgress = Math.round((rMax - rMin) / rStep);
            borderRadiusSeekBar.setMax(Math.max(1, maxProgress));
            float clamped = clamp(borderRadiusPt[0], rMin, rMax);
            borderRadiusSeekBar.setProgress(Math.max(0, Math.min(maxProgress, Math.round((clamped - rMin) / rStep))));
            updateSizeDisplay(borderRadiusValueView, clamped, context);
            borderRadiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float value = clamp(rMin + (progress * rStep), rMin, rMax);
                    updateSizeDisplay(borderRadiusValueView, value, context);
                    borderRadiusPt[0] = value;
                    if (!seekBar.isEnabled()) return;
                    try { content.removeCallbacks(applyBorderDebounced); } catch (Throwable ignore) {}
                    try { content.postDelayed(applyBorderDebounced, borderApplyDebounceMs); } catch (Throwable ignore) {}
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    float value = clamp(rMin + (seekBar.getProgress() * rStep), rMin, rMax);
                    try { content.removeCallbacks(applyBorderDebounced); } catch (Throwable ignore) {}
                    if (Math.abs(value - persistedBorderRadiusPt[0]) < 0.001f) return;
                    prefs.setBorderRadiusPt(value);
                    persistedBorderRadiusPt[0] = value;
                    borderRadiusPt[0] = value;
                    applyBorder.run();
                }
            });
        } else {
            updateSizeDisplay(borderRadiusValueView, borderRadiusPt[0], context);
        }

        if (borderStyleGroup != null) {
            int checkedId = lastBorderStyle[0] != 0 ? R.id.text_style_border_style_dashed : R.id.text_style_border_style_solid;
            try { borderStyleGroup.check(checkedId); } catch (Throwable ignore) {}
            borderStyleGroup.setOnCheckedChangeListener((group, checked) -> {
                int nextStyle = (checked == R.id.text_style_border_style_dashed) ? 1 : 0;
                if (nextStyle == lastBorderStyle[0]) return;
                prefs.setBorderStyle(nextStyle);
                lastBorderStyle[0] = nextStyle;
                applyBorder.run();
            });
        }

        if (borderColorStrip != null && colorCount > 0) {
            TextAnnotationStyleSwatches.buildStrip(context, borderColorStrip, borderSwatchViews, colorNames, swatchConfig,
                    selectedBorderColorIndex[0], R.string.text_border_color_dialog_swatch_description,
                    colorIndex -> {
                        if (selectedBorderColorIndex[0] == colorIndex) return;
                        prefs.setBorderColorIndex(colorIndex);
                        selectedBorderColorIndex[0] = colorIndex;
                        updateColorDisplay(borderColorValueView, colorNames, colorIndex);
                        TextAnnotationStyleSwatches.refreshSwatches(borderSwatchViews, selectedBorderColorIndex[0], swatchConfig);
                        applyBorder.run();
                    });
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.text_style_dialog_title);
        builder.setView(content);
        builder.show();
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private static void updateSizeDisplay(@Nullable TextView valueView, float value, Context context) {
        if (valueView != null) valueView.setText(context.getString(R.string.pen_size_dialog_value, value));
    }

    private static void updateLineHeightDisplay(@Nullable TextView valueView, float value, Context context) {
        if (valueView != null) valueView.setText(context.getString(R.string.text_line_spacing_value, value));
    }

    private static void updateColorDisplay(@Nullable TextView colorValueView, CharSequence[] colorNames, int index) {
        if (colorValueView != null && colorNames != null && index >= 0 && index < colorNames.length) colorValueView.setText(colorNames[index]);
    }

    private static void updateOpacityDisplay(@Nullable TextView valueView, float opacity01, Context context) {
        if (valueView == null) return;
        float clamped = clamp(opacity01, 0.0f, 1.0f);
        int pct = Math.round(clamped * 100f);
        valueView.setText(context.getString(R.string.opacity_percent_value, pct));
    }

    private static void setEnabledDeep(@Nullable View view, boolean enabled) {
        if (view == null) return;
        try {
            view.setEnabled(enabled);
            view.setAlpha(enabled ? 1.0f : 0.35f);
        } catch (Throwable ignore) {
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup vg = (ViewGroup) view;
        for (int i = 0; i < vg.getChildCount(); i++) setEnabledDeep(vg.getChildAt(i), enabled);
    }
}
