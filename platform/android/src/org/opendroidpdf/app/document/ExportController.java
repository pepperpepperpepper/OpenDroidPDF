package org.opendroidpdf.app.document;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.print.PrintAttributes;
import android.print.PrintManager;

import org.opendroidpdf.PdfPrintAdapter;
import org.opendroidpdf.R;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.concurrent.Callable;

/**
 * Encapsulates export/print flows so the activity can stay lean.
 */
public class ExportController {

    public interface Host {
        MuPdfRepository getRepository();
        void commitPendingInkToCoreBlocking();
        void showInfo(String message);
        String currentDocumentName();
        void setLastExportedUri(Uri uri);
        Uri getLastExportedUri();
        void markIgnoreSaveOnStop();
        Context getContext();
        android.content.ContentResolver getContentResolver();
        void callInBackgroundAndShowDialog(String message, Callable<Exception> background, Callable<Void> success, Callable<Void> failure);
    }

    private final Host host;

    public ExportController(Host host) {
        this.host = host;
    }

    public void printDoc() {
        final MuPdfRepository repo = host.getRepository();
        if (repo == null) {
            host.showInfo(host.getContext().getString(R.string.error_saveing));
            return;
        }
        if (!repo.isPdfDocument()) {
            host.showInfo(host.getContext().getString(R.string.format_currently_not_supported));
            return;
        }

        final PrintManager printManager = (PrintManager) host.getContext().getSystemService(Context.PRINT_SERVICE);
        final String documentName = host.currentDocumentName();
        final Context appContext = host.getContext().getApplicationContext();

        host.callInBackgroundAndShowDialog(
            host.getContext().getString(R.string.preparing_to_print),
            new Callable<Exception>() {
                Uri exported;
                @Override
                public Exception call() {
                    try {
                        host.commitPendingInkToCoreBlocking();
                        exported = repo.exportDocument(appContext);
                    } catch (Exception e) {
                        return e;
                    }
                    host.setLastExportedUri(exported);
                    return null;
                }
            },
            new Callable<Void>() {
                @Override
                public Void call() {
                    Uri exported = host.getLastExportedUri();
                    if (exported == null) {
                        host.showInfo(host.getContext().getString(R.string.error_saveing));
                        return null;
                    }
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        android.util.Log.i("OpenDroidPDF", "DEBUG_PRINT_LAUNCHED uri=" + exported);
                    }
                    host.markIgnoreSaveOnStop();
                    PrintAttributes attrs = new PrintAttributes.Builder().build();
                    printManager.print(documentName, new PdfPrintAdapter(host.getContext(), exported), attrs);
                    return null;
                }
            },
            null);
    }

    public void shareDoc() {
        final MuPdfRepository repo = host.getRepository();
        if (repo == null) {
            host.showInfo(host.getContext().getString(R.string.error_exporting));
            return;
        }
        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        final Context appContext = host.getContext().getApplicationContext();
        final String documentName = host.currentDocumentName();

        host.callInBackgroundAndShowDialog(
            host.getContext().getString(R.string.preparing_to_share),
            new Callable<Exception>() {
                @Override
                public Exception call() {
                    Uri exportedUri = null;
                    try
                    {
                        host.commitPendingInkToCoreBlocking();
                        exportedUri = repo.exportDocument(appContext);
                    }
                    catch(Exception e)
                    {
                        return e;
                    }
                    shareIntent.setType("application/pdf");
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.setClipData(ClipData.newUri(host.getContentResolver(), documentName, exportedUri));
                    shareIntent.putExtra(Intent.EXTRA_STREAM, exportedUri);

                    PackageManager pm = host.getContext().getPackageManager();
                    for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY)) {
                        if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                            try {
                                host.getContext().grantUriPermission(ri.activityInfo.packageName, exportedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignore) {}
                        }
                    }
                    return null;
                }
            },
            new Callable<Void>() {
                @Override
                public Void call() {
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        android.util.Log.i("OpenDroidPDF", "DEBUG_SHARE_LAUNCHED uri=" + host.getLastExportedUri());
                    }
                    host.markIgnoreSaveOnStop();
                    Intent chooser = Intent.createChooser(shareIntent, host.getContext().getString(R.string.share_with));
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    host.getContext().startActivity(chooser);
                    return null;
                }
            },
            null);
    }
}
