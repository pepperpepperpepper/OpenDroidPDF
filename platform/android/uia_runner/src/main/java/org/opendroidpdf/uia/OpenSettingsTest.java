package org.opendroidpdf.uia;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class OpenSettingsTest {
    private static final String TARGET_PKG = "org.opendroidpdf";
    private static final long TARGET_TIMEOUT_MS = 8_000;

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

    private static void launchDashboardFresh(UiDevice device) throws Exception {
        device.executeShellCommand("pm clear " + TARGET_PKG);
        device.executeShellCommand(
                "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "
                        + TARGET_PKG + "/.OpenDroidPDFActivity");
        device.wait(Until.hasObject(By.pkg(TARGET_PKG)), TARGET_TIMEOUT_MS);
        device.wait(Until.hasObject(By.res(TARGET_PKG, "menu_settings")), TARGET_TIMEOUT_MS);
    }

    private static void waitForSettingsActivity(UiDevice device) {
        device.wait(Until.hasObject(By.res(TARGET_PKG, "sub_layout")), TARGET_TIMEOUT_MS);
    }

    @Test
    public void testOpenSettingsFromDashboardDoesNotCrash() throws Exception {
        UiDevice device = device();
        launchDashboardFresh(device);

        UiObject2 settings = device.findObject(By.res(TARGET_PKG, "menu_settings"));
        assertNotNull("Settings button not found on dashboard", settings);
        settings.click();

        waitForSettingsActivity(device);
        assertEquals(TARGET_PKG, device.getCurrentPackageName());
        assertNoCrashDialogs(device);
    }
}
