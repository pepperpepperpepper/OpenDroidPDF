package org.opendroidpdf;

import android.app.Activity;
import android.util.TypedValue;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.Preference;
import android.preference.ListPreference;
import android.widget.ListView;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;


public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

            //This fixes onSharedPreferencesChanged
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(SettingsActivity.SHARED_PREFERENCES_STRING);
        preferenceManager.setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
        
            // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        ListPreference prefInkColor = (ListPreference) findPreference(SettingsActivity.PREF_INK_COLOR);
        ListPreference prefHighlightColor = (ListPreference) findPreference(SettingsActivity.PREF_HIGHLIGHT_COLOR);
        ListPreference prefUnderlineColor = (ListPreference) findPreference(SettingsActivity.PREF_UNDERLINE_COLOR);
        ListPreference prefStrikeOutColor = (ListPreference) findPreference(SettingsActivity.PREF_STRIKEOUT_COLOR);
        ListPreference prefTextAnnotIconColor = (ListPreference) findPreference(SettingsActivity.PREF_TEXTANNOTICON_COLOR);    
        
        CharSequence[] colorNames = getResources().getTextArray(R.array.pen_color_names);
        CharSequence[] colorValues = ColorPalette.getColorNumbers();

        prefInkColor.setEntries(colorNames);
        prefInkColor.setEntryValues(colorValues);
        prefHighlightColor.setEntries(colorNames);
        prefHighlightColor.setEntryValues(colorValues);
        prefUnderlineColor.setEntries(colorNames);
        prefUnderlineColor.setEntryValues(colorValues);
        prefStrikeOutColor.setEntries(colorNames);
        prefStrikeOutColor.setEntryValues(colorValues);
        prefTextAnnotIconColor.setEntries(colorNames);
        prefTextAnnotIconColor.setEntryValues(colorValues);

        configureAboutPreferences();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if(view != null) {
            ListView listView = (ListView) view.findViewById(android.R.id.list);
            if(listView != null){
                TypedValue tv = new TypedValue();
                if(getActivity().getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true)) {
                    int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
                    listView.setPadding(0, actionBarHeight, 0, 0);
                    listView.setClipToPadding(false);   
                }
            }
        }
        return view;
    }

    private void configureAboutPreferences() {
        Preference versionPref = findPreference(SettingsActivity.PREF_ABOUT_VERSION);
        if (versionPref != null) {
            String summary = getString(R.string.about_version_summary, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
            versionPref.setSummary(summary);
        }

        Preference licensePref = findPreference(SettingsActivity.PREF_ABOUT_LICENSE);
        if (licensePref != null) {
            licensePref.setOnPreferenceClickListener(preference -> {
                showLicenseDialog();
                return true;
            });
        }

        Preference sourcePref = findPreference(SettingsActivity.PREF_ABOUT_SOURCE);
        if (sourcePref != null) {
            sourcePref.setOnPreferenceClickListener(preference -> {
                openExternalUrl(R.string.about_source_url);
                return true;
            });
        }

        Preference issuesPref = findPreference(SettingsActivity.PREF_ABOUT_ISSUES);
        if (issuesPref != null) {
            issuesPref.setOnPreferenceClickListener(preference -> {
                openExternalUrl(R.string.about_issues_url);
                return true;
            });
        }
    }

    private void showLicenseDialog() {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle(R.string.about_license_dialog_title)
            .setMessage(R.string.about_license_dialog_body)
            .setPositiveButton(R.string.about_dialog_positive, null)
            .setNegativeButton(R.string.about_license_view_full, (dialog, which) -> openExternalUrl(R.string.about_license_url))
            .show();
    }

    private void openExternalUrl(int urlResId) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        String url = getString(urlResId);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(activity, R.string.about_no_browser, Toast.LENGTH_SHORT).show();
        }
    }
}
