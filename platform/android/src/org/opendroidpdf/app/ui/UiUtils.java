package org.opendroidpdf.app.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.widget.Toast;

public final class UiUtils {
    private UiUtils() {}

    public static void showInfo(Context ctx, String message) {
        if (ctx == null) return;
        Toast.makeText(ctx.getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    public static int dpToPixel(Context ctx, float dp) {
        if (ctx == null) return (int) dp;
        float scale = ctx.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    public static boolean isMemoryLow(Context ctx) {
        ActivityManager.MemoryInfo info = getAvailableMemory(ctx);
        return info != null && info.lowMemory;
    }

    public static ActivityManager.MemoryInfo getAvailableMemory(Context ctx) {
        if (ctx == null) return null;
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }
}

