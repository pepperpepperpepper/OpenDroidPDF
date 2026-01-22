package org.opendroidpdf.app.diagnostics;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
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

    /**
     * Writes the crash report file into a user-chosen destination URI (SAF).
     *
     * @return true if the write succeeded.
     */
    public static boolean writeCrashReportToUri(Context context, @Nullable android.net.Uri dest) {
        File f;
        synchronized (LOCK) { f = crashFile; }
        if (context == null || dest == null || f == null || !f.isFile() || f.length() <= 0) return false;

        try (FileInputStream in = new FileInputStream(f);
             OutputStream out = context.getContentResolver().openOutputStream(dest)) {
            if (out == null) return false;
            byte[] buf = new byte[8192];
            while (true) {
                int r = in.read(buf);
                if (r <= 0) break;
                out.write(buf, 0, r);
            }
            out.flush();
            return true;
        } catch (Throwable t) {
            Log.e("CrashReporter", "Failed writing crash report to uri", t);
            return false;
        }
    }

    /**
     * Reads the crash report as UTF-8 text for copy/paste and sharing.
     * Uses a small cap to avoid OOM if the file is unexpectedly large.
     */
    @Nullable
    public static String readCrashReportText() {
        final long maxBytes = 256L * 1024L; // 256KiB
        File f;
        synchronized (LOCK) { f = crashFile; }
        if (f == null || !f.isFile() || f.length() <= 0) return null;

        long totalRead = 0;
        boolean truncated = false;
        byte[] buf = new byte[8192];

        try (FileInputStream in = new FileInputStream(f)) {
            // Keep it simple: StringBuilder via chunks.
            StringBuilder sb = new StringBuilder((int) Math.min(maxBytes, f.length()));
            Charset utf8 = StandardCharsets.UTF_8;
            while (true) {
                int r = in.read(buf);
                if (r <= 0) break;

                long remaining = maxBytes - totalRead;
                if (remaining <= 0) {
                    truncated = true;
                    break;
                }
                if (r > remaining) {
                    r = (int) remaining;
                    truncated = true;
                }
                sb.append(new String(buf, 0, r, utf8));
                totalRead += r;

                if (truncated) break;
            }
            if (truncated) {
                sb.append("\n\n[truncated]\n");
            }
            return sb.toString();
        } catch (Throwable t) {
            Log.e("CrashReporter", "Failed reading crash file", t);
            return null;
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
