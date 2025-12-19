package org.opendroidpdf.app.helpers;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

public final class BusyIndicatorHelper {
    private BusyIndicatorHelper() {}

    public static final class Handle {
        public final ProgressBar bar;
        private final Runnable showRunnable;

        Handle(ProgressBar bar, Runnable showRunnable) {
            this.bar = bar;
            this.showRunnable = showRunnable;
        }
    }

    public static Handle attachWithDelay(ViewGroup host, Context ctx, int delayMs) {
        ProgressBar bar = new ProgressBar(ctx);
        bar.setIndeterminate(true);
        host.addView(bar);
        bar.setVisibility(View.INVISIBLE);
        Runnable showRunnable = new Runnable() {
            @Override public void run() {
                bar.setVisibility(View.VISIBLE);
            }
        };
        bar.postDelayed(showRunnable, delayMs);
        return new Handle(bar, showRunnable);
    }

    public static void cancelAndRemove(ViewGroup host, Handle handle) {
        if (handle == null) return;
        try { if (handle.bar != null) handle.bar.removeCallbacks(handle.showRunnable); } catch (Throwable ignore) {}
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
