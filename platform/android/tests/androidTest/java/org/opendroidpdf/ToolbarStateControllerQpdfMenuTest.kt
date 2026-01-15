package org.opendroidpdf

import android.content.Context
import android.view.MenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.opendroidpdf.app.toolbar.ToolbarStateController

@RunWith(AndroidJUnit4::class)
class ToolbarStateControllerQpdfMenuTest {

    @Test
    fun qpdfShareItemsVisibleForPdfWhenFlagOn() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val menu = MenuBuilder(ctx)
        val inflater = MenuInflater(ctx)
        inflater.inflate(org.opendroidpdf.R.menu.main_menu, menu)

        val host = FakeHost(
            hasDoc = true,
            hasDocView = true,
            isPdf = true,
            isEpub = false
        )
        val controller = ToolbarStateController(host)
        controller.onPrepareOptionsMenu(menu)

        val linear = menu.findItem(org.opendroidpdf.R.id.menu_share_linearized)
        val encrypted = menu.findItem(org.opendroidpdf.R.id.menu_share_encrypted)
        val expected = BuildConfig.ENABLE_QPDF_OPS
        assertEquals(expected, linear?.isVisible == true)
        assertEquals(expected, encrypted?.isVisible == true)
        assertEquals(expected, linear?.isEnabled == true)
        assertEquals(expected, encrypted?.isEnabled == true)
    }

    @Test
    fun qpdfShareItemsHiddenWhenNotPdf() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val menu = MenuBuilder(ctx)
        val inflater = MenuInflater(ctx)
        inflater.inflate(org.opendroidpdf.R.menu.main_menu, menu)

        val host = FakeHost(
            hasDoc = true,
            hasDocView = true,
            isPdf = false,
            isEpub = true
        )
        val controller = ToolbarStateController(host)
        controller.onPrepareOptionsMenu(menu)

        val linear = menu.findItem(org.opendroidpdf.R.id.menu_share_linearized)
        val encrypted = menu.findItem(org.opendroidpdf.R.id.menu_share_encrypted)
        assertFalse(linear?.isVisible == true)
        assertFalse(encrypted?.isVisible == true)
    }

    private class FakeHost(
        private val hasDoc: Boolean,
        private val hasDocView: Boolean,
        private val isPdf: Boolean,
        private val isEpub: Boolean
    ) : ToolbarStateController.Host {
        override fun hasOpenDocument() = hasDoc
        override fun hasDocumentView() = hasDocView
        override fun canUndo() = false
        override fun canRedo() = false
        override fun hasUnsavedChanges() = false
        override fun hasLinkTarget() = false
        override fun isPdfDocument() = isPdf
        override fun isEpubDocument() = isEpub
        override fun canSaveToCurrentUri() = true
        override fun isViewingNoteDocument() = false
        override fun isDrawingModeActive() = false
        override fun isErasingModeActive() = false
        override fun isFormFieldHighlightEnabled() = false
        override fun areCommentsVisible() = false
        override fun areSidecarNotesStickyModeEnabled() = false
        override fun isSelectedAnnotationEditable() = false
        override fun isPreparingOptionsMenu() = false
        override fun invalidateOptionsMenu() {}
    }
}
