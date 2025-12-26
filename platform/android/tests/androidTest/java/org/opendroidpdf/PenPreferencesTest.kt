package org.opendroidpdf

import android.content.Context
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opendroidpdf.app.preferences.PenPreferencesServiceImpl
import org.opendroidpdf.app.preferences.PreferencesNames
import org.opendroidpdf.app.preferences.SharedPreferencesPenPrefsStore

@RunWith(AndroidJUnit4::class)
class PenPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS)
            .edit()
            .clear()
            .commit()
    }

    private fun resFloat(resId: Int): Float {
        val tv = TypedValue()
        context.resources.getValue(resId, tv, true)
        return tv.float
    }

    private fun newService(): PenPreferencesServiceImpl {
        val prefs = context.getSharedPreferences(PreferencesNames.CURRENT, Context.MODE_MULTI_PROCESS)
        val store = SharedPreferencesPenPrefsStore(
            prefs,
            resFloat(R.dimen.pen_size_min),
            resFloat(R.dimen.pen_size_max),
            resFloat(R.dimen.pen_size_step),
            resFloat(R.dimen.ink_thickness_default),
        )
        return PenPreferencesServiceImpl(store)
    }

    @Test
    fun defaultThicknessRespectsBounds() {
        val service = newService()
        val snap = service.get()

        assertTrue(snap.defaultThickness >= snap.minThickness)
        assertTrue(snap.defaultThickness <= snap.maxThickness)
        assertEquals(snap.defaultThickness, snap.thickness, 0.001f)
    }

    @Test
    fun setterPersistsThicknessAndColor() {
        val service = newService()

        service.setThickness(3.25f)
        service.setColorIndex(5)

        val snap = service.get()
        assertEquals(3.25f, snap.thickness, 0.001f)
        assertEquals(5, snap.colorIndex)
    }
}
