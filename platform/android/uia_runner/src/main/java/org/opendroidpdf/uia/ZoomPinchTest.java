package org.opendroidpdf.uia;

import android.graphics.Rect;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class ZoomPinchTest {
    private static final String TARGET_PKG = "org.opendroidpdf";
    private static final long TARGET_TIMEOUT_MS = 5_000;

    private static UiDevice device() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    private static UiObject2 findPinchTarget(UiDevice device) {
        // Prefer the reader view (custom class). If it isn't exposed, fall back to any view
        // owned by the target package.
        device.wait(Until.hasObject(By.pkg(TARGET_PKG)), TARGET_TIMEOUT_MS);

        device.wait(Until.hasObject(By.clazz("org.opendroidpdf.MuPDFReaderView")), TARGET_TIMEOUT_MS);
        UiObject2 readerView = device.findObject(By.clazz("org.opendroidpdf.MuPDFReaderView"));
        if (readerView != null) {
            return readerView;
        }

        return device.findObject(By.pkg(TARGET_PKG));
    }

    private static void assertNoCrashDialogs(UiDevice device) {
        if (device.hasObject(By.textContains("keeps stopping"))) {
            fail("Crash dialog detected");
        }
        if (device.hasObject(By.textContains("isn't responding"))) {
            fail("ANR dialog detected");
        }
    }

    private static void pinchOut(UiObject2 target) {
        // UIAutomator2 pinchOpen uses a 0..1 scale factor for the pinch percent.
        target.pinchOpen(0.90f, 32);
    }

    @Test
    public void testPinchOutOnlyDoesNotCrash() throws Exception {
        UiDevice device = device();

        for (int i = 0; i < 4; i++) {
            UiObject2 pinchTarget = findPinchTarget(device);
            assertNotNull("Pinch target not found", pinchTarget);
            try {
                pinchOut(pinchTarget);
            } catch (StaleObjectException stale) {
                // The view hierarchy can change between steps (menus/overlays); re-query once.
                pinchTarget = findPinchTarget(device);
                assertNotNull("Pinch target not found (retry)", pinchTarget);
                pinchOut(pinchTarget);
            }
            Thread.sleep(1_200);

            assertEquals(TARGET_PKG, device.getCurrentPackageName());
            assertNoCrashDialogs(device);
        }
    }

    @Test
    public void testProgressiveZoomInDoesNotCrash() throws Exception {
        UiDevice device = device();

        for (int i = 0; i < 10; i++) {
            UiObject2 pinchTarget = findPinchTarget(device);
            assertNotNull("Pinch target not found", pinchTarget);
            try {
                pinchOut(pinchTarget);
            } catch (StaleObjectException stale) {
                pinchTarget = findPinchTarget(device);
                assertNotNull("Pinch target not found (retry)", pinchTarget);
                pinchOut(pinchTarget);
            }
            Thread.sleep(2_000);

            assertEquals(TARGET_PKG, device.getCurrentPackageName());
            assertNoCrashDialogs(device);

            Rect bounds = pinchTarget.getVisibleBounds();
            int startX = bounds.left + (int) (bounds.width() * 0.75f);
            int endX = bounds.left + (int) (bounds.width() * 0.25f);
            int dragY = bounds.top + (int) (bounds.height() * 0.55f);

            device.swipe(startX, dragY, endX, dragY, 40);
            Thread.sleep(750);

            assertEquals(TARGET_PKG, device.getCurrentPackageName());
            assertNoCrashDialogs(device);
        }
    }
}
