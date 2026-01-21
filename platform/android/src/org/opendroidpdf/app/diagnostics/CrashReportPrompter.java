package org.opendroidpdf.app.diagnostics;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.R;

import java.util.List;
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

        String message = activity.getString(R.string.debug_crash_report_message);
        String crashText = CrashReporter.readCrashReportText();

        AlertDialog.Builder b = new AlertDialog.Builder(activity);
        b.setTitle(activity.getString(R.string.debug_crash_report_title));
        b.setMessage(message);
        b.setPositiveButton(activity.getString(R.string.menu_share), (d, w) -> {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                if (crashText != null) {
                    send.putExtra(Intent.EXTRA_TEXT, crashText);
                }
                if (uriToShare != null) {
                    send.putExtra(Intent.EXTRA_STREAM, uriToShare);
                    send.setClipData(ClipData.newUri(activity.getContentResolver(), "OpenDroidPDF crash report", uriToShare));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        PackageManager pm = activity.getPackageManager();
                        if (pm != null) {
                            List<ResolveInfo> targets = pm.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY);
                            if (targets != null) {
                                for (ResolveInfo info : targets) {
                                    if (info == null || info.activityInfo == null) continue;
                                    String pkg = info.activityInfo.packageName;
                                    if (pkg == null) continue;
                                    activity.grantUriPermission(pkg, uriToShare, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                }
                            }
                        }
                    } catch (Throwable ignore) {}
                }
                Intent chooser = Intent.createChooser(send, activity.getString(R.string.share_with));
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(chooser);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(activity, R.string.debug_crash_report_share_failed, Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                try {
                    Toast.makeText(activity, R.string.debug_crash_report_share_failed, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {}
            }
        });
        b.setNeutralButton(activity.getString(R.string.debug_crash_report_copy), (d, w) -> {
            try {
                if (crashText == null) {
                    Toast.makeText(activity, R.string.debug_crash_report_copy_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("OpenDroidPDF crash report", crashText));
                    Toast.makeText(activity, R.string.debug_crash_report_copied, Toast.LENGTH_SHORT).show();
                    try { CrashReporter.clearCrashReport(); } catch (Throwable ignore) {}
                } else {
                    Toast.makeText(activity, R.string.debug_crash_report_copy_failed, Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable t) {
                try {
                    Toast.makeText(activity, R.string.debug_crash_report_copy_failed, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {}
            }
        });
        b.setNegativeButton(activity.getString(R.string.debug_crash_report_not_now), (d, w) -> {
            // Keep the crash report so the user can share it later.
        });
        try {
            b.show();
        } catch (Throwable ignore) {}
    }
}
