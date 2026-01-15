package org.opendroidpdf

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Ignore
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * UI-level tests that drive the overflow menu to invoke qpdf-backed save flows.
 *
 * These tests stub the ACTION_CREATE_DOCUMENT response to a FileProvider URI so
 * no user interaction with DocumentsUI is required.
 */
@RunWith(AndroidJUnit4::class)
class ExportUiQpdfActionsTest {

    private lateinit var appCtx: Context
    private lateinit var docFile: File
    private lateinit var docUri: Uri

    @Before
    fun setUp() {
        appCtx = ApplicationProvider.getApplicationContext()
        prepareSourceDoc()
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Ignore("Flaky on headless emulator; covered by controller-level tests")
    @Test
    fun saveLinearizedFromMenuCreatesFile() {
        val dest = File(appCtx.cacheDir, "intent_linearized.pdf")
        val destUri = FileProvider.getUriForFile(appCtx, "org.opendroidpdf.fileprovider", dest)
        stubCreateDocument(destUri)

        launchWithDoc()
        Thread.sleep(800)
        openActionBarOverflowOrOptionsMenu(appCtx)
        onView(withText(R.string.menu_save_linearized)).perform(click())

        // Wait a bit for background task to finish
        Thread.sleep(1800)
        assertTrue(dest.exists())
        assertTrue(dest.length() > 0)
    }

    @Ignore("Dialog not reliably shown in headless CI; covered by controller tests")
    @Test
    fun saveEncryptedFromMenuCreatesFile() {
        val dest = File(appCtx.cacheDir, "intent_encrypted.pdf")
        val destUri = FileProvider.getUriForFile(appCtx, "org.opendroidpdf.fileprovider", dest)
        stubCreateDocument(destUri)

        launchWithDoc()
        openActionBarOverflowOrOptionsMenu(appCtx)
        onView(withText(R.string.menu_save_encrypted)).perform(click())
        onView(withHint(R.string.encrypt_user_password)).perform(replaceText("userpw"), closeSoftKeyboard())
        onView(withHint(R.string.encrypt_owner_password)).perform(replaceText("ownerpw"), closeSoftKeyboard())
        onView(withText(R.string.menu_save_encrypted)).perform(click())

        Thread.sleep(1500)
        assertTrue(dest.exists())
        assertTrue(dest.length() > 0)
    }

    private fun launchWithDoc() {
        val intent = Intent(Intent.ACTION_VIEW, docUri).apply {
            setClass(appCtx, OpenDroidPDFActivity::class.java)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ActivityScenario.launch<OpenDroidPDFActivity>(intent)
    }

    private fun stubCreateDocument(resultUri: Uri) {
        val resultData = Intent().apply { data = resultUri }
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(result)
    }

    private fun prepareSourceDoc() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        docFile = File(appCtx.cacheDir, "source_for_ui.pdf")
        assets.open("two_page_sample.pdf").use { input ->
            FileOutputStream(docFile).use { output -> input.copyTo(output) }
        }
        docUri = FileProvider.getUriForFile(appCtx, "org.opendroidpdf.fileprovider", docFile)
    }
}
