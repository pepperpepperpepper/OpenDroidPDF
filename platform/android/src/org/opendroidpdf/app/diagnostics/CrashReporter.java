package org.opendroidpdf.app.diagnostics;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persists uncaught Java exceptions to a shareable file under cache/tmpfiles/.
 * This is intentionally small and synchronous so it can run in production.
 */
public final class CrashReporter {
    private static final String AUTHORITY = "org.opendroidpdf.fileprovider";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final Object LOCK = new Object();

    @Nullable private static File crashFile;
    @Nullable private static Thread.UncaughtExceptionHandler previousHandler;

    private CrashReporter() {}

    public static void install(Context context) {
        if (context == null) return;
        if (!INSTALLED.compareAndSet(false, true)) return;

        File dir = new File(context.getCacheDir(), "tmpfiles");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        synchronized (LOCK) {
            crashFile = new File(dir, "opendroidpdf_last_crash.txt");
        }

        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeCrashReport(thread, throwable);
            } catch (Throwable t) {
                Log.e("CrashReporter", "Failed to write crash report", t);
            }
            Thread.UncaughtExceptionHandler prev = previousHandler;
            if (prev != null) {
                prev.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    public static boolean hasCrashReport() {
        File f;
        synchronized (LOCK) { f = crashFile; }
        return f != null && f.isFile() && f.length() > 0;
    }

    @Nullable
    public static android.net.Uri getCrashReportUri(Context context) {
        File f;
        synchronized (LOCK) { f = crashFile; }
        if (context == null || f == null || !f.isFile()) return null;
        try {
            return FileProvider.getUriForFile(context, AUTHORITY, f);
        } catch (Throwable t) {
            Log.e("CrashReporter", "Failed to build FileProvider URI", t);
            return null;
        }
    }

    public static void clearCrashReport() {
        File f;
        synchronized (LOCK) { f = crashFile; }
        if (f != null) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private static void writeCrashReport(@Nullable Thread thread, @Nullable Throwable throwable) {
        File f;
        synchronized (LOCK) { f = crashFile; }
        if (f == null) return;

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("OpenDroidPDF crash report");
        pw.println("timeMs=" + System.currentTimeMillis());
        pw.println("thread=" + (thread != null ? thread.getName() : "null"));
        pw.println("pid=" + android.os.Process.myPid());
        pw.println();
        if (throwable != null) throwable.printStackTrace(pw);
        pw.flush();

        byte[] bytes = sw.toString().getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            out.write(bytes);
            out.flush();
        } catch (Throwable t) {
            Log.e("CrashReporter", "Failed writing crash file", t);
        }

        try {
            AppLog.e("CrashReporter", "uncaught exception captured", throwable);
        } catch (Throwable ignore) {}
    }
}

