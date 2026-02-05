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
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.app.preferences.PreferencesNames;
import org.opendroidpdf.app.assistant.AssistantLlmProviderConfig;
import org.opendroidpdf.app.assistant.AssistantLlmProvidersStore;
import org.opendroidpdf.app.assistant.AssistantSecrets;
import org.opendroidpdf.app.assistant.AssistantActivity;
import org.opendroidpdf.app.assistant.AssistantProvidersActivity;


public class SettingsFragment extends PreferenceFragment {
    private Preference assistantProvidersPref;
    private Preference cartesiaKeyPref;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

            //This fixes onSharedPreferencesChanged
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(PreferencesNames.CURRENT);
        preferenceManager.setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
        
            // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        configureAboutPreferences();
        configureViewerPreferences();
        configureAssistantPreferences();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (assistantProvidersPref != null) refreshAssistantProvidersSummary(assistantProvidersPref);
        if (cartesiaKeyPref != null) refreshCartesiaKeySummary(cartesiaKeyPref);
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

    private void configureAssistantPreferences() {
        cartesiaKeyPref = findPreference(SettingsActivity.PREF_ASSISTANT_CARTESIA_API_KEY);
        if (cartesiaKeyPref != null) {
            refreshCartesiaKeySummary(cartesiaKeyPref);
            cartesiaKeyPref.setOnPreferenceClickListener(preference -> {
                showCartesiaApiKeyDialog(preference);
                return true;
            });
        }

        assistantProvidersPref = findPreference(SettingsActivity.PREF_ASSISTANT_PROVIDERS);
        if (assistantProvidersPref != null) {
            refreshAssistantProvidersSummary(assistantProvidersPref);
            assistantProvidersPref.setOnPreferenceClickListener(preference -> {
                Activity activity = getActivity();
                if (activity == null) return true;
                startActivity(new Intent(activity, AssistantProvidersActivity.class));
                return true;
            });
        }

        Preference voiceAssistantPref = findPreference(SettingsActivity.PREF_ASSISTANT_VOICE_ASSISTANT);
        if (voiceAssistantPref != null) {
            voiceAssistantPref.setOnPreferenceClickListener(preference -> {
                Activity activity = getActivity();
                if (activity == null) return true;
                startActivity(new Intent(activity, AssistantActivity.class));
                return true;
            });
        }
    }

    private void refreshAssistantProvidersSummary(Preference pref) {
        Activity activity = getActivity();
        if (activity == null) return;

        AssistantLlmProviderConfig cfg = AssistantLlmProvidersStore.defaultProviderOrNull(activity);
        if (cfg == null) {
            pref.setSummary(R.string.assistant_providers_summary_unset);
            return;
        }
        pref.setSummary(getString(R.string.assistant_providers_summary_set, cfg.name()));
    }

    private void configureViewerPreferences() {
        final ListPreference scrollMode = (ListPreference) findPreference(SettingsActivity.PREF_READER_SCROLL_MODE);
        final ListPreference pagingAxis = (ListPreference) findPreference(SettingsActivity.PREF_PAGE_PAGING_AXIS);
        if (scrollMode == null || pagingAxis == null) return;

        final Runnable refreshEnabledState = () -> {
            String value = scrollMode.getValue();
            boolean isPaged = "paged".equals(value);
            pagingAxis.setEnabled(isPaged);
        };
        refreshEnabledState.run();

        scrollMode.setOnPreferenceChangeListener((pref, newValue) -> {
            boolean isPaged = "paged".equals(String.valueOf(newValue));
            pagingAxis.setEnabled(isPaged);
            return true;
        });
    }

    private void refreshCartesiaKeySummary(Preference pref) {
        Activity activity = getActivity();
        if (activity == null) return;

        String last4 = AssistantSecrets.cartesiaApiKeyLast4OrNull(activity);
        if (last4 == null) {
            pref.setSummary(R.string.assistant_cartesia_api_key_summary_unset);
            return;
        }
        pref.setSummary(getString(R.string.assistant_cartesia_api_key_summary_set, last4));
    }

    private void showCartesiaApiKeyDialog(Preference pref) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) return;

        final EditText input = new EditText(activity);
        input.setHint(R.string.assistant_cartesia_api_key_dialog_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTransformationMethod(PasswordTransformationMethod.getInstance());

        new AlertDialog.Builder(activity)
                .setTitle(R.string.assistant_cartesia_api_key_dialog_title)
                .setMessage(R.string.assistant_cartesia_api_key_dialog_message)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    try {
                        AssistantSecrets.setCartesiaApiKey(activity, input.getText().toString());
                        refreshCartesiaKeySummary(pref);
                        Toast.makeText(activity, R.string.assistant_saved, Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        Toast.makeText(activity, R.string.assistant_save_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton(R.string.assistant_clear, (dialog, which) -> {
                    AssistantSecrets.clearCartesiaApiKey(activity);
                    refreshCartesiaKeySummary(pref);
                    Toast.makeText(activity, R.string.assistant_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
