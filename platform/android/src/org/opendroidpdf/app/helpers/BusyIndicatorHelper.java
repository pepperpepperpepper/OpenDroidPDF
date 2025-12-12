package org.opendroidpdf.app.helpers;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import kotlinx.coroutines.Job;
import org.opendroidpdf.app.AppCoroutines;

public final class BusyIndicatorHelper {
    private BusyIndicatorHelper() {}

    public static final class Handle {
        public final ProgressBar bar;
        public final Job job;

        Handle(ProgressBar bar, Job job) {
            this.bar = bar;
            this.job = job;
        }
    }

    public static Handle attachWithDelay(ViewGroup host, Context ctx, int delayMs) {
        ProgressBar bar = new ProgressBar(ctx);
        bar.setIndeterminate(true);
        host.addView(bar);
        bar.setVisibility(View.INVISIBLE);
        Job job = AppCoroutines.launchMainDelayed(AppCoroutines.mainScope(), delayMs, new Runnable() {
            @Override public void run() {
                bar.setVisibility(View.VISIBLE);
            }
        });
        return new Handle(bar, job);
    }

    public static void cancelAndRemove(ViewGroup host, Handle handle) {
        if (handle == null) return;
        try { AppCoroutines.cancel(handle.job); } catch (Throwable ignore) {}
        try { if (handle.bar != null) host.removeView(handle.bar); } catch (Throwable ignore) {}
    }

    public static void measureCenter(Handle handle, int parentWidth, int parentHeight) {
        if (handle == null || handle.bar == null) return;
        int limit = Math.min(parentWidth, parentHeight) / 2;
        handle.bar.measure(View.MeasureSpec.AT_MOST | limit, View.MeasureSpec.AT_MOST | limit);
    }

    public static void layoutCenter(Handle handle, int width, int height) {
        if (handle == null || handle.bar == null) return;
        int bw = handle.bar.getMeasuredWidth();
        int bh = handle.bar.getMeasuredHeight();
        handle.bar.layout((width - bw) / 2, (height - bh) / 2, (width + bw) / 2, (height + bh) / 2);
    }
}
