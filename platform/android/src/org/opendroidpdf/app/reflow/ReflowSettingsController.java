package org.opendroidpdf.app.reflow;

import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.app.document.DocumentIds;
import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.document.DocumentViewDelegate;
import org.opendroidpdf.app.document.ViewportHelper;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;

/** Per-document reflow settings UI + application (EPUB/HTML). */
public final class ReflowSettingsController {
    private static final float FONT_MIN_DP = 8f;
    private static final float FONT_MAX_DP = 40f;
    private static final float FONT_STEP_DP = 1f;

    private static final float MARGIN_MIN = 0f;
    private static final float MARGIN_MAX = 3f;
    private static final float MARGIN_STEP = 0.1f;

    private static final float LINE_MIN = 0.8f;
    private static final float LINE_MAX = 1.8f;
    private static final float LINE_STEP = 0.05f;

    private final OpenDroidPDFActivity activity;
    private final ReflowPrefsStore store;
    private final DocumentViewDelegate documentViewDelegate;

    public ReflowSettingsController(@NonNull OpenDroidPDFActivity activity,
                                   @NonNull ReflowPrefsStore store,
                                   @Nullable DocumentViewDelegate documentViewDelegate) {
        this.activity = activity;
        this.store = store;
        this.documentViewDelegate = documentViewDelegate;
    }

    public void showForCurrentDocument() {
        OpenDroidPDFCore core = activity.getCore();
        if (core == null || core.getUri() == null) return;
        if (DocumentType.fromFileFormat(core.fileFormat()) != DocumentType.EPUB) return;

        String docId = DocumentIds.fromUri(core.getUri());
        ReflowPrefsSnapshot initial = store.load(docId);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_reflow_settings, null);
        SeekBar fontSeek = view.findViewById(R.id.reflow_seek_font_size);
        TextView fontValue = view.findViewById(R.id.reflow_value_font_size);

        SeekBar marginSeek = view.findViewById(R.id.reflow_seek_margins);
        TextView marginValue = view.findViewById(R.id.reflow_value_margins);

        SeekBar lineSeek = view.findViewById(R.id.reflow_seek_line_spacing);
        TextView lineValue = view.findViewById(R.id.reflow_value_line_spacing);

        RadioGroup themeGroup = view.findViewById(R.id.reflow_radio_theme);

