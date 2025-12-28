package org.opendroidpdf.app.diagnostics;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * App-owned lightweight log sink that persists to cache so production crashes can be debugged
 * without requiring logcat access on the device.
 */
public final class AppLog {
    private static final String AUTHORITY = "org.opendroidpdf.fileprovider";
    private static final long MAX_BYTES = 512 * 1024;
    private static final Object LOCK = new Object();

    @Nullable private static File logFile;
    @Nullable private static File logDir;

    private AppLog() {}

    public static void init(Context context) {
        if (context == null) return;
        synchronized (LOCK) {
            if (logFile != null) return;
            File dir = new File(context.getCacheDir(), "tmpfiles");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            logDir = dir;
            logFile = new File(dir, "opendroidpdf_app.log");
        }
        i("AppLog", "init pid=" + android.os.Process.myPid());
    }

    public static void i(String tag, String msg) {
        Log.i(tag, msg);
        append("I", tag, msg, null);
    }

    public static void w(String tag, String msg) {
        Log.w(tag, msg);
        append("W", tag, msg, null);
    }

    public static void e(String tag, String msg, @Nullable Throwable t) {
        Log.e(tag, msg, t);
        append("E", tag, msg, t);
    }

    public static boolean hasLogFile() {
        File f = logFile;
        return f != null && f.isFile() && f.length() > 0;
    }

    @Nullable
    public static android.net.Uri getLogUri(Context context) {
        File f = logFile;
        if (context == null || f == null || !f.isFile()) return null;
        try {
            return FileProvider.getUriForFile(context, AUTHORITY, f);
        } catch (Throwable t) {
            Log.e("AppLog", "Failed to build FileProvider URI", t);
            return null;
        }
    }

    private static void append(String level, String tag, String msg, @Nullable Throwable t) {
        File f;
        File dir;
        synchronized (LOCK) {
            f = logFile;
            dir = logDir;
        }
        if (f == null || dir == null) return;

        StringBuilder line = new StringBuilder();
        line.append(System.currentTimeMillis()).append(' ')
                .append(level).append('/')
                .append(tag).append(": ")
                .append(msg);
        if (t != null) {
            line.append(" (").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(')');
        }
        line.append('\n');

        synchronized (LOCK) {
            try {
                if (f.isFile() && f.length() > MAX_BYTES) {
                    File rotated = new File(dir, "opendroidpdf_app.log.1");
                    //noinspection ResultOfMethodCallIgnored
                    rotated.delete();
                    //noinspection ResultOfMethodCallIgnored
                    f.renameTo(rotated);
                }
                try (FileOutputStream out = new FileOutputStream(f, true)) {
                    out.write(line.toString().getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (Throwable ignore) {
                // Never crash for logging.
            }
        }
    }
}

