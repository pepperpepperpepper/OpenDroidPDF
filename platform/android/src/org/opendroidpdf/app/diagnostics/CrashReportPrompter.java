package org.opendroidpdf.app.diagnostics;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.R;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UI glue for offering a share action for crash/log artifacts.
 */
public final class CrashReportPrompter {
    private static final AtomicBoolean SHOWN_THIS_PROCESS = new AtomicBoolean(false);

    private CrashReportPrompter() {}

    public static void maybePrompt(AppCompatActivity activity, @Nullable SessionDiagnostics.PreviousSession prev) {
        if (activity == null) return;
        if (!SHOWN_THIS_PROCESS.compareAndSet(false, true)) return;

        boolean hasCrashFile = CrashReporter.hasCrashReport();
        if (!hasCrashFile) return;

        final Uri uriToShare = CrashReporter.getCrashReportUri(activity);
        if (uriToShare == null) return;

        String message = activity.getString(R.string.debug_crash_report_message);

        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(activity.getString(R.string.debug_crash_report_title));
        b.setMessage(message);
        b.setPositiveButton(activity.getString(R.string.menu_share), (d, w) -> {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_STREAM, uriToShare);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share_with)));
            } catch (Throwable ignore) {}
            try { CrashReporter.clearCrashReport(); } catch (Throwable ignore) {}
        });
        b.setNegativeButton(activity.getString(R.string.cancel), (d, w) -> {
            try { CrashReporter.clearCrashReport(); } catch (Throwable ignore) {}
        });
        try {
            b.show();
        } catch (Throwable ignore) {}
    }
}
