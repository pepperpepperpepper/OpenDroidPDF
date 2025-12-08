package org.opendroidpdf

import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opendroidpdf.Annotation
import org.opendroidpdf.Annotation.Type
import org.opendroidpdf.app.annotation.InkUndoController

@RunWith(AndroidJUnit4::class)
class InkUndoControllerTest {

    private class FakeBackend : InkUndoController.Backend {
        val annotations = mutableListOf<Annotation>()
        var dirty = false
        override fun annotations(page: Int): Array<Annotation> = annotations.toTypedArray()
        override fun deleteAnnotation(page: Int, index: Int) {
            if (index in annotations.indices) annotations.removeAt(index)
        }
        override fun markDocumentDirty() { dirty = true }
    }

    @Test
    fun undoRemovesMatchedInkAndMarksDirty() {
        val backend = FakeBackend()
        val host = object : InkUndoController.Host {
            var mutated = 0
            override fun pageNumber(): Int = 0
            override fun onInkStackMutated() { mutated++ }
        }

        val arcs = arrayOf(arrayOf(PointF(0f, 0f), PointF(1f, 1f)))
        backend.annotations.add(Annotation(0f, 0f, 10f, 10f, Type.INK, arcs, null, 42L))

        val controller = InkUndoController(host, backend, "TEST", false)
        controller.recordCommittedInkForUndo(arcs)

        assertTrue(controller.hasUndo())
        assertEquals(1, controller.stackSize())

        val undone = controller.undoLast()
        assertTrue(undone)
        assertEquals(0, backend.annotations.size)
        assertTrue(backend.dirty)
        assertEquals(0, controller.stackSize())
        assertEquals(1, host.mutated)
    }

    @Test
    fun undoFailsWhenNoMatch() {
        val backend = FakeBackend()
        val host = object : InkUndoController.Host {
            override fun pageNumber(): Int = 0
            override fun onInkStackMutated() { }
        }
        val controller = InkUndoController(host, backend, "TEST", false)

        val result = controller.undoLast()
        assertFalse(result)
        assertEquals(0, controller.stackSize())
    }
}
