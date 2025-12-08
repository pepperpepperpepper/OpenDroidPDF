package org.opendroidpdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opendroidpdf.app.preferences.PenPreferences

@RunWith(AndroidJUnit4::class)
class PenPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaultThicknessRespectsBounds() {
        val prefs = PenPreferences(context)
        val defaultThickness = prefs.defaultThickness

        assertTrue(defaultThickness >= prefs.minThickness)
        assertTrue(defaultThickness <= prefs.maxThickness)
        assertEquals(defaultThickness, prefs.thickness, 0.001f)
    }

    @Test
    fun setterPersistsThicknessAndColor() {
        val prefs = PenPreferences(context)

        prefs.setThickness(3.25f)
        prefs.setColorIndex(5)

        assertEquals(3.25f, prefs.thickness, 0.001f)
        assertEquals(5, prefs.colorIndex)
    }
}
