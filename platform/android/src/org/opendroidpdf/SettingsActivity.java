package org.opendroidpdf;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.activity.OnBackPressedCallback;

import org.opendroidpdf.app.preferences.PreferencesNames;
import org.opendroidpdf.app.preferences.PreferencesNamespaceMigrator;

public class SettingsActivity extends androidx.appcompat.app.AppCompatActivity {
    public static final String PREF_USE_STYLUS = "pref_use_stylus";
    public static final String PREF_SCROLL_VERTICAL = "pref_scroll_vertical";
    public static final String PREF_SCROLL_CONTINUOUS = "pref_scroll_continuous";
    public static final String PREF_FIT_WIDTH = "pref_fit_width";
    public static final String PREF_PAGE_PAGING_AXIS = "pref_page_paging_axis";
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
	
    // Backwards-compatible alias used by legacy call sites; prefer PreferencesNames.CURRENT.
    public final static String SHARED_PREFERENCES_STRING = PreferencesNames.CURRENT;
    private final static String TAG = "SettingsActivity";
    private OnBackPressedCallback backPressedCallback;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferencesNamespaceMigrator.ensureMigrated(this);

        setContentView(R.layout.settings);
        Toolbar myToolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        String title = getString(R.string.app_name);
        androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();
		if(actionBar != null){
			actionBar.setTitle(title);
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
                overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }
}