        int fontMaxProgress = Math.round((FONT_MAX_DP - FONT_MIN_DP) / FONT_STEP_DP);
        fontSeek.setMax(Math.max(0, fontMaxProgress));
        fontSeek.setProgress(toProgress(initial.fontDp, FONT_MIN_DP, FONT_STEP_DP, fontMaxProgress));
        setText(fontValue, formatFloat(fromProgress(fontSeek.getProgress(), FONT_MIN_DP, FONT_STEP_DP), 0));
        fontSeek.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setText(fontValue, formatFloat(fromProgress(progress, FONT_MIN_DP, FONT_STEP_DP), 0));
            }
        });

        int marginMaxProgress = Math.round((MARGIN_MAX - MARGIN_MIN) / MARGIN_STEP);
        marginSeek.setMax(Math.max(0, marginMaxProgress));
        marginSeek.setProgress(toProgress(initial.marginScale, MARGIN_MIN, MARGIN_STEP, marginMaxProgress));
        setText(marginValue, formatFloat(fromProgress(marginSeek.getProgress(), MARGIN_MIN, MARGIN_STEP), 1));
        marginSeek.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setText(marginValue, formatFloat(fromProgress(progress, MARGIN_MIN, MARGIN_STEP), 1));
            }
        });

        int lineMaxProgress = Math.round((LINE_MAX - LINE_MIN) / LINE_STEP);
        lineSeek.setMax(Math.max(0, lineMaxProgress));
        lineSeek.setProgress(toProgress(initial.lineSpacing, LINE_MIN, LINE_STEP, lineMaxProgress));
        setText(lineValue, formatFloat(fromProgress(lineSeek.getProgress(), LINE_MIN, LINE_STEP), 2));
        lineSeek.setOnSeekBarChangeListener(new SimpleSeekBarListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setText(lineValue, formatFloat(fromProgress(progress, LINE_MIN, LINE_STEP), 2));
            }
        });

        switch (initial.theme) {
            case DARK:
                themeGroup.check(R.id.reflow_theme_dark);
                break;
            case SEPIA:
                themeGroup.check(R.id.reflow_theme_sepia);
                break;
            case LIGHT:
            default:
                themeGroup.check(R.id.reflow_theme_light);
                break;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.reflow_settings_title);
        builder.setView(view);
        builder.setNegativeButton(R.string.menu_cancel, (d, w) -> {});
        builder.setPositiveButton(R.string.reflow_apply, (d, w) -> {
            ReflowPrefsSnapshot updated = new ReflowPrefsSnapshot(
                    clamp(fromProgress(fontSeek.getProgress(), FONT_MIN_DP, FONT_STEP_DP), FONT_MIN_DP, FONT_MAX_DP),
                    clamp(fromProgress(marginSeek.getProgress(), MARGIN_MIN, MARGIN_STEP), MARGIN_MIN, MARGIN_MAX),
                    clamp(fromProgress(lineSeek.getProgress(), LINE_MIN, LINE_STEP), LINE_MIN, LINE_MAX),
                    themeFromSelection(themeGroup.getCheckedRadioButtonId()));

            final boolean layoutChanged = layoutAffectingChanged(initial, updated);
            final SidecarAnnotationSession sidecar = currentSidecarSessionOrNull();
            if (layoutChanged && sidecar != null && sidecar.hasAnyInk()) {
                // Ink is geometry-anchored; changing layout will drift strokes. Keep layout locked
                // but still allow paint-only theme changes.
                ReflowPrefsSnapshot themeOnly = new ReflowPrefsSnapshot(
                        initial.fontDp,
                        initial.marginScale,
                        initial.lineSpacing,
                        updated.theme);
                store.save(docId, themeOnly);
                applyThemeOnly(themeOnly);
                showInkLayoutLockedDialog();
                return;
            }

            store.save(docId, updated);
            if (layoutChanged) {
                applyWithRelayout(updated);
                maybePromptLayoutMismatchAfterRelayout();
            } else {
                applyThemeOnly(updated);
            }
        });
        builder.show();
    }

    /**
     * Switches the document back to the layout snapshot under which annotations were created.
     * Used when the user changes layout and existing sidecar annotations become hidden.
     */
    public boolean applyAnnotatedLayoutForCurrentDocument() {
        OpenDroidPDFCore core = activity.getCore();
        if (core == null || core.getUri() == null) return false;
        if (DocumentType.fromFileFormat(core.fileFormat()) != DocumentType.EPUB) return false;

        String docId = DocumentIds.fromUri(core.getUri());
        ReflowAnnotatedLayout annotated = store.loadAnnotatedLayoutOrNull(docId);
        if (annotated == null) return false;

        // Preserve current theme (paint-only), but restore layout-affecting params.
        ReflowPrefsSnapshot current = store.load(docId);
        ReflowPrefsSnapshot combined = new ReflowPrefsSnapshot(
                annotated.prefs.fontDp,
                annotated.prefs.marginScale,
                annotated.prefs.lineSpacing,
                current.theme);

        store.save(docId, combined);
        applyWithRelayout(combined, annotated.pageWidthPt, annotated.pageHeightPt);
        return true;
    }

    private void applyThemeOnly(@NonNull ReflowPrefsSnapshot prefs) {
        OpenDroidPDFCore core = activity.getCore();
        if (core == null) return;

        float em = prefs.fontDp * 72f / 160f;
        String css = ReflowCss.compose(prefs, em);
        core.setUserCss(css);

        MuPDFReaderView docView = activity.getDocView();
        if (docView == null) return;
        int cur = docView.getSelectedItemPosition();
        for (int page = cur - 1; page <= cur + 1; page++) {
            View v = docView.getView(page);
            if (v instanceof org.opendroidpdf.MuPDFView) {
                ((org.opendroidpdf.MuPDFView) v).redraw(true);
            }
        }
    }

    private void applyWithRelayout(@NonNull ReflowPrefsSnapshot prefs) {
        applyWithRelayout(prefs, -1f, -1f);
    }

    private void applyWithRelayout(@NonNull ReflowPrefsSnapshot prefs,
                                   float pageWOverridePt,
                                   float pageHOverridePt) {
        OpenDroidPDFCore core = activity.getCore();
        if (core == null) return;

        float em = prefs.fontDp * 72f / 160f;
        String css = ReflowCss.compose(prefs, em);
        core.setUserCss(css);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        float densityDpi = dm != null && dm.densityDpi > 0 ? dm.densityDpi : 160f;

        MuPDFReaderView docView = activity.getDocView();
        float pageW;
        float pageH;
        if (pageWOverridePt > 0f && pageHOverridePt > 0f) {
            pageW = pageWOverridePt;
            pageH = pageHOverridePt;
        } else {
            int widthPx = dm != null ? dm.widthPixels : 0;
            int heightPx = dm != null ? dm.heightPixels : 0;
            if (docView != null && docView.getWidth() > 0 && docView.getHeight() > 0) {
                widthPx = Math.max(1, docView.getWidth() - docView.getPaddingLeft() - docView.getPaddingRight());
                heightPx = Math.max(1, docView.getHeight() - docView.getPaddingTop() - docView.getPaddingBottom());
            }

            pageW = widthPx * 72f / densityDpi;
            pageH = heightPx * 72f / densityDpi;
        }

        org.opendroidpdf.app.services.recent.ViewportSnapshot snap = ViewportHelper.snapshot(docView);
        float progress01 = ViewportHelper.computeDocProgress01(docView, snap);
        if (snap != null && progress01 >= 0f) {
            snap = snap.withDocProgress01(progress01);
        }
        boolean ok = core.layoutDocument(pageW, pageH, em);
        if (!ok) {
            activity.showInfo(activity.getString(R.string.cannot_open_document));
        }
        if (org.opendroidpdf.BuildConfig.DEBUG) {
            try {
                android.graphics.PointF sz0 = core.getPageSize(0);
                String layoutId = ReflowLayoutProfileId.from(prefs, sz0, em);
                android.util.Log.i(
                        "ReflowSettingsController",
                        "layoutDocument ok=" + ok +
                                " requested=" + pageW + "x" + pageH +
                                " page0=" + (sz0 != null ? (sz0.x + "x" + sz0.y) : "null") +
                                " em=" + em +
                                " layoutId=" + layoutId +
                                " override=" + (pageWOverridePt > 0f && pageHOverridePt > 0f));
            } catch (Throwable ignore) {
            }
        }

        // Pagination may have changed; recreate the adapter so page sizes/count update, then restore viewport.
        if (documentViewDelegate != null) {
            documentViewDelegate.recreateAdapterPreservingViewport(snap);
        } else if (docView != null && snap != null) {
            ViewportHelper.applySnapshot(docView, snap);
        }
    }

    private void showInkLayoutLockedDialog() {
        AlertDialog alert = new AlertDialog.Builder(activity)
                .setTitle(R.string.reflow_layout_locked_title)
                .setMessage(R.string.reflow_layout_locked_message)
                .setPositiveButton(R.string.dismiss, (d, w) -> {})
                .create();
        alert.show();
    }

    @Nullable
    private SidecarAnnotationSession currentSidecarSessionOrNull() {
        SidecarAnnotationProvider provider = activity.currentSidecarAnnotationProviderOrNull();
        return (provider instanceof SidecarAnnotationSession) ? (SidecarAnnotationSession) provider : null;
    }

    private void maybePromptLayoutMismatchAfterRelayout() {
        org.opendroidpdf.app.ui.UiStateDelegate ui = activity.getUiStateDelegate();
        SidecarAnnotationSession session = currentSidecarSessionOrNull();
        if (session == null || !session.hasAnnotationsInOtherLayouts()) {
            if (ui != null) ui.dismissReflowLayoutMismatchBanner();
            return;
        }

        ReflowAnnotatedLayout annotated = store.loadAnnotatedLayoutOrNull(session.docId());
        if (annotated == null) {
            activity.showInfo(activity.getString(R.string.reflow_annotations_hidden));
            return;
        }

        if (ui != null) {
            int message = session.hasAnyAnnotationsInCurrentLayout()
                    ? R.string.reflow_layout_mismatch_message
                    : R.string.reflow_annotations_hidden;
            ui.showReflowLayoutMismatchBanner(message, this::applyAnnotatedLayoutForCurrentDocument);
        } else {
            activity.showInfo(activity.getString(R.string.reflow_annotations_hidden));
        }
    }

    private static boolean layoutAffectingChanged(@NonNull ReflowPrefsSnapshot a, @NonNull ReflowPrefsSnapshot b) {
        return Math.abs(a.fontDp - b.fontDp) > 0.001f
                || Math.abs(a.marginScale - b.marginScale) > 0.001f
                || Math.abs(a.lineSpacing - b.lineSpacing) > 0.001f;
    }

    @NonNull
    private static ReflowTheme themeFromSelection(int checkedId) {
        if (checkedId == R.id.reflow_theme_dark) return ReflowTheme.DARK;
        if (checkedId == R.id.reflow_theme_sepia) return ReflowTheme.SEPIA;
        return ReflowTheme.LIGHT;
    }

    private static int toProgress(float value, float min, float step, int max) {
        int p = Math.round((value - min) / step);
        return Math.max(0, Math.min(max, p));
    }

    private static float fromProgress(int progress, float min, float step) {
        return min + (progress * step);
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static void setText(@Nullable TextView tv, @NonNull String text) {
        if (tv != null) tv.setText(text);
    }

    private static String formatFloat(float v, int decimals) {
        if (decimals <= 0) return String.valueOf(Math.round(v));
        return String.format(java.util.Locale.US, "%." + decimals + "f", v);
    }

    private abstract static class SimpleSeekBarListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
