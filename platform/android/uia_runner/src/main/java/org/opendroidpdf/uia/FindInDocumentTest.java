package org.opendroidpdf.uia;

import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class FindInDocumentTest {
    private static final String TARGET_PKG = "org.opendroidpdf";
    private static final long TARGET_TIMEOUT_MS = 15_000;

    // Minimal 2-page PDF with the text "needle" once on each page (base64-encoded).
    private static final String PDF_BASE64 =
            "JVBERi0xLjQKJeLjz9MKMSAwIG9iago8PCAvVHlwZSAvQ2F0YWxvZyAvUGFnZXMgMiAwIFIgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL1R5cGUgL1BhZ2VzIC9LaWRzIFszIDAgUiA0IDAgUl0gL0NvdW50IDIgPj4KZW5kb2JqCjMgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCA2MTIgNzkyXSAvQ29udGVudHMgNSAwIFIgL1Jlc291cmNlcyA8PCAvRm9udCA8PCAvRjEgNyAwIFIgPj4gPj4gPj4KZW5kb2JqCjQgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCA2MTIgNzkyXSAvQ29udGVudHMgNiAwIFIgL1Jlc291cmNlcyA8PCAvRm9udCA8PCAvRjEgNyAwIFIgPj4gPj4gPj4KZW5kb2JqCjUgMCBvYmoKPDwgL0xlbmd0aCAzOSA+PgpzdHJlYW0KQlQKL0YxIDI0IFRmCjEwMCA3MDAgVGQKKG5lZWRsZSkgVGoKRVQKZW5kc3RyZWFtCmVuZG9iago2IDAgb2JqCjw8IC9MZW5ndGggMzkgPj4Kc3RyZWFtCkJUCi9GMSAyNCBUZgoxMDAgNzAwIFRkCihuZWVkbGUpIFRqCkVUCmVuZHN0cmVhbQplbmRvYmoKNyAwIG9iago8PCAvVHlwZSAvRm9udCAvU3VidHlwZSAvVHlwZTEgL0Jhc2VGb250IC9IZWx2ZXRpY2EgPj4KZW5kb2JqCnhyZWYKMCA4CjAwMDAwMDAwMDAgNjU1MzUgZiAKMDAwMDAwMDAxNSAwMDAwMCBuIAowMDAwMDAwMDY0IDAwMDAwIG4gCjAwMDAwMDAxMjcgMDAwMDAgbiAKMDAwMDAwMDI1MyAwMDAwMCBuIAowMDAwMDAwMzc5IDAwMDAwIG4gCjAwMDAwMDA0NjcgMDAwMDAgbiAKMDAwMDAwMDU1NSAwMDAwMCBuIAp0cmFpbGVyCjw8IC9TaXplIDggL1Jvb3QgMSAwIFIgPj4Kc3RhcnR4cmVmCjYyNQolJUVPRgo=";

    private static UiDevice device() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    private static void assertNoCrashDialogs(UiDevice device) {
        if (device.hasObject(By.textContains("keeps stopping"))) {
            fail("Crash dialog detected");
        }
        if (device.hasObject(By.textContains("isn't responding"))) {
            fail("ANR dialog detected");
        }
    }

    private static void writeTestPdfToDownloads(UiDevice device, String remotePath) throws Exception {
        // Use shell to avoid scoped-storage permission issues.
        String cmd = "mkdir -p /sdcard/Download"
                + " && printf '%s' '" + PDF_BASE64 + "'"
                + " | toybox base64 -d > " + remotePath
                + " && sync";
        device.executeShellCommand(cmd);
    }

    private static void launchPdf(UiDevice device, String remotePath) throws Exception {
        device.executeShellCommand("pm clear " + TARGET_PKG);
        String uri = "file://" + remotePath;
        device.executeShellCommand(
                "am start -W -a android.intent.action.VIEW"
                        + " -d \"" + uri + "\""
                        + " -t application/pdf"
                        + " -n " + TARGET_PKG + "/.OpenDroidPDFActivity");
        device.wait(Until.hasObject(By.pkg(TARGET_PKG)), TARGET_TIMEOUT_MS);
        device.wait(Until.hasObject(By.clazz("org.opendroidpdf.MuPDFReaderView")), TARGET_TIMEOUT_MS);
    }

    private static void clickOrFail(UiDevice device, BySelector selector, String label) {
        UiObject2 obj = device.findObject(selector);
        assertNotNull(label + " not found", obj);
        obj.click();
    }

    private static void waitForText(UiDevice device, BySelector selector, String expected, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 obj = device.findObject(selector);
            if (obj != null) {
                String text = obj.getText();
                if (expected.equals(text)) return;
            }
            SystemClock.sleep(120);
        }
        UiObject2 obj = device.findObject(selector);
        String got = obj != null ? obj.getText() : null;
        fail("Timed out waiting for text '" + expected + "'; got '" + got + "'");
    }

    @Test
    public void testFindInDocumentBar_matchCounter_nextPrev_close() throws Exception {
        UiDevice device = device();
        String remotePdf = "/sdcard/Download/odp_find_in_document_test.pdf";

        writeTestPdfToDownloads(device, remotePdf);
        launchPdf(device, remotePdf);

        // Enable reading mode (hide top toolbar) to ensure Find remains usable without it.
        device.wait(Until.hasObject(By.res(TARGET_PKG, "page_indicator")), TARGET_TIMEOUT_MS);
        clickOrFail(device, By.res(TARGET_PKG, "page_indicator"), "Page indicator");
        device.wait(Until.hasObject(By.res(TARGET_PKG, "navigate_view_row_reading_mode")), TARGET_TIMEOUT_MS);
        clickOrFail(device, By.res(TARGET_PKG, "navigate_view_row_reading_mode"), "Reading mode row");
        device.pressBack();

        // Open Find from the always-available bottom quick actions (More tools -> Search).
        device.wait(Until.hasObject(By.res(TARGET_PKG, "quick_action_more_tools")), TARGET_TIMEOUT_MS);
        clickOrFail(device, By.res(TARGET_PKG, "quick_action_more_tools"), "More tools");
        device.wait(Until.hasObject(By.text("Search")), TARGET_TIMEOUT_MS);
        clickOrFail(device, By.text("Search"), "Search item");

        device.wait(Until.hasObject(By.res(TARGET_PKG, "find_in_document_query")), TARGET_TIMEOUT_MS);
        UiObject2 query = device.findObject(By.res(TARGET_PKG, "find_in_document_query"));
        assertNotNull("Find query field not found", query);
        query.setText("needle");
        device.pressEnter(); // submit: jumps to first match

        waitForText(device, By.res(TARGET_PKG, "find_in_document_counter"), "1 / 2", TARGET_TIMEOUT_MS);

        clickOrFail(device, By.res(TARGET_PKG, "find_in_document_next"), "Find next");
        waitForText(device, By.res(TARGET_PKG, "find_in_document_counter"), "2 / 2", TARGET_TIMEOUT_MS);

        clickOrFail(device, By.res(TARGET_PKG, "find_in_document_prev"), "Find previous");
        waitForText(device, By.res(TARGET_PKG, "find_in_document_counter"), "1 / 2", TARGET_TIMEOUT_MS);

        clickOrFail(device, By.res(TARGET_PKG, "find_in_document_close"), "Find close");
        assertTrue("Find bar did not close",
                device.wait(Until.gone(By.res(TARGET_PKG, "find_in_document_query")), TARGET_TIMEOUT_MS));

        assertEquals(TARGET_PKG, device.getCurrentPackageName());
        assertNoCrashDialogs(device);
    }
}
