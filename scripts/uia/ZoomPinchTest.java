package org.opendroidpdf.uia;

import com.android.uiautomator.testrunner.UiAutomatorTestCase;
import com.android.uiautomator.core.UiDevice;
import com.android.uiautomator.core.UiObject;
import com.android.uiautomator.core.UiSelector;
import android.graphics.Rect;

public final class ZoomPinchTest extends UiAutomatorTestCase {
    public void testProgressiveZoomInDoesNotCrash() throws Exception {
        UiDevice device = getUiDevice();

        // Pinch on the reader view if it is exposed; otherwise pinch on any package view.
        UiObject pinchTarget = new UiObject(new UiSelector().className("org.opendroidpdf.MuPDFReaderView"));
        if (!pinchTarget.waitForExists(5_000)) {
            pinchTarget = new UiObject(new UiSelector().packageName("org.opendroidpdf"));
            pinchTarget.waitForExists(5_000);
        }

        Rect bounds = pinchTarget.getBounds();
        int startX = bounds.left + (int) (bounds.width() * 0.75f);
        int endX = bounds.left + (int) (bounds.width() * 0.25f);
        int dragY = bounds.top + (int) (bounds.height() * 0.55f);

        // Progressive zoom-in: multiple small pinch-outs.
        for (int i = 0; i < 10; i++) {
            pinchTarget.pinchOut(90, 32);
            Thread.sleep(2_000);

            // Fail fast if we got kicked out of the app or a crash dialog appears.
            String pkg = device.getCurrentPackageName();
            assertEquals("org.opendroidpdf", pkg);

            UiObject crash = new UiObject(new UiSelector().textContains("keeps stopping"));
            if (crash.exists()) {
                fail("Crash dialog detected after zoom-in");
            }

            // After zooming in, pan the page by pressing + dragging with a single finger.
            device.swipe(startX, dragY, endX, dragY, 40);
            Thread.sleep(750);

            pkg = device.getCurrentPackageName();
            assertEquals("org.opendroidpdf", pkg);

            crash = new UiObject(new UiSelector().textContains("keeps stopping"));
            if (crash.exists()) {
                fail("Crash dialog detected after zoom+drag");
            }

            UiObject anr = new UiObject(new UiSelector().textContains("isn't responding"));
            if (anr.exists()) {
                fail("ANR dialog detected after zoom+drag");
            }
        }
    }
}
