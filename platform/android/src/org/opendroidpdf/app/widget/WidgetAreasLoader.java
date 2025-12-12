package org.opendroidpdf.app.widget;

import android.graphics.RectF;

import org.opendroidpdf.core.WidgetAreasCallback;
import org.opendroidpdf.core.WidgetController;
import org.opendroidpdf.core.WidgetController.WidgetJob;

/**
 * Manages async loading of widget areas for a page and cancels in-flight jobs when needed.
 */
public class WidgetAreasLoader {
    private final WidgetController controller;
    private WidgetJob currentJob;

    public WidgetAreasLoader(WidgetController controller) {
        this.controller = controller;
    }

    public void load(int pageIndex, WidgetAreasCallback callback) {
        cancel();
        currentJob = controller.loadWidgetAreasAsync(pageIndex, callback);
    }

    public void cancel() {
        if (currentJob != null) {
            currentJob.cancel();
            currentJob = null;
        }
    }

    public void release() {
        cancel();
    }
}

