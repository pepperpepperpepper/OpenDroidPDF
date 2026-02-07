package org.opendroidpdf;

import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.activity.OnBackPressedCallback;

import org.opendroidpdf.app.preferences.PreferencesNames;
import org.opendroidpdf.app.preferences.PreferencesNamespaceMigrator;
import org.opendroidpdf.app.preferences.PreferencesTypeMigrator;

public class SettingsActivity extends androidx.appcompat.app.AppCompatActivity {
    public static final String PREF_USE_STYLUS = "pref_use_stylus";
    public static final String PREF_SCROLL_VERTICAL = "pref_scroll_vertical";
    public static final String PREF_SCROLL_CONTINUOUS = "pref_scroll_continuous";
    public static final String PREF_FIT_WIDTH = "pref_fit_width";
    public static final String PREF_READER_SCROLL_MODE = "pref_reader_scroll_mode";
    public static final String PREF_PAGE_PAGING_AXIS = "pref_page_paging_axis";
    public static final String PREF_READING_MODE = "pref_reading_mode";
    public static final String PREF_NIGHT_MODE = "pref_night_mode";
    public static final String PREF_INK_THICKNESS = "pref_ink_thickness";
    public static final String PREF_ERASER_THICKNESS = "pref_eraser_thickness";
    public static final String PREF_INK_COLOR = "pref_ink_color";
    public static final String PREF_HIGHLIGHT_COLOR = "pref_highlight_color";
    public static final String PREF_UNDERLINE_COLOR = "pref_underline_color";
    public static final String PREF_STRIKEOUT_COLOR = "pref_strikeout_color";
    public static final String PREF_TEXTANNOTICON_COLOR = "pref_textannoticon_color";
    public static final String PREF_ABOUT_VERSION = "pref_about_version";
    public static final String PREF_ABOUT_LICENSE = "pref_about_license";
    public static final String PREF_ABOUT_SOURCE = "pref_about_source";
    public static final String PREF_ABOUT_ISSUES = "pref_about_issues";
    
    public static final String PREF_NUMBER_RECENT_FILES = "pref_number_recent_files";
    
    public static final String PREF_SAVE_ON_DESTROY = "pref_save_on_destroy";
    public static final String PREF_SAVE_ON_STOP = "pref_save_on_stop";
    public static final String PREF_SMART_TEXT_SELECTION = "pref_smart_text_selection";
    public static final String PREF_KEEP_SCREEN_ON = "keep_screen_on";

    public static final String PREF_EXPERIMENTAL_MODE = "experimental_mode";

    // Assistant (LLM/voice) settings
    public static final String PREF_ASSISTANT_ENABLED = "pref_assistant_enabled";
    public static final String PREF_ASSISTANT_PROVIDER = "pref_assistant_provider";
    public static final String PREF_ASSISTANT_PROVIDERS = "pref_assistant_providers";
    public static final String PREF_ASSISTANT_REQUIRE_PREVIEW = "pref_assistant_require_preview";
    public static final String PREF_ASSISTANT_ALLOW_WHOLE_DOCUMENT = "pref_assistant_allow_whole_document";
    public static final String PREF_ASSISTANT_WIFI_ONLY = "pref_assistant_wifi_only";
    public static final String PREF_TTS_RATE = "pref_tts_rate";
    public static final String PREF_ASSISTANT_CARTESIA_API_KEY = "pref_assistant_cartesia_api_key";
    public static final String PREF_ASSISTANT_VOICE_ASSISTANT = "pref_assistant_voice_assistant";

    // In-app hints (not exposed in Settings UI).
    public static final String PREF_SEEN_PAGE_INDICATOR_NAV_HINT = "pref_seen_page_indicator_nav_hint";
    public static final String PREF_SEEN_IMPORTED_WORD_BANNER = "pref_seen_imported_word_banner";
	
    // Backwards-compatible alias used by legacy call sites; prefer PreferencesNames.CURRENT.
    public final static String SHARED_PREFERENCES_STRING = PreferencesNames.CURRENT;
    private final static String TAG = "SettingsActivity";
    private OnBackPressedCallback backPressedCallback;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferencesNamespaceMigrator.ensureMigrated(this);
        PreferencesTypeMigrator.ensureMigrated(this);

        setContentView(R.layout.settings);
        Toolbar myToolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
		if(actionBar != null){
			actionBar.setTitle(R.string.settings_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
		}
        
        // Add the fragment to the layout
        // if the savedInstanceState != null this is apprantly not necessary...
        if(savedInstanceState == null)
        {
            getFragmentManager().beginTransaction()
                .add(R.id.sub_layout, new SettingsFragment())
                .commit();
        }

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setEnabled(false);
                finish();
                overridePendingTransition(R.anim.fade_in, R.anim.exit_to_left);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.exit_to_left);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
