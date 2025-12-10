package org.opendroidpdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.opendroidpdf.app.reader.ReaderGeometry
import org.opendroidpdf.app.reader.ZoomController

@RunWith(AndroidJUnit4::class)
class ZoomControllerHelperInstrumentedTest {

    @Test
    fun computeSnapFitWidthScale_behavesNearAndFar() {
        val cw = 1080
        val ch = 1920
        val padL = 0
        val padR = 0
        val padT = 0
        val padB = 0
        val viewMeasuredW = 1000
        val viewMeasuredH = 2400
        val minScale = 0.5f
        val maxScale = 5.0f

        val fill = ReaderGeometry.fillScreenScale(
            cw, ch, padL, padR, padT, padB, viewMeasuredW, viewMeasuredH
        )
        val target = cw.toFloat() / (viewMeasuredW * fill)

        // Near the target → should snap (non-null) and equal target within small epsilon
        val near = ZoomController.computeSnapFitWidthScale(
            true, /*fitWidth*/ false, /*reflow*/ target * 0.93f,
            cw, ch, padL, padR, padT, padB, viewMeasuredW, viewMeasuredH, minScale, maxScale
        )
        assertNotNull("Expected non-null snap scale when close to target", near)
        assertEquals("Snap should point to computed fit-width scale", target, near!!, 1e-3f)

        // Far from the target → should not snap
        val far = ZoomController.computeSnapFitWidthScale(
            true, /*fitWidth*/ false, /*reflow*/ 1.0f,
            cw, ch, padL, padR, padT, padB, viewMeasuredW, viewMeasuredH, minScale, maxScale
        )
        assertNull("Expected null when far from target", far)
    }
}
