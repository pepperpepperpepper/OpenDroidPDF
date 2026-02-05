package org.opendroidpdf.app.document;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.lifecycle.ActivityComposition;
import org.opendroidpdf.app.preferences.PreferencesCoordinator;
import org.opendroidpdf.app.preferences.SharedPreferencesViewerPrefsStore;
import org.opendroidpdf.app.reader.ScrollMode;
import org.opendroidpdf.app.ui.ReadingModeController;

/** Bottom-sheet: Acrobat-like View settings (layout + reading + night mode). */
final class ViewSettingsUi {
    private ViewSettingsUi() {}

    static void show(@NonNull AppCompatActivity activity, @NonNull MuPDFReaderView docView) {
        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_view_settings_sheet, null);
        dialog.setContentView(root);

        final PreferencesCoordinator prefsCoordinator = resolvePreferencesCoordinator(activity);
        final SharedPreferences prefs = activity.getApplicationContext().getSharedPreferences(
                SettingsActivity.SHARED_PREFERENCES_STRING,
                Context.MODE_MULTI_PROCESS);

        bindScrollMode(root, prefs, prefsCoordinator, activity, docView);
        bindReadingMode(root, activity, docView);
        bindNightMode(root, prefs, prefsCoordinator, activity, docView);

        dialog.show();
    }

    private static void bindScrollMode(@NonNull View root,
                                       @NonNull SharedPreferences prefs,
                                       @Nullable PreferencesCoordinator prefsCoordinator,
                                       @NonNull AppCompatActivity activity,
                                       @NonNull MuPDFReaderView docView) {
        RadioGroup scrollGroup = root.findViewById(R.id.view_settings_scroll_mode_group);
        if (scrollGroup == null) return;

        String modePref = prefs.getString(SettingsActivity.PREF_READER_SCROLL_MODE, ScrollMode.CONTINUOUS.prefValue);
        ScrollMode current = ScrollMode.fromPrefValue(modePref);
        scrollGroup.check(current == ScrollMode.CONTINUOUS
                ? R.id.view_settings_scroll_mode_continuous
                : R.id.view_settings_scroll_mode_single_page);

        scrollGroup.setOnCheckedChangeListener((group, checkedId) -> {
            ScrollMode next = (checkedId == R.id.view_settings_scroll_mode_continuous) ? ScrollMode.CONTINUOUS : ScrollMode.PAGED;
            prefs.edit().putString(SettingsActivity.PREF_READER_SCROLL_MODE, next.prefValue).apply();
            applyViewerPrefs(prefsCoordinator, activity, docView);
        });
    }

    private static void bindReadingMode(@NonNull View root,
                                        @NonNull AppCompatActivity activity,
                                        @NonNull MuPDFReaderView docView) {
        SwitchCompat readingSwitch = root.findViewById(R.id.view_settings_switch_reading_mode);
        View readingRow = root.findViewById(R.id.view_settings_row_reading_mode);
        if (readingSwitch == null || readingRow == null) return;

        readingRow.setOnClickListener(v -> readingSwitch.toggle());
        boolean initial = ReadingModeController.isEnabled(activity);
        readingSwitch.setChecked(initial);
        readingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ReadingModeController.setEnabled(activity, isChecked);
            ReadingModeController.applyToDocumentView(activity, docView, isChecked);
            try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
        });
    }

    private static void bindNightMode(@NonNull View root,
                                      @NonNull SharedPreferences prefs,
                                      @Nullable PreferencesCoordinator prefsCoordinator,
                                      @NonNull AppCompatActivity activity,
                                      @NonNull MuPDFReaderView docView) {
        View nightRow = root.findViewById(R.id.view_settings_row_night_mode);
        SwitchCompat nightSwitch = root.findViewById(R.id.view_settings_switch_night_mode);
        if (nightRow == null || nightSwitch == null) return;

        DocumentType docType = resolveDocumentType(activity);
        boolean supported = docType != DocumentType.EPUB;
        nightRow.setVisibility(supported ? View.VISIBLE : View.GONE);
        if (!supported) return;

        nightRow.setOnClickListener(v -> nightSwitch.toggle());
        boolean initial = prefs.getBoolean(SettingsActivity.PREF_NIGHT_MODE, false);
        nightSwitch.setChecked(initial);
        nightSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(SettingsActivity.PREF_NIGHT_MODE, isChecked).apply();
            applyViewerPrefs(prefsCoordinator, activity, docView);
        });
    }

    private static void applyViewerPrefs(@Nullable PreferencesCoordinator prefsCoordinator,
                                         @NonNull AppCompatActivity activity,
                                         @NonNull MuPDFReaderView docView) {
        if (prefsCoordinator != null) {
            try { prefsCoordinator.refreshAndApply(); } catch (Throwable ignore) {}
        } else {
            try {
                docView.applyViewerPrefs(new SharedPreferencesViewerPrefsStore(activity).load());
            } catch (Throwable ignore) {
            }
        }
        try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
    }

    @Nullable
    private static PreferencesCoordinator resolvePreferencesCoordinator(@NonNull AppCompatActivity activity) {
        try {
            if (activity instanceof OpenDroidPDFActivity) {
                ActivityComposition.Composition comp = ((OpenDroidPDFActivity) activity).getComposition();
                return comp != null ? comp.preferencesCoordinator : null;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    @NonNull
    private static DocumentType resolveDocumentType(@NonNull AppCompatActivity activity) {
        try {
            if (activity instanceof OpenDroidPDFActivity) {
                OpenDroidPDFCore core = ((OpenDroidPDFActivity) activity).getCore();
                return core != null ? DocumentType.fromFileFormat(core.fileFormat()) : DocumentType.OTHER;
            }
        } catch (Throwable ignore) {
        }
        return DocumentType.OTHER;
    }
}

