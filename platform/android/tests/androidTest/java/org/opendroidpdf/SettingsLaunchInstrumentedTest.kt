package org.opendroidpdf

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.opendroidpdf.app.preferences.PreferencesNames
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsLaunchInstrumentedTest {

    @Test
    fun tappingSettingsFromDashboardOpensSettingsScreen() {
        ActivityScenario.launch(OpenDroidPDFActivity::class.java).use {
            onView(withId(R.id.menu_settings)).check(matches(isDisplayed()))
            onView(withId(R.id.menu_settings)).perform(click())
            onView(withText(R.string.autosave_settings)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun settingsOpensWhenLegacyPenPrefsStoredAsNumericTypes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS)
        prefs.edit()
            .putFloat(SettingsActivity.PREF_INK_THICKNESS, 2.5f)
            .putInt(SettingsActivity.PREF_INK_COLOR, 3)
            .commit()

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withText(R.string.autosave_settings)).check(matches(isDisplayed()))
        }
    }
}
