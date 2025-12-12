package org.opendroidpdf.app.helpers;

import android.content.Context;
import android.view.ViewGroup;

/**
 * Small wrapper around BusyIndicatorHelper that owns the handle lifecycle
 * so callers don’t need to track it explicitly.
 */
public final class BusyIndicatorAdapter {
    private BusyIndicatorHelper.Handle handle;

    public void attachIfNeeded(ViewGroup parent, Context ctx, int delayMs) {
        if (handle == null) {
            handle = BusyIndicatorHelper.attachWithDelay(parent, ctx, delayMs);
        }
    }

    public void cancelAndRemove(ViewGroup parent) {
        BusyIndicatorHelper.cancelAndRemove(parent, handle);
        handle = null;
    }

    public void measureCenter(int width, int height) {
        BusyIndicatorHelper.measureCenter(handle, width, height);
    }

    public BusyIndicatorHelper.Handle getHandle() {
        return handle;
    }
}

