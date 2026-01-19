package org.opendroidpdf

import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opendroidpdf.app.selection.TextSelectionActions

@RunWith(AndroidJUnit4::class)
class TextSelectionActionsQuadOrderTest {

    private fun word(left: Float, top: Float, right: Float, bottom: Float, text: String): TextWord {
        val w = TextWord()
        w.set(left, top, right, bottom)
        w.w = text
        return w
    }

    @Test
    fun markupSelectionProducesUlUrLlLrOrder() {
        val actions = TextSelectionActions()

        var captured: Array<PointF>? = null

        val host = object : TextSelectionActions.Host {
            override fun processSelectedText(processor: TextProcessor) {
                val w1 = word(10f, 20f, 30f, 40f, "Hello")
                val w2 = word(35f, 20f, 50f, 40f, "World")
                processor.onStartLine()
                processor.onWord(w1)
                processor.onWord(w2)
                processor.onEndLine()
                processor.onEndText()
            }

            override fun deselectText() {
                // no-op
            }

            override fun getContext() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        }

        val ok = actions.markupSelection(
            host,
            Annotation.Type.UNDERLINE,
            TextSelectionActions.AddMarkup { quadPoints, _, _, onComplete ->
                captured = quadPoints
                onComplete.run()
            }
        )

        assertTrue(ok)
        val q = captured
        assertNotNull(q)
        assertEquals(4, q!!.size)

        // Expected union rect: left=10 top=20 right=50 bottom=40.
        assertPoint(10f, 20f, q[0]) // UL
        assertPoint(50f, 20f, q[1]) // UR
        assertPoint(10f, 40f, q[2]) // LL
        assertPoint(50f, 40f, q[3]) // LR
    }

    @Test
    fun caretSelectionProducesUlUrLlLrOrder() {
        val actions = TextSelectionActions()

        var captured: Array<PointF>? = null

        val host = object : TextSelectionActions.Host {
            override fun processSelectedText(processor: TextProcessor) {
                val w1 = word(10f, 20f, 30f, 40f, "Hello")
                val w2 = word(35f, 20f, 50f, 40f, "World")
                processor.onStartLine()
                processor.onWord(w1)
                processor.onWord(w2)
                processor.onEndLine()
                processor.onEndText()
            }

            override fun deselectText() {
                // no-op
            }

            override fun getContext() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        }

        val ok = actions.markupSelection(
            host,
            Annotation.Type.CARET,
            TextSelectionActions.AddMarkup { quadPoints, _, _, onComplete ->
                captured = quadPoints
                onComplete.run()
            }
        )

        assertTrue(ok)
        val q = captured
        assertNotNull(q)
        assertEquals(4, q!!.size)

        // caretWidth = max(lineHeight*0.35, 6) = max(20*0.35, 6) = 7
        assertPoint(10f, 20f, q[0]) // UL
        assertPoint(17f, 20f, q[1]) // UR
        assertPoint(10f, 40f, q[2]) // LL
        assertPoint(17f, 40f, q[3]) // LR
    }

    private fun assertPoint(x: Float, y: Float, p: PointF?) {
        assertNotNull(p)
        assertEquals(x, p!!.x, 0.0f)
        assertEquals(y, p.y, 0.0f)
    }
}
