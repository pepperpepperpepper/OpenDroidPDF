package org.opendroidpdf.app.document;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.print.PrintAttributes;
import android.print.PrintManager;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import org.opendroidpdf.PdfPrintAdapter;
import org.opendroidpdf.R;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.core.MuPdfRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
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
        void markIgnoreSaveOnStop();
        Context getContext();
        android.content.ContentResolver getContentResolver();
        void callInBackgroundAndShowDialog(String message, Callable<Exception> background, Callable<Void> success, Callable<Void> failure);
        void promptSaveAs();
        @Nullable SidecarAnnotationProvider sidecarAnnotationProviderOrNull();
    }

    private final Host host;
    private @Nullable Uri lastExportedUri;

    public ExportController(Host host) {
        this.host = host;
    }

    public @Nullable Uri lastExportedUriOrNull() {
        return lastExportedUri;
    }

    public void printDoc() {
        final MuPdfRepository repo = host.getRepository();
        if (repo == null) {
            host.showInfo(host.getContext().getString(R.string.error_saveing));
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
                        exported = exportPdfForExternalUse(appContext, repo, documentName);
                    } catch (Exception e) {
                        return e;
                    }
                    lastExportedUri = exported;
                    return null;
                }
            },
            new Callable<Void>() {
                @Override
                public Void call() {
                    Uri exported = lastExportedUri;
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
                        exportedUri = exportPdfForExternalUse(appContext, repo, documentName);
                    }
                    catch(Exception e)
                    {
                        return e;
                    }
                    lastExportedUri = exportedUri;
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
                        android.util.Log.i("OpenDroidPDF", "DEBUG_SHARE_LAUNCHED uri=" + lastExportedUri);
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

    /**
     * Saves the current document (export to a new Uri) using the existing save-as prompt.
     */
    public void saveDoc() {
        host.promptSaveAs();
    }

    public void shareSidecarAnnotationsBundle() {
        SidecarAnnotationProvider provider = host.sidecarAnnotationProviderOrNull();
        if (!(provider instanceof SidecarAnnotationSession)) {
            host.showInfo(host.getContext().getString(R.string.no_sidecar_annotations));
            return;
        }
        final SidecarAnnotationSession session = (SidecarAnnotationSession) provider;

        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
        final Context appContext = host.getContext().getApplicationContext();
        final String documentName = host.currentDocumentName();

        host.callInBackgroundAndShowDialog(
                host.getContext().getString(R.string.preparing_to_share),
                new Callable<Exception>() {
                    Uri exportedUri;

                    @Override
                    public Exception call() {
                        try {
                            host.commitPendingInkToCoreBlocking();
                            exportedUri = exportSidecarBundleForExternalUse(appContext, session, documentName);
                        } catch (Exception e) {
                            return e;
                        }

                        shareIntent.setType("application/json");
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
                        host.markIgnoreSaveOnStop();
                        Intent chooser = Intent.createChooser(shareIntent, host.getContext().getString(R.string.share_with));
                        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        host.getContext().startActivity(chooser);
                        return null;
                    }
                },
                null);
    }

    private Uri exportPdfForExternalUse(Context appContext, MuPdfRepository repo, String baseName) throws Exception {
        SidecarAnnotationProvider sidecar = host.sidecarAnnotationProviderOrNull();
        if (sidecar != null) {
            if (repo.isPdfDocument()) {
                try {
                    return SidecarPdfEmbedExporter.export(appContext, repo, sidecar, baseName);
                } catch (Throwable embedError) {
                    // Fallback: always produce a usable PDF even if embedding fails.
                    if (org.opendroidpdf.BuildConfig.DEBUG) {
                        android.util.Log.w("ExportController", "embed export failed; falling back to flattened", embedError);
                    }
                }
            }
            return FlattenedPdfExporter.export(appContext, repo, sidecar, baseName);
        }
        return repo.exportDocument(appContext);
    }

    private static Uri exportSidecarBundleForExternalUse(Context appContext,
                                                        SidecarAnnotationSession session,
                                                        String baseName) throws Exception {
        File outFile = newSidecarBundleFile(appContext, baseName);
        try (OutputStream os = new FileOutputStream(outFile, false)) {
            session.writeBundleJson(os);
        }
        return FileProvider.getUriForFile(appContext, "org.opendroidpdf.fileprovider", outFile);
    }

    private static File newSidecarBundleFile(Context appContext, String baseName) {
        File dir = new File(appContext.getCacheDir(), "tmpfiles");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();

        String safe = (baseName == null || baseName.trim().isEmpty()) ? "document" : baseName.trim();
        safe = safe.replace('/', '_').replace('\\', '_');
        safe = safe.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (safe.length() > 64) safe = safe.substring(0, 64);

        if (safe.toLowerCase().endsWith(".epub")) {
            safe = safe.substring(0, safe.length() - 5);
        } else if (safe.toLowerCase().endsWith(".pdf")) {
            safe = safe.substring(0, safe.length() - 4);
        }

        String fileName = safe + "_annotations_" + System.currentTimeMillis() + ".json";
        return new File(dir, fileName);
    }
}
